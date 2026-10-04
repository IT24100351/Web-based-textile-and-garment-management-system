package lk.ac.sliit.tgms.search;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.auth.AuthService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.delivery.DeliveryService;
import lk.ac.sliit.tgms.inventory.InventoryMaterialService;
import lk.ac.sliit.tgms.order.OrderService;
import lk.ac.sliit.tgms.order.OrderSummary;
import lk.ac.sliit.tgms.product.ProductService;
import lk.ac.sliit.tgms.supplier.MaterialSupplyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedSearchService {
    private static final int MAX_SEARCH_LENGTH = 120;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 25;
    private static final int MAX_PAGE = 1000;

    private final AuthService authService;
    private final ProductService productService;
    private final MaterialSupplyService materialSupplyService;
    private final InventoryMaterialService inventoryMaterialService;
    private final OrderService orderService;
    private final DeliveryService deliveryService;

    public SharedSearchService(
            AuthService authService,
            ProductService productService,
            MaterialSupplyService materialSupplyService,
            InventoryMaterialService inventoryMaterialService,
            OrderService orderService,
            DeliveryService deliveryService) {
        this.authService = authService;
        this.productService = productService;
        this.materialSupplyService = materialSupplyService;
        this.inventoryMaterialService = inventoryMaterialService;
        this.orderService = orderService;
        this.deliveryService = deliveryService;
    }

    /**
     * Cross-module entry point. Each module applies its existing search and authorization contract
     * before the permitted results are combined and paginated for the response.
     */
    @Transactional(readOnly = true)
    public SharedSearchPage search(
            long userId, String rawSearch, Integer requestedPage, Integer requestedSize) {
        UserAccount account = authService.requireActiveUser(userId);
        String search = normalize(rawSearch);
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? DEFAULT_PAGE_SIZE : requestedSize;
        validate(search, page, size);

        List<SharedSearchResult> results = new ArrayList<>();
        results.addAll(productResults(search));
        switch (account.role()) {
            case SUPPLIER -> results.addAll(supplyResults(userId, search));
            case INVENTORY_MANAGER -> {
                results.addAll(supplyResults(userId, search));
                results.addAll(inventoryResults(search));
            }
            case SALES_OFFICER -> {
                results.addAll(staffOrderResults(search));
                results.addAll(deliveryResults(search));
            }
            case ADMINISTRATOR -> {
                results.addAll(supplyResults(userId, search));
                results.addAll(staffOrderResults(search));
            }
            case CUSTOMER -> results.addAll(customerOrderResults(userId, search));
            case PRODUCTION_MANAGER -> {
                // Product results are the only shared-search records exposed for this role.
            }
        }

        return SharedSearchPage.from(search, page, size, results);
    }

    private List<SharedSearchResult> productResults(String search) {
        return productService.getPublicCatalog(search, null, null, null, null)
                .stream()
                .map(details -> new SharedSearchResult(
                        "PRODUCT",
                        details.product().id(),
                        details.product().name(),
                        details.category().name(),
                        details.product().status().name(),
                        "/products/" + details.product().id()))
                .toList();
    }

    private List<SharedSearchResult> supplyResults(long userId, String search) {
        return materialSupplyService.getPermittedSupplies(userId, search, null)
                .stream()
                .map(item -> new SharedSearchResult(
                        "SUPPLY",
                        item.supply().id(),
                        item.supply().materialCode() + " — " + item.supply().materialName(),
                        item.supplierBusinessName(),
                        item.supply().status().name(),
                        "/supplies?search=" + urlToken(item.supply().materialCode())))
                .toList();
    }

    private List<SharedSearchResult> inventoryResults(String search) {
        return inventoryMaterialService.getMaterials(search, null, null)
                .stream()
                .map(material -> new SharedSearchResult(
                        "INVENTORY",
                        material.id(),
                        material.materialCode() + " — " + material.materialName(),
                        material.currentQuantity().stripTrailingZeros().toPlainString()
                                + " " + material.unitOfMeasure(),
                        material.status().name(),
                        "/inventory/materials?search=" + urlToken(material.materialCode())))
                .toList();
    }

    private List<SharedSearchResult> staffOrderResults(String search) {
        return orderService.getStaffOrders(search, null)
                .stream()
                .map(order -> orderResult(order, false))
                .toList();
    }

    private List<SharedSearchResult> customerOrderResults(long userId, String search) {
        return orderService.getCustomerOrders(userId, search)
                .stream()
                .map(order -> orderResult(order, true))
                .toList();
    }

    private SharedSearchResult orderResult(OrderSummary order, boolean customer) {
        return new SharedSearchResult(
                "ORDER",
                order.id(),
                order.orderNumber(),
                customer ? order.itemCount() + " item(s)" : order.customerName(),
                order.status().name(),
                (customer ? "/orders/history/" : "/orders/") + order.id());
    }

    private List<SharedSearchResult> deliveryResults(String search) {
        return deliveryService.getStaffRecords(search, null)
                .stream()
                .map(record -> new SharedSearchResult(
                        "DELIVERY",
                        record.delivery().id(),
                        record.delivery().deliveryNumber(),
                        "Order " + record.order().orderNumber(),
                        record.delivery().status().name(),
                        "/deliveries/" + record.delivery().id()))
                .toList();
    }

    private void validate(String search, int page, int size) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (search == null || search.length() < 2) {
            fields.put("search", "Search must contain at least 2 characters.");
        } else if (search.length() > MAX_SEARCH_LENGTH) {
            fields.put("search", "Search must not exceed 120 characters.");
        }
        if (page < 0 || page > MAX_PAGE) {
            fields.put("page", "Page must be between 0 and 1000.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            fields.put("size", "Page size must be between 1 and 25.");
        }
        if (!fields.isEmpty()) {
            throw new SharedSearchValidationException(fields);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String urlToken(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

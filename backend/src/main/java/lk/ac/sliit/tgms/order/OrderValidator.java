package lk.ac.sliit.tgms.order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserAccountRepository;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.product.OrderProductSelection;
import lk.ac.sliit.tgms.product.ProductNotSelectableException;
import lk.ac.sliit.tgms.product.ProductService;
import org.springframework.stereotype.Component;

/**
 * Shared validation boundary for every new-order path.
 *
 * <p>Both Sales Officer and registered-customer order creation must pass through this
 * validator before any order row is written. It validates the customer, order shape,
 * line selections, quantities, and current Product Management order-selectability.
 */
@Component
public class OrderValidator {

    static final int MAX_ITEMS_PER_ORDER = 50;

    private final UserAccountRepository userAccountRepository;
    private final ProductService productService;

    public OrderValidator(
            UserAccountRepository userAccountRepository,
            ProductService productService) {
        this.userAccountRepository = userAccountRepository;
        this.productService = productService;
    }

    public ValidatedOrder validate(
            Long customerId, List<CreateOrderItemCommand> requestedItems) {
        validateRequestShape(customerId, requestedItems);

        UserAccount customer = userAccountRepository.findById(customerId)
                .filter(UserAccount::active)
                .filter(account -> account.role() == UserRole.CUSTOMER)
                .orElseThrow(OrderCustomerNotSelectableException::new);

        Map<String, String> unavailableFields = new LinkedHashMap<>();
        List<ValidatedOrderItem> validatedItems = new ArrayList<>();
        for (int index = 0; index < requestedItems.size(); index++) {
            CreateOrderItemCommand requestedItem = requestedItems.get(index);
            try {
                OrderProductSelection selection = productService.requireOrderSelectableVariant(
                        requestedItem.productId(), requestedItem.variantId());
                validatedItems.add(new ValidatedOrderItem(requestedItem, selection));
            } catch (ProductNotSelectableException exception) {
                unavailableFields.put(
                        "items[" + index + "].variantId",
                        "This product/size/color selection is no longer available. Choose an available garment variant.");
            }
        }

        if (!unavailableFields.isEmpty()) {
            throw new OrderProductNotSelectableException(unavailableFields);
        }

        return new ValidatedOrder(customer, List.copyOf(validatedItems));
    }

    private void validateRequestShape(
            Long customerId, List<CreateOrderItemCommand> requestedItems) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (customerId == null || customerId <= 0) {
            fields.put("customerId", "Select a valid customer account.");
        }

        if (requestedItems == null || requestedItems.isEmpty()) {
            fields.put("items", "At least one order item is required.");
        } else if (requestedItems.size() > MAX_ITEMS_PER_ORDER) {
            fields.put("items", "An order may contain at most 50 items.");
        } else {
            for (int index = 0; index < requestedItems.size(); index++) {
                validateItem(requestedItems.get(index), index, fields);
            }
        }

        if (!fields.isEmpty()) {
            throw new OrderValidationException(fields);
        }
    }

    private void validateItem(
            CreateOrderItemCommand item, int index, Map<String, String> fields) {
        String prefix = "items[" + index + "]";
        if (item == null) {
            fields.put(prefix, "Order item is required.");
            return;
        }
        if (item.productId() == null || item.productId() <= 0) {
            fields.put(prefix + ".productId", "Select a product.");
        }
        if (item.variantId() == null || item.variantId() <= 0) {
            fields.put(prefix + ".variantId", "Select an available size/color variant.");
        }
        if (item.quantity() == null || item.quantity() <= 0) {
            fields.put(prefix + ".quantity", "Quantity must be a positive whole number.");
        }
    }

    public record ValidatedOrder(UserAccount customer, List<ValidatedOrderItem> items) {}

    public record ValidatedOrderItem(
            CreateOrderItemCommand requested, OrderProductSelection selection) {}
}

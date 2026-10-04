package lk.ac.sliit.tgms.quotation;

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

@Component
public class QuotationValidator {

    static final int MAX_ITEMS_PER_QUOTATION = 50;

    private final UserAccountRepository userAccountRepository;
    private final ProductService productService;

    public QuotationValidator(
            UserAccountRepository userAccountRepository,
            ProductService productService) {
        this.userAccountRepository = userAccountRepository;
        this.productService = productService;
    }

    public ValidatedQuotation validate(
            Long customerId, List<CreateQuotationItemCommand> requestedItems) {
        validateShape(customerId, requestedItems);

        UserAccount customer = userAccountRepository.findById(customerId)
                .filter(UserAccount::active)
                .filter(account -> account.role() == UserRole.CUSTOMER)
                .orElseThrow(QuotationCustomerNotSelectableException::new);

        Map<String, String> unavailable = new LinkedHashMap<>();
        List<ValidatedQuotationItem> validatedItems = new ArrayList<>();
        for (int index = 0; index < requestedItems.size(); index++) {
            CreateQuotationItemCommand item = requestedItems.get(index);
            try {
                OrderProductSelection selection = productService.requireOrderSelectableVariant(
                        item.productId(), item.variantId());
                validatedItems.add(new ValidatedQuotationItem(item, selection));
            } catch (ProductNotSelectableException exception) {
                unavailable.put(
                        "items[" + index + "].variantId",
                        "This product/size/color selection is no longer available. Choose an available garment variant.");
            }
        }

        if (!unavailable.isEmpty()) {
            throw new QuotationProductNotSelectableException(unavailable);
        }
        return new ValidatedQuotation(customer, List.copyOf(validatedItems));
    }

    private void validateShape(Long customerId, List<CreateQuotationItemCommand> requestedItems) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (customerId == null || customerId <= 0) {
            fields.put("customerId", "Select a valid customer account.");
        }
        if (requestedItems == null || requestedItems.isEmpty()) {
            fields.put("items", "At least one quotation item is required.");
        } else if (requestedItems.size() > MAX_ITEMS_PER_QUOTATION) {
            fields.put("items", "A quotation may contain at most 50 items.");
        } else {
            for (int index = 0; index < requestedItems.size(); index++) {
                validateItem(requestedItems.get(index), index, fields);
            }
        }
        if (!fields.isEmpty()) {
            throw new QuotationValidationException(fields);
        }
    }

    private void validateItem(
            CreateQuotationItemCommand item, int index, Map<String, String> fields) {
        String prefix = "items[" + index + "]";
        if (item == null) {
            fields.put(prefix, "Quotation item is required.");
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

    public record ValidatedQuotation(
            UserAccount customer, List<ValidatedQuotationItem> items) {}

    public record ValidatedQuotationItem(
            CreateQuotationItemCommand requested, OrderProductSelection selection) {}
}

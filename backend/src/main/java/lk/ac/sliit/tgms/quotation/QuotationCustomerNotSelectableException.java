package lk.ac.sliit.tgms.quotation;

import java.util.Map;

public class QuotationCustomerNotSelectableException extends RuntimeException {
    public QuotationCustomerNotSelectableException() {
        super("The selected customer is not available for a quotation.");
    }

    public Map<String, String> fields() {
        return Map.of("customerId", "Select an active registered customer account.");
    }
}

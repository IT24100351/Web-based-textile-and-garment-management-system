package lk.ac.sliit.tgms.order;

import java.util.Map;

public class OrderCustomerNotSelectableException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderCustomerNotSelectableException() {
        super("The selected customer account is unavailable for a new order.");
        this.fields = Map.of(
                "customerId",
                "Select an active registered customer account before placing the order.");
    }

    public Map<String, String> fields() {
        return fields;
    }
}

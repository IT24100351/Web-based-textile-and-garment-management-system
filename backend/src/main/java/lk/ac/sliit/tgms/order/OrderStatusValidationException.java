package lk.ac.sliit.tgms.order;

import java.util.Map;

public class OrderStatusValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderStatusValidationException(Map<String, String> fields) {
        super("Order status update validation failed.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

package lk.ac.sliit.tgms.order;

import java.util.Map;

/** Order-specific product availability error carrying actionable order-line fields. */
public class OrderProductNotSelectableException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderProductNotSelectableException(Map<String, String> fields) {
        super("The selected product variant is not available for a new order.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

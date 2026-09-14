package lk.ac.sliit.tgms.order;

import java.util.Map;

public class OrderBillingValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public OrderBillingValidationException(Map<String, String> fields) {
        super("Please correct the highlighted billing fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

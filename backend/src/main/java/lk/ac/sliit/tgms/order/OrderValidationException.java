package lk.ac.sliit.tgms.order;

import java.util.Map;

public class OrderValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderValidationException(Map<String, String> fields) {
        super("Please correct the highlighted fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

package lk.ac.sliit.tgms.order;

import java.util.Map;

public class OrderReadValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderReadValidationException(Map<String, String> fields) {
        super("Please correct the highlighted order filters.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

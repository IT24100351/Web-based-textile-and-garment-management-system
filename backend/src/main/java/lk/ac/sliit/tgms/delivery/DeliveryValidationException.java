package lk.ac.sliit.tgms.delivery;

import java.util.Map;

public class DeliveryValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public DeliveryValidationException(Map<String, String> fields) {
        super("Please correct the highlighted delivery fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

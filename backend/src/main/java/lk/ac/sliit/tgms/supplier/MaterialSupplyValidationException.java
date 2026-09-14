package lk.ac.sliit.tgms.supplier;

import java.util.Map;

public class MaterialSupplyValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public MaterialSupplyValidationException(Map<String, String> fields) {
        super("Please correct the highlighted material supply fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

package lk.ac.sliit.tgms.supplier;

import java.util.Map;

public class SupplierProfileValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public SupplierProfileValidationException(Map<String, String> fields) {
        super("Please correct the highlighted supplier profile fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

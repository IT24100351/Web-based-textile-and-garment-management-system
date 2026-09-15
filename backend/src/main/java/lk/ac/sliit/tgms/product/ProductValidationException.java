package lk.ac.sliit.tgms.product;

import java.util.Map;

public class ProductValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductValidationException(Map<String, String> fields) {
        super("Please correct the highlighted product fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

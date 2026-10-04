package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionQualityControlException extends RuntimeException {
    private final Map<String, String> fields;

    public ProductionQualityControlException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

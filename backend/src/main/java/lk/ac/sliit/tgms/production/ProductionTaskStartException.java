package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionTaskStartException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductionTaskStartException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

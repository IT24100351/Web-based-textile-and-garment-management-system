package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionTaskStatusValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductionTaskStatusValidationException(Map<String, String> fields) {
        super("Production status update validation failed.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

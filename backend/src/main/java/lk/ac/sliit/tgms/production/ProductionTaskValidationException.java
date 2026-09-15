package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionTaskValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductionTaskValidationException(Map<String, String> fields) {
        super("Please correct the highlighted production task fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

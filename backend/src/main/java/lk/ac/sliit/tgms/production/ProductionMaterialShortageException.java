package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionMaterialShortageException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductionMaterialShortageException(Map<String, String> fields) {
        super("Production cannot start because one or more required materials are unavailable.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

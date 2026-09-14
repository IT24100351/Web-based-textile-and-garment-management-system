package lk.ac.sliit.tgms.inventory;

import java.util.Map;

public class InventoryMaterialValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public InventoryMaterialValidationException(Map<String, String> fields) {
        super("Inventory material data is invalid.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

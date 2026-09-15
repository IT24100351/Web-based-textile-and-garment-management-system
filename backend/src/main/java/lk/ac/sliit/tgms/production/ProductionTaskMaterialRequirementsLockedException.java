package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionTaskMaterialRequirementsLockedException extends RuntimeException {
    private final Map<String, String> fields;

    public ProductionTaskMaterialRequirementsLockedException() {
        super("Production material requirements are locked after production starts.");
        this.fields = Map.of(
                "materials",
                "Material requirements can only be changed while the production task is PENDING.");
    }

    public Map<String, String> fields() {
        return fields;
    }
}

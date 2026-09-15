package lk.ac.sliit.tgms.production;

import java.util.Locale;
import java.util.Map;

public enum ProductionTaskRecordView {
    ACTIVE,
    COMPLETED,
    ALL;

    public static ProductionTaskRecordView parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProductionTaskValidationException(Map.of(
                    "view", "View must be ACTIVE, COMPLETED, or ALL."));
        }
    }
}

package lk.ac.sliit.tgms.production;

import java.util.Map;

/**
 * Custom exception used to indicate that a production task
 * has failed or encountered an issue during quality control checks.
 */
public class ProductionQualityControlException extends RuntimeException {

    // Holds additional context or field-specific error details
    private final Map<String, String> fields;

    /**
     * Constructor that accepts a custom error message and
     * a map of field details explaining the cause of the exception.
     *
     * @param message descriptive error message
     * @param fields  key-value pairs providing extra context
     */
    public ProductionQualityControlException(String message, Map<String, String> fields) {
        super(message);
        // Defensive copy to ensure immutability of the provided map
        this.fields = Map.copyOf(fields);
    }

    /**
     * Returns the map of fields that describe
     * the specific validation or quality control issues.
     *
     * @return immutable map of error details
     */
    public Map<String, String> fields() {
        return fields;
    }
}

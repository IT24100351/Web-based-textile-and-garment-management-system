package lk.ac.sliit.tgms.production;

import java.util.Map;

/**
 * Custom exception thrown when a production order
 * is not eligible to be processed in Production Management.
 */
public class ProductionOrderNotEligibleException extends RuntimeException {

    // Holds additional details about why the order is not eligible
    private final Map<String, String> fields;

    /**
     * Default constructor sets a descriptive error message
     * and provides guidance in the fields map.
     */
    public ProductionOrderNotEligibleException() {
        super("The selected order is not currently ready for Production Management.");
        this.fields = Map.of(
                "orderId",
                "Choose an order whose Order Management handoff reports readyForProduction=true.");
    }

    /**
     * Returns the map of fields that explain
     * which values caused the exception.
     */
    public Map<String, String> fields() {
        return fields;
    }
}

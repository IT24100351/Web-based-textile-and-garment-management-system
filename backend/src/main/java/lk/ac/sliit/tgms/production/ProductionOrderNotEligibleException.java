package lk.ac.sliit.tgms.production;

import java.util.Map;

public class ProductionOrderNotEligibleException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductionOrderNotEligibleException() {
        super("The selected order is not currently ready for Production Management.");
        this.fields = Map.of(
                "orderId",
                "Choose an order whose Order Management handoff reports readyForProduction=true.");
    }

    public Map<String, String> fields() {
        return fields;
    }
}

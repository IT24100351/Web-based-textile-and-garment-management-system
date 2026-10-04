package lk.ac.sliit.tgms.delivery;

import java.util.Map;

public class DeliveryOrderAlreadyAssignedException extends RuntimeException {

    private final Map<String, String> fields = Map.of(
            "orderId",
            "An active or completed Delivery already exists for this order. Cancel an incorrect SCHEDULED record before creating a replacement.");

    public DeliveryOrderAlreadyAssignedException() {
        super("This order already has a non-cancelled Delivery record.");
    }

    public Map<String, String> fields() {
        return fields;
    }
}

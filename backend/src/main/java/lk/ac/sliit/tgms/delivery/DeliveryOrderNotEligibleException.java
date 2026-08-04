package lk.ac.sliit.tgms.delivery;

import java.util.Map;
import lk.ac.sliit.tgms.order.OrderStatus;

public class DeliveryOrderNotEligibleException extends RuntimeException {

    private final Map<String, String> fields;

    public DeliveryOrderNotEligibleException(OrderStatus currentStatus) {
        super("The selected order is not currently ready for Delivery Management.");
        this.fields = Map.of(
                "orderId",
                "Choose an order whose Order Management handoff reports readyForDelivery=true. Current status: "
                        + currentStatus.name() + ".");
    }

    public Map<String, String> fields() {
        return fields;
    }
}

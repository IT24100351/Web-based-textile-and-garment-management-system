package lk.ac.sliit.tgms.delivery;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeliveryStatusTransitionException extends RuntimeException {

    private final Map<String, String> fields;

    public DeliveryStatusTransitionException(DeliveryStatus current, DeliveryStatus requested) {
        super("Delivery status cannot move from " + current.name() + " to " + requested.name() + ".");
        this.fields = Map.of("status", fieldMessage(current));
    }

    public Map<String, String> fields() {
        return fields;
    }

    private static String fieldMessage(DeliveryStatus current) {
        List<DeliveryStatus> allowed = DeliveryStatusLifecycle.allowedTransitions(current);
        if (allowed.isEmpty()) {
            return "This delivery is in a terminal status and cannot be changed.";
        }
        return "Allowed next status: " + allowed.stream()
                .map(DeliveryStatus::name)
                .collect(Collectors.joining(" or ")) + ".";
    }
}

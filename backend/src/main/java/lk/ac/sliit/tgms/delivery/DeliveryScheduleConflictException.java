package lk.ac.sliit.tgms.delivery;

import java.time.Instant;
import java.util.Map;

public class DeliveryScheduleConflictException extends RuntimeException {

    private final Map<String, String> fields;

    public DeliveryScheduleConflictException(DeliveryScheduleConflict conflict) {
        super("The requested delivery time is unavailable.");
        Instant scheduledAt = conflict.scheduledAt();
        this.fields = Map.of(
                "scheduledAt",
                "This time conflicts with active delivery " + conflict.deliveryNumber()
                        + " scheduled at " + scheduledAt
                        + ". Choose a time at least 60 minutes before or after existing active deliveries.");
    }

    public Map<String, String> fields() {
        return fields;
    }
}

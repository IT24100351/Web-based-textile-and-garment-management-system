package lk.ac.sliit.tgms.delivery;

import java.time.Instant;

public record DeliveryScheduleConflict(
        String deliveryNumber,
        Instant scheduledAt) {}

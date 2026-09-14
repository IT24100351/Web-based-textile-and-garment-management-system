package lk.ac.sliit.tgms.order;

import java.time.Instant;

public record OrderStatusHistoryEntry(
        long id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        long changedByUserId,
        Instant changedAt) {}

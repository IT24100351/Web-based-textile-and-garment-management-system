package lk.ac.sliit.tgms.production;

import java.time.Instant;

/** Stable Production Management task header linked to an Order Management order. */
public record ProductionTask(
        long id,
        String taskNumber,
        long orderId,
        ProductionTaskStatus status,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        ProductionQualityControlResult qualityControlResult,
        Long qualityCheckedByUserId,
        Instant qualityCheckedAt) {}

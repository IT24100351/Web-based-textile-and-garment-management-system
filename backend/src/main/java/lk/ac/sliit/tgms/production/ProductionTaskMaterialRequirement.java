package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.time.Instant;

/** Persisted Production-owned requirement referencing Inventory Management by stable material ID. */
public record ProductionTaskMaterialRequirement(
        long productionTaskId,
        long inventoryMaterialId,
        BigDecimal requiredQuantity,
        Instant createdAt,
        Instant updatedAt) {}

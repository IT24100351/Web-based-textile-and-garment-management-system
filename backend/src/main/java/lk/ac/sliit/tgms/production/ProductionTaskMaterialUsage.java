package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductionTaskMaterialUsage(
        long id,
        long productionTaskId,
        long inventoryMaterialId,
        BigDecimal quantityUsed,
        long recordedByUserId,
        Instant recordedAt) {}

package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductionTaskMaterialUsageView(
        long id,
        long inventoryMaterialId,
        String materialCode,
        String materialName,
        String unitOfMeasure,
        BigDecimal quantityUsed,
        BigDecimal remainingQuantity,
        long recordedByUserId,
        Instant recordedAt) {}

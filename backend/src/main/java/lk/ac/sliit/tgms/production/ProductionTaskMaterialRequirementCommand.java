package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;

public record ProductionTaskMaterialRequirementCommand(
        Long inventoryMaterialId,
        BigDecimal requiredQuantity) {}

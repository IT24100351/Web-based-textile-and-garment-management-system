package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.time.Instant;
import lk.ac.sliit.tgms.inventory.InventoryAvailabilityState;
import lk.ac.sliit.tgms.inventory.InventoryMaterialStatus;
import lk.ac.sliit.tgms.inventory.InventoryMaterialType;

/** Production view combining its persisted requirement with live Inventory-owned metadata/state. */
public record ProductionTaskMaterialRequirementView(
        long inventoryMaterialId,
        String materialCode,
        String materialName,
        InventoryMaterialType materialType,
        String unitOfMeasure,
        BigDecimal requiredQuantity,
        BigDecimal currentQuantity,
        InventoryMaterialStatus inventoryStatus,
        InventoryAvailabilityState availabilityState,
        Instant createdAt,
        Instant updatedAt) {}

package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import lk.ac.sliit.tgms.inventory.InventoryMaterialStatus;
import lk.ac.sliit.tgms.inventory.InventoryMaterialType;
import lk.ac.sliit.tgms.inventory.InventoryStockState;

/** Read-only Inventory option exposed to Production without copying Inventory master data. */
public record ProductionInventoryMaterialOption(
        long inventoryMaterialId,
        String materialCode,
        String materialName,
        InventoryMaterialType materialType,
        String unitOfMeasure,
        BigDecimal currentQuantity,
        InventoryMaterialStatus status,
        InventoryStockState stockState) {}

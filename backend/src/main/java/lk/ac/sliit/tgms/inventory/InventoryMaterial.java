package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryMaterial(
        long id,
        Long sourceMaterialSupplyId,
        String materialCode,
        String materialName,
        String materialDescription,
        InventoryMaterialType materialType,
        String unitOfMeasure,
        BigDecimal currentQuantity,
        BigDecimal lowStockThreshold,
        InventoryMaterialStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public InventoryStockState stockState() {
        return InventoryStockState.from(currentQuantity, lowStockThreshold);
    }
}

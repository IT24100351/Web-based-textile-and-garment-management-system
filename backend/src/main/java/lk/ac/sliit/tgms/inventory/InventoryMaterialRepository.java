package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryMaterialRepository {

    long create(
            Long sourceMaterialSupplyId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            BigDecimal lowStockThreshold);

    Optional<InventoryMaterial> findById(long materialId);

    Optional<InventoryMaterial> findByMaterialCode(String materialCode);

    default List<InventoryMaterial> findAll() {
        return findAll(InventoryMaterialQuery.empty());
    }

    List<InventoryMaterial> findAll(InventoryMaterialQuery query);

    int updateMetadata(
            long materialId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            BigDecimal lowStockThreshold,
            InventoryMaterialStatus status);

    boolean isReferenced(long materialId);

    int deleteById(long materialId);

    int receiveStockIfActive(long materialId, BigDecimal quantity);

    int consumeStockIfAvailable(long materialId, BigDecimal quantity);
}

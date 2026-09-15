package lk.ac.sliit.tgms.production;

import java.util.List;
import java.util.Optional;

public interface ProductionTaskRepository {

    ProductionTask createTask(long orderId, String taskNumber);

    Optional<ProductionTask> findById(long taskId);

    Optional<ProductionTask> findByIdForUpdate(long taskId);

    int startPendingTask(long taskId);

    int completeInProgressTask(long taskId);

    long countIncompleteTasksForOrder(long orderId);

    List<ProductionTask> findRecords(ProductionTaskRecordView view);

    int updateQualityControl(
            long taskId,
            ProductionQualityControlResult result,
            long checkedByUserId);

    Optional<ProductionTaskWorkDetails> findWorkDetails(long taskId);

    ProductionTaskWorkDetails saveWorkDetails(
            long taskId,
            String workDetails,
            String workAssignment,
            String workNotes);

    List<ProductionTaskMaterialRequirement> findMaterialRequirements(long taskId);

    void replaceMaterialRequirements(
            long taskId,
            List<ProductionTaskMaterialRequirementCommand> requirements);

    List<ProductionTaskMaterialUsage> findMaterialUsage(long taskId);

    ProductionTaskMaterialUsage createMaterialUsage(
            long taskId,
            long inventoryMaterialId,
            java.math.BigDecimal quantityUsed,
            long recordedByUserId);
}

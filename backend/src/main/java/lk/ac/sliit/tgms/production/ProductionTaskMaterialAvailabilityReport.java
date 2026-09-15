package lk.ac.sliit.tgms.production;

import java.util.List;

/**
 * Point-in-time Production readiness report resolved through Inventory Management.
 * This is a check only: it does not reserve or consume stock.
 */
public record ProductionTaskMaterialAvailabilityReport(
        long taskId,
        String taskNumber,
        ProductionTaskStatus taskStatus,
        boolean hasMaterialRequirements,
        boolean allMaterialsAvailable,
        boolean canStart,
        List<ProductionTaskMaterialRequirementView> materials) {

    public ProductionTaskMaterialAvailabilityReport {
        materials = List.copyOf(materials);
    }
}

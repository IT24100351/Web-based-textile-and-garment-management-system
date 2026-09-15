package lk.ac.sliit.tgms.production;

import java.util.List;

public record ProductionTaskMaterialUsageReport(
        long taskId,
        String taskNumber,
        ProductionTaskStatus taskStatus,
        boolean usageRecorded,
        List<ProductionTaskMaterialUsageView> materials) {}

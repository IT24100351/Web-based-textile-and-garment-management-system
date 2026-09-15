package lk.ac.sliit.tgms.production;

import java.util.List;
import lk.ac.sliit.tgms.order.OrderHandoff;

/** Production task aggregate without duplicating Order or Inventory source-of-truth data. */
public record ProductionTaskDetailView(
        ProductionTask task,
        OrderHandoff order,
        ProductionTaskWorkDetails workDetails,
        List<ProductionTaskMaterialRequirementView> materialRequirements) {}

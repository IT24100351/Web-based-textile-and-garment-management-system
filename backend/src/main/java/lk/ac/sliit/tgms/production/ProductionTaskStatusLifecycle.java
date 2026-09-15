package lk.ac.sliit.tgms.production;

import java.util.List;

/** Central TGMS-56 Production lifecycle policy. */
public final class ProductionTaskStatusLifecycle {

    private ProductionTaskStatusLifecycle() {}

    public static List<ProductionTaskStatus> allowedTransitions(ProductionTaskStatus current) {
        return switch (current) {
            case PENDING -> List.of(ProductionTaskStatus.IN_PROGRESS);
            case IN_PROGRESS -> List.of(ProductionTaskStatus.COMPLETED);
            case COMPLETED -> List.of();
        };
    }

    public static boolean canTransition(ProductionTaskStatus current, ProductionTaskStatus requested) {
        return allowedTransitions(current).contains(requested);
    }
}

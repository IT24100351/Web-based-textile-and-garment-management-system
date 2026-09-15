package lk.ac.sliit.tgms.production;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductionTaskStatusLifecycleTests {

    @Test
    void lifecycleAllowsOnlyForwardProductionProgress() {
        assertThat(ProductionTaskStatusLifecycle.allowedTransitions(ProductionTaskStatus.PENDING))
                .containsExactly(ProductionTaskStatus.IN_PROGRESS);
        assertThat(ProductionTaskStatusLifecycle.allowedTransitions(ProductionTaskStatus.IN_PROGRESS))
                .containsExactly(ProductionTaskStatus.COMPLETED);
        assertThat(ProductionTaskStatusLifecycle.allowedTransitions(ProductionTaskStatus.COMPLETED))
                .isEmpty();
    }

    @Test
    void lifecycleRejectsSkippingBackwardAndTerminalChanges() {
        assertThat(ProductionTaskStatusLifecycle.canTransition(
                ProductionTaskStatus.PENDING, ProductionTaskStatus.COMPLETED)).isFalse();
        assertThat(ProductionTaskStatusLifecycle.canTransition(
                ProductionTaskStatus.IN_PROGRESS, ProductionTaskStatus.PENDING)).isFalse();
        assertThat(ProductionTaskStatusLifecycle.canTransition(
                ProductionTaskStatus.COMPLETED, ProductionTaskStatus.IN_PROGRESS)).isFalse();
    }
}

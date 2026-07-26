package lk.ac.sliit.tgms.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryStatusLifecycleTests {

    @Test
    void exposesOnlyApprovedDeliveryTransitions() {
        assertThat(DeliveryStatusLifecycle.allowedTransitions(DeliveryStatus.SCHEDULED))
                .containsExactly(DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.CANCELLED);
        assertThat(DeliveryStatusLifecycle.allowedTransitions(DeliveryStatus.OUT_FOR_DELIVERY))
                .containsExactly(DeliveryStatus.DELIVERED);
        assertThat(DeliveryStatusLifecycle.allowedTransitions(DeliveryStatus.DELIVERED)).isEmpty();
        assertThat(DeliveryStatusLifecycle.allowedTransitions(DeliveryStatus.CANCELLED)).isEmpty();
    }

    @Test
    void rejectsSkippedBackwardAndTerminalTransitions() {
        assertThat(DeliveryStatusLifecycle.canTransition(DeliveryStatus.SCHEDULED, DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatusLifecycle.canTransition(DeliveryStatus.SCHEDULED, DeliveryStatus.DELIVERED)).isFalse();
        assertThat(DeliveryStatusLifecycle.canTransition(DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.SCHEDULED)).isFalse();
        assertThat(DeliveryStatusLifecycle.canTransition(DeliveryStatus.DELIVERED, DeliveryStatus.SCHEDULED)).isFalse();
        assertThat(DeliveryStatusLifecycle.canTransition(DeliveryStatus.CANCELLED, DeliveryStatus.OUT_FOR_DELIVERY)).isFalse();
    }
}

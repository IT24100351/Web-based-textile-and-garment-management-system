package lk.ac.sliit.tgms.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderStatusLifecycleTests {

    @Test
    void exposesOnlyApprovedForwardTransitions() {
        assertThat(OrderStatusLifecycle.allowedTransitions(OrderStatus.PENDING))
                .containsExactly(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(OrderStatusLifecycle.allowedTransitions(OrderStatus.CONFIRMED))
                .containsExactly(OrderStatus.IN_PRODUCTION, OrderStatus.CANCELLED);
        assertThat(OrderStatusLifecycle.allowedTransitions(OrderStatus.IN_PRODUCTION))
                .containsExactly(OrderStatus.READY_FOR_DELIVERY);
        assertThat(OrderStatusLifecycle.allowedTransitions(OrderStatus.READY_FOR_DELIVERY)).isEmpty();
        assertThat(OrderStatusLifecycle.allowedTransitions(OrderStatus.COMPLETED)).isEmpty();
        assertThat(OrderStatusLifecycle.allowedTransitions(OrderStatus.CANCELLED)).isEmpty();
    }

    @Test
    void rejectsSkippingBackwardsAndTerminalTransitions() {
        List.of(
                        new Transition(OrderStatus.PENDING, OrderStatus.IN_PRODUCTION),
                        new Transition(OrderStatus.CONFIRMED, OrderStatus.COMPLETED),
                        new Transition(OrderStatus.IN_PRODUCTION, OrderStatus.CONFIRMED),
                        new Transition(OrderStatus.READY_FOR_DELIVERY, OrderStatus.COMPLETED),
                        new Transition(OrderStatus.READY_FOR_DELIVERY, OrderStatus.CANCELLED),
                        new Transition(OrderStatus.COMPLETED, OrderStatus.PENDING),
                        new Transition(OrderStatus.CANCELLED, OrderStatus.CONFIRMED))
                .forEach(transition -> assertThat(OrderStatusLifecycle.canTransition(
                                transition.from(), transition.to()))
                        .isFalse());
    }

    private record Transition(OrderStatus from, OrderStatus to) {}
}

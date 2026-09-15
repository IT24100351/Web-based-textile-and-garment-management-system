package lk.ac.sliit.tgms.order;

import java.util.List;

/**
 * TGMS-46 central order lifecycle policy. Future Production/Delivery integrations must
 * call the Order service rather than updating orders.status directly.
 */
public final class OrderStatusLifecycle {

    private OrderStatusLifecycle() {}

    public static List<OrderStatus> allowedTransitions(OrderStatus current) {
        return switch (current) {
           
        };
    }

    public static boolean canTransition(OrderStatus current, OrderStatus requested) {
        return allowedTransitions(current).contains(requested);
    }

    /**
     * Status changes that Sales/Admin staff may perform directly from Order Management.
     * Production and Delivery own the later lifecycle transitions through their synchronization
     * services, so exposing those transitions here would allow staff to bypass required work.
     */
    public static List<OrderStatus> allowedStaffTransitions(OrderStatus current) {
        return switch (current) {
            case PENDING -> List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> List.of(OrderStatus.CANCELLED);
            case IN_PRODUCTION, READY_FOR_DELIVERY, COMPLETED, CANCELLED -> List.of();
        };
    }

    public static boolean canStaffTransition(OrderStatus current, OrderStatus requested) {
        return allowedStaffTransitions(current).contains(requested);
    }
}

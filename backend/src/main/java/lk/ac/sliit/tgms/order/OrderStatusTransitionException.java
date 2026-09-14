package lk.ac.sliit.tgms.order;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrderStatusTransitionException extends RuntimeException {

    private final Map<String, String> fields;

    public OrderStatusTransitionException(OrderStatus current, OrderStatus requested) {
        this(current, requested, OrderStatusLifecycle.allowedTransitions(current), false);
    }

    public static OrderStatusTransitionException forStaff(
            OrderStatus current, OrderStatus requested) {
        return new OrderStatusTransitionException(
                current,
                requested,
                OrderStatusLifecycle.allowedStaffTransitions(current),
                true);
    }

    private OrderStatusTransitionException(
            OrderStatus current,
            OrderStatus requested,
            List<OrderStatus> allowed,
            boolean staffWorkflow) {
        super(message(current, requested));
        this.fields = Map.of("status", fieldMessage(current, allowed, staffWorkflow));
    }

    public Map<String, String> fields() {
        return fields;
    }

    private static String message(OrderStatus current, OrderStatus requested) {
        return "Order status cannot move from " + current.name() + " to " + requested.name() + ".";
    }

    private static String fieldMessage(
            OrderStatus current, List<OrderStatus> allowed, boolean staffWorkflow) {
        if (current == OrderStatus.READY_FOR_DELIVERY) {
            return "Order completion is synchronized by Delivery Management after the delivery is marked delivered.";
        }
        if (staffWorkflow && current == OrderStatus.IN_PRODUCTION) {
            return "Production progress is synchronized by Production Management and cannot be changed here.";
        }
        if (staffWorkflow && current == OrderStatus.CONFIRMED) {
            return "Production starts through Production Management. The only manual Order action currently available is CANCELLED.";
        }
        if (allowed.isEmpty()) {
            return "This order is in a terminal status and cannot be changed.";
        }
        return "Allowed next status: " + allowed.stream()
                .map(OrderStatus::name)
                .collect(Collectors.joining(" or ")) + ".";
    }
}

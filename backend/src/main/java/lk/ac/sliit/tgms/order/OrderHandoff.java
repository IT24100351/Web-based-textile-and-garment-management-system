package lk.ac.sliit.tgms.order;

import java.util.List;

/** Stable Order Management handoff contract for later Production/Delivery modules. */
public record OrderHandoff(
        long orderId,
        String orderNumber,
        long customerId,
        OrderStatus currentStatus,
        boolean readyForProduction,
        boolean readyForDelivery,
        List<OrderHandoffItem> items) {

    static OrderHandoff from(OrderDetail order) {
        return new OrderHandoff(
                order.id(),
                order.orderNumber(),
                order.customerId(),
                order.status(),
                order.status() == OrderStatus.CONFIRMED,
                order.status() == OrderStatus.READY_FOR_DELIVERY,
                order.items().stream().map(OrderHandoffItem::from).toList());
    }
}

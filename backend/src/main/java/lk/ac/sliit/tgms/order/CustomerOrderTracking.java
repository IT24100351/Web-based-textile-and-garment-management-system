package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Customer-facing Order Management tracking snapshot.
 *
 * <p>This intentionally contains only Order Management facts. Production and Delivery modules can
 * later extend the tracking API with sibling sections without changing or replacing the immutable
 * order status history stored here.</p>
 */
public record CustomerOrderTracking(
        long orderId,
        String orderNumber,
        OrderStatus currentStatus,
        Instant placedAt,
        Instant lastUpdatedAt,
        int itemCount,
        BigDecimal totalAmount,
        List<OrderStatusHistoryEntry> orderHistory) {

    public static CustomerOrderTracking from(OrderDetail order) {
        return new CustomerOrderTracking(
                order.id(),
                order.orderNumber(),
                order.status(),
                order.createdAt(),
                order.updatedAt(),
                order.items().size(),
                order.totalAmount(),
                List.copyOf(order.statusHistory()));
    }
}

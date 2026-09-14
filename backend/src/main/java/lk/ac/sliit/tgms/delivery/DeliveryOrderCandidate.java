package lk.ac.sliit.tgms.delivery;

import java.math.BigDecimal;
import java.util.List;
import lk.ac.sliit.tgms.order.OrderHandoffItem;
import lk.ac.sliit.tgms.order.OrderStatus;

/**
 * Read-only Order Management projection used while a Sales Officer selects an order for Delivery.
 * No customer/order master data is persisted in Delivery by this record.
 */
public record DeliveryOrderCandidate(
        long orderId,
        String orderNumber,
        long customerId,
        String customerName,
        String customerEmail,
        OrderStatus currentStatus,
        int itemCount,
        BigDecimal totalAmount,
        boolean readyForDelivery,
        List<OrderHandoffItem> items) {}

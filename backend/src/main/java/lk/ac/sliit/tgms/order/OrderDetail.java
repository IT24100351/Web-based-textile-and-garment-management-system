package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetail(
        long id,
        String orderNumber,
        long customerId,
        String customerName,
        String customerEmail,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<OrderDetailItem> items,
        BigDecimal totalAmount,
        List<OrderStatusHistoryEntry> statusHistory) {}

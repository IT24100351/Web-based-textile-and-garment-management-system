package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummary(
        long id,
        String orderNumber,
        long customerId,
        String customerName,
        String customerEmail,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        int itemCount,
        BigDecimal totalAmount) {}

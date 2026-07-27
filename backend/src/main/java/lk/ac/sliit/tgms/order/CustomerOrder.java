package lk.ac.sliit.tgms.order;

import java.time.Instant;

/** Order Management's stable customer-order header model. */
public record CustomerOrder(
        long id,
        long customerId,
        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {}

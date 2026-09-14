package lk.ac.sliit.tgms.delivery;

import java.time.Instant;

/**
 * Stable Delivery Management record linked to an Order Management order.
 *
 * <p>Customer/order master data remains owned by Order Management. The delivery record stores only
 * delivery-owned scheduling and destination information.
 */
public record DeliveryRecord(
        long id,
        String deliveryNumber,
        long orderId,
        Instant scheduledAt,
        String deliveryAddress,
        String deliveryNotes,
        DeliveryStatus status,
        Instant createdAt,
        Instant updatedAt) {}

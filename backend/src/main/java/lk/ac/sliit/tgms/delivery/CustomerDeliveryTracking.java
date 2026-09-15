package lk.ac.sliit.tgms.delivery;

import java.time.Instant;

/**
 
 * <p>The Order module proves ownership before Delivery data is read. A missing Delivery is a
 * normal state and is represented by a null {@link #delivery()} rather than an error.</p>
 */
public record CustomerDeliveryTracking(long orderId, DeliveryProgress delivery) {

    public boolean hasDelivery() {
        return delivery != null;
    }

    
    

    public static CustomerDeliveryTracking from(DeliveryRecord delivery) {
        return new CustomerDeliveryTracking(
                delivery.orderId(),
                new DeliveryProgress(
                        delivery.id(),
                        delivery.deliveryNumber(),
                        delivery.scheduledAt(),
                        delivery.status(),
                        delivery.date())),
                        delivery.updatedAt()));
    }

    public record DeliveryProgress(
            long deliveryId,
            String deliveryNumber,
            Instant scheduledAt,
            DeliveryStatus status,
            Instant lastUpdatedAt) {}
}

package lk.ac.sliit.tgms.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository {

    /** True when an Order already has a non-cancelled Delivery that blocks replacement scheduling. */
    boolean existsActiveByOrderId(long orderId);

    Optional<DeliveryRecord> findById(long deliveryId);

    /** Returns the current non-cancelled Delivery first, otherwise the latest cancelled history row. */
    Optional<DeliveryRecord> findByOrderId(long orderId);

    List<DeliveryRecord> findRecords(String search, DeliveryStatus status);

    Optional<DeliveryScheduleConflict> findActiveScheduleConflict(
            Instant windowStartExclusive,
            Instant windowEndExclusive);

    boolean updateStatus(long deliveryId, DeliveryStatus expectedCurrent, DeliveryStatus requested);

    DeliveryRecord createDelivery(
            long orderId,
            String deliveryNumber,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes);
}

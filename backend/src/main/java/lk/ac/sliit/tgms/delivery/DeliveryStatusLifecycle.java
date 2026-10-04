package lk.ac.sliit.tgms.delivery;

import java.util.List;

/** TGMS-64/TGMS-66 controlled Delivery lifecycle policy. */
public final class DeliveryStatusLifecycle {

    private DeliveryStatusLifecycle() {}

    public static List<DeliveryStatus> allowedTransitions(DeliveryStatus current) {
        return switch (current) {
            // TGMS-66 safe remove behavior: an incorrect Delivery may be cancelled only before
            // dispatch. The row remains historical and its active Order lock is released.
            case SCHEDULED -> List.of(DeliveryStatus.OUT_FOR_DELIVERY, DeliveryStatus.CANCELLED);
            case OUT_FOR_DELIVERY -> List.of(DeliveryStatus.DELIVERED);
            case DELIVERED, CANCELLED -> List.of();
        };
    }

    public static boolean canTransition(DeliveryStatus current, DeliveryStatus requested) {
        return allowedTransitions(current).contains(requested);
    }
}

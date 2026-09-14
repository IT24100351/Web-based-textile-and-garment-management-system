package lk.ac.sliit.tgms.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.ac.sliit.tgms.notification.NotificationDispatchService;
import lk.ac.sliit.tgms.order.OrderDetail;
import lk.ac.sliit.tgms.order.OrderHandoff;
import lk.ac.sliit.tgms.order.OrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    static final Duration MINIMUM_SCHEDULE_SEPARATION = Duration.ofMinutes(60);
    private static final int MAX_RECORD_SEARCH_LENGTH = 120;

    private final OrderService orderService;
    private final DeliveryRepository deliveryRepository;
    private final NotificationDispatchService notificationDispatchService;

    public DeliveryService(
            OrderService orderService,
            DeliveryRepository deliveryRepository,
            NotificationDispatchService notificationDispatchService) {
        this.orderService = orderService;
        this.deliveryRepository = deliveryRepository;
        this.notificationDispatchService = notificationDispatchService;
    }

    /**
     * TGMS-65 customer Delivery tracking. Order Management proves ownership with a query scoped
     * by both customer ID and order ID before Delivery reads its own record by stable order ID.
     * No Delivery row yet is a normal tracking state, not an error.
     */
    @Transactional(readOnly = true)
    public CustomerDeliveryTracking getCustomerDeliveryTracking(long customerId, long orderId) {
        OrderDetail ownedOrder = orderService.getCustomerOrder(customerId, orderId);
        return deliveryRepository.findByOrderId(ownedOrder.id())
                .map(CustomerDeliveryTracking::from)
                .orElseGet(() -> CustomerDeliveryTracking.none(ownedOrder.id()));
    }

    /** TGMS-66 staff Delivery records with Delivery-owned search/status filtering. */
    @Transactional(readOnly = true)
    public List<DeliveryStaffRecord> getStaffRecords(String search, String status) {
        String normalizedSearch = normalizeOptional(search);
        if (normalizedSearch != null && normalizedSearch.length() > MAX_RECORD_SEARCH_LENGTH) {
            throw new DeliveryValidationException(
                    Map.of("search", "Delivery search must be 120 characters or fewer."));
        }
        DeliveryStatus parsedStatus = parseOptionalStatus(status);
        return deliveryRepository.findRecords(normalizedSearch, parsedStatus).stream()
                .map(this::staffRecord)
                .toList();
    }

    /** TGMS-66 complete staff detail resolved through the Order Management read contract. */
    @Transactional(readOnly = true)
    public DeliveryStaffRecord getStaffRecord(long deliveryId) {
        if (deliveryId <= 0) {
            throw new DeliveryValidationException(
                    Map.of("deliveryId", "Select a valid positive delivery ID."));
        }
        DeliveryRecord delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(DeliveryNotFoundException::new);
        return staffRecord(delivery);
    }

    /**
     * Lists orders that Order Management currently marks ready for Delivery and that do not have a
     * non-cancelled Delivery. Cancelled history is deliberately retained but releases the order for
     * a corrected replacement schedule.
     */
    @Transactional(readOnly = true)
    public List<DeliveryOrderCandidate> getEligibleOrders(String search) {
        return orderService.getDeliveryEligibleHandoffs(search).stream()
                .filter(handoff -> !deliveryRepository.existsActiveByOrderId(handoff.orderId()))
                .map(this::candidate)
                .toList();
    }

    /** Revalidates one selected order immediately before a Delivery workflow may proceed. */
    @Transactional(readOnly = true)
    public DeliveryOrderCandidate validateSelectedOrder(long orderId) {
        if (orderId <= 0) {
            throw new DeliveryValidationException(
                    Map.of("orderId", "Select a valid positive order ID."));
        }

        OrderHandoff handoff = orderService.getHandoff(orderId);
        if (!handoff.readyForDelivery()) {
            throw new DeliveryOrderNotEligibleException(handoff.currentStatus());
        }
        if (deliveryRepository.existsActiveByOrderId(orderId)) {
            throw new DeliveryOrderAlreadyAssignedException();
        }
        return candidate(handoff);
    }

    /** Creates the Delivery record after revalidating order readiness and schedule availability. */
    @Transactional
    public DeliveryRecord scheduleDelivery(
            long orderId,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes) {
        validateSelectedOrder(orderId);
        ScheduleDetails details = validateSchedule(scheduledAt, deliveryAddress, deliveryNotes);
        rejectScheduleConflict(details.scheduledAt());

        try {
            return deliveryRepository.createDelivery(
                    orderId,
                    generateDeliveryNumber(),
                    details.scheduledAt(),
                    details.deliveryAddress(),
                    details.deliveryNotes());
        } catch (DuplicateKeyException exception) {
            // The active-order lock is the final concurrent-submit guard. Cancelled history has no
            // active lock and therefore does not block a replacement Delivery.
            if (deliveryRepository.existsActiveByOrderId(orderId)) {
                throw new DeliveryOrderAlreadyAssignedException();
            }
            throw exception;
        }
    }

    /**
     * Controlled Delivery lifecycle. SCHEDULED may be cancelled safely before dispatch; cancellation
     * releases only the active-order lock and never deletes the historical Delivery row.
     */
    @Transactional
    public DeliveryRecord updateStatus(long deliveryId, String requestedStatus, long changedByUserId) {
        if (deliveryId <= 0) {
            throw new DeliveryValidationException(Map.of(
                    "deliveryId", "Select a valid positive delivery ID."));
        }
        if (changedByUserId <= 0) {
            throw new DeliveryValidationException(Map.of(
                    "userId", "Authenticated user ID must be positive."));
        }

        DeliveryStatus requested = parseStatus(requestedStatus);
        DeliveryRecord current = deliveryRepository.findById(deliveryId)
                .orElseThrow(DeliveryNotFoundException::new);

        if (current.status() == requested) {
            return current;
        }
        if (!DeliveryStatusLifecycle.canTransition(current.status(), requested)) {
            throw new DeliveryStatusTransitionException(current.status(), requested);
        }

        if (requested == DeliveryStatus.OUT_FOR_DELIVERY || requested == DeliveryStatus.DELIVERED) {
            OrderHandoff handoff = orderService.getHandoff(current.orderId());
            if (!handoff.readyForDelivery()) {
                throw new DeliveryOrderNotEligibleException(handoff.currentStatus());
            }
        }

        boolean updated = deliveryRepository.updateStatus(deliveryId, current.status(), requested);
        if (!updated) {
            DeliveryRecord latest = deliveryRepository.findById(deliveryId)
                    .orElseThrow(DeliveryNotFoundException::new);
            if (latest.status() == requested) {
                return latest;
            }
            throw new DeliveryStatusTransitionException(latest.status(), requested);
        }

        if (requested == DeliveryStatus.DELIVERED) {
            orderService.synchronizeDeliveryCompleted(changedByUserId, current.orderId());
        }

        DeliveryRecord updatedDelivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(DeliveryNotFoundException::new);
        if (requested == DeliveryStatus.OUT_FOR_DELIVERY
                || requested == DeliveryStatus.DELIVERED
                || requested == DeliveryStatus.CANCELLED) {
            OrderHandoff handoff = orderService.getHandoff(current.orderId());
            notificationDispatchService.deliveryStatus(
                    handoff.customerId(),
                    updatedDelivery.id(),
                    updatedDelivery.deliveryNumber(),
                    handoff.orderNumber(),
                    requested);
        }
        return updatedDelivery;
    }

    private DeliveryStaffRecord staffRecord(DeliveryRecord delivery) {
        return new DeliveryStaffRecord(delivery, orderService.getStaffOrder(delivery.orderId()));
    }

    private DeliveryStatus parseOptionalStatus(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return parseStatus(normalized);
    }

    private DeliveryStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new DeliveryValidationException(Map.of(
                    "status",
                    "Status must be SCHEDULED, OUT_FOR_DELIVERY, DELIVERED, or CANCELLED."));
        }
        try {
            return DeliveryStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new DeliveryValidationException(Map.of(
                    "status",
                    "Status must be SCHEDULED, OUT_FOR_DELIVERY, DELIVERED, or CANCELLED."));
        }
    }

    private void rejectScheduleConflict(Instant scheduledAt) {
        Instant windowStart = scheduledAt.minus(MINIMUM_SCHEDULE_SEPARATION);
        Instant windowEnd = scheduledAt.plus(MINIMUM_SCHEDULE_SEPARATION);
        deliveryRepository.findActiveScheduleConflict(windowStart, windowEnd)
                .ifPresent(conflict -> {
                    throw new DeliveryScheduleConflictException(conflict);
                });
    }

    private ScheduleDetails validateSchedule(
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes) {
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        Instant now = Instant.now();
        if (scheduledAt == null) {
            fields.put("scheduledAt", "Enter a delivery date and time.");
        } else if (!scheduledAt.isAfter(now)) {
            fields.put("scheduledAt", "Delivery date and time must be in the future.");
        }

        String address = normalizeRequired(deliveryAddress);
        if (address == null) {
            fields.put("deliveryAddress", "Enter the delivery address.");
        } else if (address.length() > 500) {
            fields.put("deliveryAddress", "Delivery address must be 500 characters or fewer.");
        }

        String notes = normalizeOptional(deliveryNotes);
        if (notes != null && notes.length() > 1000) {
            fields.put("deliveryNotes", "Delivery notes must be 1000 characters or fewer.");
        }

        if (!fields.isEmpty()) {
            throw new DeliveryValidationException(Map.copyOf(fields));
        }
        return new ScheduleDetails(scheduledAt, address, notes);
    }

    private DeliveryOrderCandidate candidate(OrderHandoff handoff) {
        OrderDetail order = orderService.getStaffOrder(handoff.orderId());
        return new DeliveryOrderCandidate(
                handoff.orderId(),
                handoff.orderNumber(),
                handoff.customerId(),
                order.customerName(),
                order.customerEmail(),
                handoff.currentStatus(),
                handoff.items().size(),
                order.totalAmount(),
                handoff.readyForDelivery(),
                handoff.items());
    }

    private String generateDeliveryNumber() {
        return "DLV-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private record ScheduleDetails(
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes) {}
}

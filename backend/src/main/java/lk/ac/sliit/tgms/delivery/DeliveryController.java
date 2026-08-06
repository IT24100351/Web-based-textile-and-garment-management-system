package lk.ac.sliit.tgms.delivery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.ac.sliit.tgms.authorization.RoleGuards.CustomerOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.SalesOfficerOnly;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.order.OrderDetailItem;
import lk.ac.sliit.tgms.order.OrderHandoffItem;
import lk.ac.sliit.tgms.order.OrderStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/mine/orders/{orderId}/tracking")
    @CustomerOnly
    public CustomerDeliveryTrackingResponse myDeliveryTracking(
            @PathVariable long orderId,
            @AuthenticationPrincipal Jwt jwt) {
        return CustomerDeliveryTrackingResponse.from(
                deliveryService.getCustomerDeliveryTracking(currentUserId(jwt), orderId));
    }

    /** TGMS-66 staff Delivery records list/search. */
    @GetMapping
    @SalesOfficerOnly
    public List<StaffDeliveryResponse> deliveries(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return deliveryService.getStaffRecords(search, status).stream()
                .map(StaffDeliveryResponse::from)
                .toList();
    }

    /** TGMS-66 staff Delivery detail. */
    @GetMapping("/{deliveryId}")
    @SalesOfficerOnly
    public StaffDeliveryResponse delivery(@PathVariable long deliveryId) {
        return StaffDeliveryResponse.from(deliveryService.getStaffRecord(deliveryId));
    }

    @GetMapping("/eligible-orders")
    @SalesOfficerOnly
    public List<EligibleDeliveryOrderResponse> eligibleOrders(
            @RequestParam(required = false) String search) {
        return deliveryService.getEligibleOrders(search).stream()
                .map(EligibleDeliveryOrderResponse::from)
                .toList();
    }

    @GetMapping("/eligible-orders/{orderId}")
    @SalesOfficerOnly
    public EligibleDeliveryOrderResponse validateSelectedOrder(@PathVariable long orderId) {
        return EligibleDeliveryOrderResponse.from(deliveryService.validateSelectedOrder(orderId));
    }

    @PatchMapping("/{deliveryId}/status")
    @SalesOfficerOnly
    public UpdateDeliveryStatusResponse updateStatus(
            @PathVariable long deliveryId,
            @RequestBody UpdateDeliveryStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        DeliveryRecord delivery = deliveryService.updateStatus(
                deliveryId, request == null ? null : request.status(), currentUserId(jwt));
        return new UpdateDeliveryStatusResponse(
                "Delivery status updated successfully.", DeliveryResponse.from(delivery));
    }

    @PostMapping
    @SalesOfficerOnly
    public ScheduleDeliveryResponse scheduleDelivery(@RequestBody ScheduleDeliveryRequest request) {
        DeliveryRecord delivery = deliveryService.scheduleDelivery(
                request == null || request.orderId() == null ? 0L : request.orderId(),
                request == null ? null : request.scheduledAt(),
                request == null ? null : request.deliveryAddress(),
                request == null ? null : request.deliveryNotes());
        return new ScheduleDeliveryResponse(
                "Delivery scheduled successfully.", DeliveryResponse.from(delivery));
    }

    public record CustomerDeliveryTrackingResponse(
            long orderId,
            boolean hasDelivery,
            CustomerDeliveryProgressResponse delivery) {

        static CustomerDeliveryTrackingResponse from(CustomerDeliveryTracking tracking) {
            return new CustomerDeliveryTrackingResponse(
                    tracking.orderId(),
                    tracking.hasDelivery(),
                    tracking.delivery() == null
                            ? null
                            : CustomerDeliveryProgressResponse.from(tracking.delivery()));
        }
    }

    public record CustomerDeliveryProgressResponse(
            long deliveryId,
            String deliveryNumber,
            Instant scheduledAt,
            DeliveryStatus status,
            Instant lastUpdatedAt) {

        static CustomerDeliveryProgressResponse from(
                CustomerDeliveryTracking.DeliveryProgress delivery) {
            return new CustomerDeliveryProgressResponse(
                    delivery.deliveryId(),
                    delivery.deliveryNumber(),
                    delivery.scheduledAt(),
                    delivery.status(),
                    delivery.lastUpdatedAt());
        }
    }

    public record StaffDeliveryResponse(
            DeliveryResponse delivery,
            String orderNumber,
            long customerId,
            String customerName,
            String customerEmail,
            OrderStatus orderStatus,
            BigDecimal orderTotal,
            List<StaffDeliveryOrderItemResponse> items) {

        static StaffDeliveryResponse from(DeliveryStaffRecord record) {
            return new StaffDeliveryResponse(
                    DeliveryResponse.from(record.delivery()),
                    record.order().orderNumber(),
                    record.order().customerId(),
                    record.order().customerName(),
                    record.order().customerEmail(),
                    record.order().status(),
                    record.order().totalAmount(),
                    record.order().items().stream().map(StaffDeliveryOrderItemResponse::from).toList());
        }
    }

    public record StaffDeliveryOrderItemResponse(
            long orderItemId,
            long productId,
            long variantId,
            String productName,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot,
            BigDecimal lineTotal) {

        static StaffDeliveryOrderItemResponse from(OrderDetailItem item) {
            return new StaffDeliveryOrderItemResponse(
                    item.id(),
                    item.productId(),
                    item.variantId(),
                    item.productName(),
                    item.quantity(),
                    item.selectedSize(),
                    item.selectedColor(),
                    item.unitPriceSnapshot(),
                    item.lineTotal());
        }
    }

    public record EligibleDeliveryOrderResponse(
            long orderId,
            String orderNumber,
            long customerId,
            String customerName,
            String customerEmail,
            OrderStatus currentStatus,
            int itemCount,
            BigDecimal totalAmount,
            boolean readyForDelivery,
            List<EligibleDeliveryOrderItemResponse> items) {

        static EligibleDeliveryOrderResponse from(DeliveryOrderCandidate candidate) {
            return new EligibleDeliveryOrderResponse(
                    candidate.orderId(),
                    candidate.orderNumber(),
                    candidate.customerId(),
                    candidate.customerName(),
                    candidate.customerEmail(),
                    candidate.currentStatus(),
                    candidate.itemCount(),
                    candidate.totalAmount(),
                    candidate.readyForDelivery(),
                    candidate.items().stream().map(EligibleDeliveryOrderItemResponse::from).toList());
        }
    }

    public record EligibleDeliveryOrderItemResponse(
            long orderItemId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor) {

        static EligibleDeliveryOrderItemResponse from(OrderHandoffItem item) {
            return new EligibleDeliveryOrderItemResponse(
                    item.orderItemId(),
                    item.productId(),
                    item.variantId(),
                    item.quantity(),
                    item.selectedSize(),
                    item.selectedColor());
        }
    }

    public record ScheduleDeliveryRequest(
            Long orderId,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes) {}

    public record ScheduleDeliveryResponse(String message, DeliveryResponse delivery) {}

    public record DeliveryResponse(
            long id,
            String deliveryNumber,
            long orderId,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes,
            DeliveryStatus status,
            List<DeliveryStatus> allowedStatusTransitions,
            Instant createdAt,
            Instant updatedAt) {

        static DeliveryResponse from(DeliveryRecord delivery) {
            return new DeliveryResponse(
                    delivery.id(),
                    delivery.deliveryNumber(),
                    delivery.orderId(),
                    delivery.scheduledAt(),
                    delivery.deliveryAddress(),
                    delivery.deliveryNotes(),
                    delivery.status(),
                    DeliveryStatusLifecycle.allowedTransitions(delivery.status()),
                    delivery.createdAt(),
                    delivery.updatedAt());
        }
    }

    public record UpdateDeliveryStatusRequest(String status) {}

    public record UpdateDeliveryStatusResponse(String message, DeliveryResponse delivery) {}

    private long currentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidSessionException();
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new InvalidSessionException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new InvalidSessionException();
        }
    }
}

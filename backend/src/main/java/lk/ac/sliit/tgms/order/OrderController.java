package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.authorization.RoleGuards.CustomerOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.OrderBillingWritable;
import lk.ac.sliit.tgms.authorization.RoleGuards.OrderHandoffReadable;
import lk.ac.sliit.tgms.authorization.RoleGuards.OrderStaffReadable;
import lk.ac.sliit.tgms.authorization.RoleGuards.OrderStatusWritable;
import lk.ac.sliit.tgms.authorization.RoleGuards.SalesOfficerOnly;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/customers")
    @SalesOfficerOnly
    public List<CustomerResponse> customers(@RequestParam(required = false) String search) {
        return orderService.getSelectableCustomers(search).stream()
                .map(CustomerResponse::from)
                .toList();
    }

    @GetMapping
    @OrderStaffReadable
    public List<OrderSummaryResponse> orders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return orderService.getStaffOrders(search, status).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{orderId}")
    @OrderStaffReadable
    public OrderDetailResponse order(@PathVariable long orderId) {
        return OrderDetailResponse.forStaff(orderService.getStaffOrder(orderId));
    }

    @GetMapping("/{orderId}/billing")
    @OrderStaffReadable
    public OrderBillingResponse billing(@PathVariable long orderId) {
        return OrderBillingResponse.from(orderService.getStaffBilling(orderId));
    }

    @PostMapping("/{orderId}/invoice")
    @OrderBillingWritable
    public OrderBillingMutationResponse generateInvoice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId) {
        OrderBilling billing = orderService.generateInvoice(authenticatedUserId(jwt), orderId);
        return new OrderBillingMutationResponse(
                "Invoice generated successfully.", OrderBillingResponse.from(billing));
    }

    @PatchMapping("/{orderId}/payment")
    @OrderBillingWritable
    public OrderBillingMutationResponse recordPayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId,
            @RequestBody OrderPaymentUpdateRequest request) {
        OrderBilling billing = orderService.recordPayment(
                authenticatedUserId(jwt),
                orderId,
                request == null ? null : request.paymentStatus(),
                request == null ? null : request.amountPaid(),
                request == null ? null : request.paymentMethod(),
                request == null ? null : request.paymentReference(),
                request == null ? null : request.note());
        return new OrderBillingMutationResponse(
                "Payment record updated successfully.", OrderBillingResponse.from(billing));
    }

    @GetMapping("/{orderId}/handoff")
    @OrderHandoffReadable
    public OrderHandoffResponse handoff(@PathVariable long orderId) {
        return OrderHandoffResponse.from(orderService.getHandoff(orderId));
    }

    @PatchMapping("/{orderId}/status")
    @OrderStatusWritable
    public OrderStatusUpdateResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId,
            @RequestBody OrderStatusUpdateRequest request) {
        OrderDetail updated = orderService.updateStatus(
                authenticatedUserId(jwt),
                orderId,
                request == null ? null : request.status());
        return new OrderStatusUpdateResponse(
                "Order status updated successfully.", OrderDetailResponse.forStaff(updated));
    }

    @GetMapping("/mine")
    @CustomerOnly
    public List<OrderSummaryResponse> myOrders(@AuthenticationPrincipal Jwt jwt) {
        return orderService.getCustomerOrders(authenticatedUserId(jwt)).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    @GetMapping("/mine/{orderId}")
    @CustomerOnly
    public OrderDetailResponse myOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId) {
        return OrderDetailResponse.forCustomer(
                orderService.getCustomerOrder(authenticatedUserId(jwt), orderId));
    }

    @GetMapping("/mine/{orderId}/billing")
    @CustomerOnly
    public OrderBillingResponse myBilling(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId) {
        return OrderBillingResponse.from(
                orderService.getCustomerBilling(authenticatedUserId(jwt), orderId));
    }

    @GetMapping("/mine/{orderId}/tracking")
    @CustomerOnly
    public CustomerOrderTrackingResponse myOrderTracking(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long orderId) {
        return CustomerOrderTrackingResponse.from(
                orderService.getCustomerOrderTracking(authenticatedUserId(jwt), orderId));
    }

    @PostMapping
    @SalesOfficerOnly
    public ResponseEntity<CreateOrderResponse> create(
            @RequestBody CreateOrderRequest request) {
        CreatedCustomerOrder created = orderService.createOrder(
                request == null ? null : request.customerId(),
                request == null ? null : toCommands(request.items()));
        return createdResponse(created);
    }

    @PostMapping("/mine")
    @CustomerOnly
    public ResponseEntity<CreateOrderResponse> createForCurrentCustomer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CustomerCreateOrderRequest request) {
        long authenticatedCustomerId = authenticatedUserId(jwt);
        CreatedCustomerOrder created = orderService.createCustomerOrder(
                authenticatedCustomerId,
                request == null ? null : toCommands(request.items()));
        return createdResponse(created);
    }

    private List<CreateOrderItemCommand> toCommands(List<CreateOrderItemRequest> items) {
        if (items == null) {
            return null;
        }
        return items.stream()
                .map(item -> item == null
                        ? null
                        : new CreateOrderItemCommand(
                                item.productId(), item.variantId(), item.quantity()))
                .toList();
    }

    private ResponseEntity<CreateOrderResponse> createdResponse(CreatedCustomerOrder created) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateOrderResponse.from(created));
    }

    private long authenticatedUserId(Jwt jwt) {
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

    public record CreateOrderRequest(
            Long customerId,
            List<CreateOrderItemRequest> items) {}

    public record CustomerCreateOrderRequest(List<CreateOrderItemRequest> items) {}

    public record OrderStatusUpdateRequest(String status) {}

    public record OrderStatusUpdateResponse(String message, OrderDetailResponse order) {}

    public record OrderPaymentUpdateRequest(
            String paymentStatus,
            String amountPaid,
            String paymentMethod,
            String paymentReference,
            String note) {}

    public record OrderBillingMutationResponse(String message, OrderBillingResponse billing) {}

    public record CreateOrderItemRequest(Long productId, Long variantId, Integer quantity) {}

    public record OrderBillingResponse(
            boolean invoiceGenerated,
            OrderInvoiceResponse invoice,
            OrderPaymentResponse payment) {

        static OrderBillingResponse from(java.util.Optional<OrderBilling> billing) {
            return billing.map(OrderBillingResponse::from)
                    .orElseGet(() -> new OrderBillingResponse(false, null, null));
        }

        static OrderBillingResponse from(OrderBilling billing) {
            return new OrderBillingResponse(
                    true,
                    OrderInvoiceResponse.from(billing.invoice()),
                    OrderPaymentResponse.from(billing.payment()));
        }
    }

    public record OrderInvoiceResponse(
            long id,
            long orderId,
            String invoiceNumber,
            String totalAmount,
            Instant issuedAt) {

        static OrderInvoiceResponse from(OrderInvoice invoice) {
            return new OrderInvoiceResponse(
                    invoice.id(),
                    invoice.orderId(),
                    invoice.invoiceNumber(),
                    invoice.totalAmount().toPlainString(),
                    invoice.issuedAt());
        }
    }

    public record OrderPaymentResponse(
            long id,
            OrderPaymentStatus paymentStatus,
            String amountPaid,
            OrderPaymentMethod paymentMethod,
            String paymentReference,
            String note,
            Instant recordedAt,
            Instant updatedAt) {

        static OrderPaymentResponse from(OrderPaymentRecord payment) {
            return new OrderPaymentResponse(
                    payment.id(),
                    payment.paymentStatus(),
                    payment.amountPaid().toPlainString(),
                    payment.paymentMethod(),
                    payment.paymentReference(),
                    payment.note(),
                    payment.recordedAt(),
                    payment.updatedAt());
        }
    }

    public record OrderHandoffResponse(
            long orderId,
            String orderNumber,
            long customerId,
            OrderStatus currentStatus,
            boolean readyForProduction,
            boolean readyForDelivery,
            List<OrderHandoffItemResponse> items) {

        static OrderHandoffResponse from(OrderHandoff handoff) {
            return new OrderHandoffResponse(
                    handoff.orderId(),
                    handoff.orderNumber(),
                    handoff.customerId(),
                    handoff.currentStatus(),
                    handoff.readyForProduction(),
                    handoff.readyForDelivery(),
                    handoff.items().stream().map(OrderHandoffItemResponse::from).toList());
        }
    }

    public record OrderHandoffItemResponse(
            long orderItemId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor) {

        static OrderHandoffItemResponse from(OrderHandoffItem item) {
            return new OrderHandoffItemResponse(
                    item.orderItemId(), item.productId(), item.variantId(), item.quantity(),
                    item.selectedSize(), item.selectedColor());
        }
    }

    public record CustomerResponse(long id, String fullName, String email) {
        static CustomerResponse from(OrderCustomerOption customer) {
            return new CustomerResponse(customer.id(), customer.fullName(), customer.email());
        }
    }

    public record OrderSummaryResponse(
            long id,
            String orderNumber,
            long customerId,
            String customerName,
            String customerEmail,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            int itemCount,
            String totalAmount) {

        static OrderSummaryResponse from(OrderSummary order) {
            return new OrderSummaryResponse(
                    order.id(),
                    order.orderNumber(),
                    order.customerId(),
                    order.customerName(),
                    order.customerEmail(),
                    order.status(),
                    order.createdAt(),
                    order.updatedAt(),
                    order.itemCount(),
                    order.totalAmount().toPlainString());
        }
    }

    public record OrderDetailResponse(
            long id,
            String orderNumber,
            long customerId,
            String customerName,
            String customerEmail,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<OrderDetailItemResponse> items,
            String totalAmount,
            List<OrderStatus> allowedStatusTransitions,
            List<OrderStatusHistoryResponse> statusHistory) {

        static OrderDetailResponse forStaff(OrderDetail order) {
            return from(order, OrderStatusLifecycle.allowedStaffTransitions(order.status()));
        }

        static OrderDetailResponse forCustomer(OrderDetail order) {
            return from(order, List.of());
        }

        private static OrderDetailResponse from(
                OrderDetail order, List<OrderStatus> allowedStatusTransitions) {
            return new OrderDetailResponse(
                    order.id(),
                    order.orderNumber(),
                    order.customerId(),
                    order.customerName(),
                    order.customerEmail(),
                    order.status(),
                    order.createdAt(),
                    order.updatedAt(),
                    order.items().stream().map(OrderDetailItemResponse::from).toList(),
                    order.totalAmount().toPlainString(),
                    allowedStatusTransitions,
                    order.statusHistory().stream().map(OrderStatusHistoryResponse::from).toList());
        }
    }

    public record CustomerOrderTrackingResponse(
            long orderId,
            String orderNumber,
            OrderStatus currentStatus,
            Instant placedAt,
            Instant lastUpdatedAt,
            int itemCount,
            String totalAmount,
            List<OrderStatusHistoryResponse> orderHistory) {

        static CustomerOrderTrackingResponse from(CustomerOrderTracking tracking) {
            return new CustomerOrderTrackingResponse(
                    tracking.orderId(),
                    tracking.orderNumber(),
                    tracking.currentStatus(),
                    tracking.placedAt(),
                    tracking.lastUpdatedAt(),
                    tracking.itemCount(),
                    tracking.totalAmount().toPlainString(),
                    tracking.orderHistory().stream()
                            .map(OrderStatusHistoryResponse::from)
                            .toList());
        }
    }

    public record OrderStatusHistoryResponse(
            long id,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            Instant changedAt) {

        static OrderStatusHistoryResponse from(OrderStatusHistoryEntry entry) {
            return new OrderStatusHistoryResponse(
                    entry.id(), entry.fromStatus(), entry.toStatus(), entry.changedAt());
        }
    }

    public record OrderDetailItemResponse(
            long id,
            long productId,
            long variantId,
            String productName,
            int quantity,
            String selectedSize,
            String selectedColor,
            String unitPriceSnapshot,
            String lineTotal) {

        static OrderDetailItemResponse from(OrderDetailItem item) {
            return new OrderDetailItemResponse(
                    item.id(),
                    item.productId(),
                    item.variantId(),
                    item.productName(),
                    item.quantity(),
                    item.selectedSize(),
                    item.selectedColor(),
                    item.unitPriceSnapshot().toPlainString(),
                    item.lineTotal().toPlainString());
        }
    }

    public record CreateOrderResponse(
            String message,
            long id,
            String orderNumber,
            long customerId,
            CustomerResponse customer,
            OrderStatus status,
            Instant createdAt,
            String totalAmount,
            List<OrderItemResponse> items) {

        static CreateOrderResponse from(CreatedCustomerOrder created) {
            CustomerOrder order = created.order();
            BigDecimal totalAmount = created.items().stream()
                    .map(createdItem -> createdItem.item().unitPriceSnapshot().multiply(
                            BigDecimal.valueOf(createdItem.item().quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CreateOrderResponse(
                    "Customer order created successfully.",
                    order.id(),
                    order.orderNumber(),
                    order.customerId(),
                    CustomerResponse.from(created.customer()),
                    order.status(),
                    order.createdAt(),
                    totalAmount.toPlainString(),
                    created.items().stream().map(OrderItemResponse::from).toList());
        }
    }

    public record OrderItemResponse(
            long id,
            long productId,
            long variantId,
            String productName,
            int quantity,
            String selectedSize,
            String selectedColor,
            String unitPriceSnapshot,
            String lineTotal) {

        static OrderItemResponse from(CreatedOrderItem createdItem) {
            CustomerOrderItem item = createdItem.item();
            return new OrderItemResponse(
                    item.id(),
                    item.productId(),
                    item.variantId(),
                    createdItem.productName(),
                    item.quantity(),
                    item.selectedSize(),
                    item.selectedColor(),
                    item.unitPriceSnapshot().toPlainString(),
                    item.unitPriceSnapshot().multiply(BigDecimal.valueOf(item.quantity()))
                            .toPlainString());
        }
    }
}

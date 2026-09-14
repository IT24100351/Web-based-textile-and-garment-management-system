package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserAccountRepository;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.notification.NotificationDispatchService;
import lk.ac.sliit.tgms.order.OrderValidator.ValidatedOrder;
import lk.ac.sliit.tgms.order.OrderValidator.ValidatedOrderItem;
import lk.ac.sliit.tgms.product.OrderProductSelection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int MAX_CUSTOMER_SEARCH_LENGTH = 120;
    private static final int MAX_ORDER_SEARCH_LENGTH = 120;

    private final UserAccountRepository userAccountRepository;
    private final OrderValidator orderValidator;
    private final OrderRepository orderRepository;
    private final NotificationDispatchService notificationDispatchService;

    public OrderService(
            UserAccountRepository userAccountRepository,
            OrderValidator orderValidator,
            OrderRepository orderRepository,
            NotificationDispatchService notificationDispatchService) {
        this.userAccountRepository = userAccountRepository;
        this.orderValidator = orderValidator;
        this.orderRepository = orderRepository;
        this.notificationDispatchService = notificationDispatchService;
    }

    @Transactional(readOnly = true)
    public List<OrderCustomerOption> getSelectableCustomers(String search) {
        String normalizedSearch = normalizeOptional(search);
        if (normalizedSearch != null && normalizedSearch.length() > MAX_CUSTOMER_SEARCH_LENGTH) {
            throw new OrderValidationException(
                    Map.of("search", "Customer search must not exceed 120 characters."));
        }

        return userAccountRepository.findActiveByRole(UserRole.CUSTOMER, normalizedSearch).stream()
                .map(this::toCustomerOption)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> getStaffOrders(String search, String status) {
        return orderRepository.findOrders(readQuery(search, status), null);
    }

    @Transactional(readOnly = true)
    public OrderDetail getStaffOrder(long orderId) {
        return orderRepository.findOrderDetail(requireOrderId(orderId), null)
                .orElseThrow(OrderNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<OrderSummary> getCustomerOrders(long customerId) {
        return getCustomerOrders(customerId, null);
    }

    /** Role-scoped customer search reused by TGMS-74 shared search. */
    @Transactional(readOnly = true)
    public List<OrderSummary> getCustomerOrders(long customerId, String search) {
        return orderRepository.findOrders(readQuery(search, null), requireCustomerId(customerId));
    }

    @Transactional(readOnly = true)
    public OrderDetail getCustomerOrder(long customerId, long orderId) {
        return orderRepository.findOrderDetail(requireOrderId(orderId), requireCustomerId(customerId))
                .orElseThrow(OrderNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public CustomerOrderTracking getCustomerOrderTracking(long customerId, long orderId) {
        // Reuse the ownership-scoped detail lookup so unknown and not-owned IDs have the same
        // ORDER_NOT_FOUND behavior and do not leak another customer's order existence.
        return CustomerOrderTracking.from(getCustomerOrder(customerId, orderId));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<OrderBilling> getStaffBilling(long orderId) {
        getStaffOrder(orderId);
        return billingFor(orderId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<OrderBilling> getCustomerBilling(long customerId, long orderId) {
        getCustomerOrder(customerId, orderId);
        return billingFor(orderId);
    }

    @Transactional
    public OrderBilling generateInvoice(long issuedByUserId, long orderId) {
        OrderDetail order = getStaffOrder(orderId);
        if (order.status() == OrderStatus.CANCELLED) {
            throw new OrderBillingStateException(
                    "ORDER_CANCELLED", "A cancelled order cannot receive a new invoice.");
        }
        java.util.Optional<OrderBilling> existing = billingFor(order.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (order.totalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderBillingStateException(
                    "ORDER_HAS_NO_BILLABLE_ITEMS", "The order has no positive stored item total to invoice.");
        }

        OrderInvoice invoice = orderRepository.createInvoice(
                order.id(), generateInvoiceNumber(), order.totalAmount(), issuedByUserId);
        OrderPaymentRecord payment = orderRepository.createInitialPaymentRecord(
                order.id(), invoice.id(), issuedByUserId);
        return new OrderBilling(invoice, payment);
    }

    @Transactional
    public OrderBilling recordPayment(
            long recordedByUserId,
            long orderId,
            String requestedStatus,
            String requestedAmountPaid,
            String requestedMethod,
            String paymentReference,
            String note) {
        OrderDetail order = getStaffOrder(orderId);
        if (order.status() == OrderStatus.CANCELLED) {
            throw new OrderBillingStateException(
                    "ORDER_CANCELLED", "Payment details cannot be changed for a cancelled order.");
        }
        OrderBilling billing = billingFor(order.id()).orElseThrow(() ->
                new OrderBillingStateException(
                        "INVOICE_REQUIRED", "Generate the order invoice before recording payment details."));

        PaymentUpdate validated = validatePaymentUpdate(
                billing.invoice(), billing.payment(), requestedStatus, requestedAmountPaid, requestedMethod,
                paymentReference, note);
        OrderPaymentRecord payment = orderRepository.updatePaymentRecord(
                order.id(),
                validated.status(),
                validated.amountPaid(),
                validated.method(),
                validated.reference(),
                validated.note(),
                recordedByUserId);
        return new OrderBilling(billing.invoice(), payment);
    }

    @Transactional(readOnly = true)
    public List<OrderHandoff> getProductionEligibleHandoffs() {
        return orderRepository.findOrders(new OrderQuery(null, OrderStatus.CONFIRMED), null).stream()
                .map(summary -> OrderHandoff.from(getStaffOrder(summary.id())))
                .filter(OrderHandoff::readyForProduction)
                .toList();
    }

    /**
     * TGMS-61 Delivery selection contract. Delivery queries Order Management rather than reading
     * the orders table directly, and receives only handoffs whose current state is delivery-ready.
     */
    @Transactional(readOnly = true)
    public List<OrderHandoff> getDeliveryEligibleHandoffs(String search) {
        return orderRepository.findOrders(readQuery(search, OrderStatus.READY_FOR_DELIVERY.name()), null).stream()
                .map(summary -> OrderHandoff.from(getStaffOrder(summary.id())))
                .filter(OrderHandoff::readyForDelivery)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderHandoff getHandoff(long orderId) {
        return OrderHandoff.from(getStaffOrder(orderId));
    }

    /**
     * TGMS-56 Production synchronization contract. Production must call this service instead
     * of updating orders.status directly. The operation is idempotent for an order that is
     * already IN_PRODUCTION so multiple production tasks for one order remain coherent.
     */
    @Transactional
    public OrderHandoff synchronizeProductionStarted(long changedByUserId, long orderId) {
        return synchronizeProductionStatus(
                changedByUserId, orderId, OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION);
    }

    /**
     * TGMS-56 Production synchronization contract used only after Production confirms that all
     * production tasks for an order are complete. It is idempotent once READY_FOR_DELIVERY.
     */
    @Transactional
    public OrderHandoff synchronizeProductionCompleted(long changedByUserId, long orderId) {
        return synchronizeProductionStatus(
                changedByUserId, orderId, OrderStatus.IN_PRODUCTION, OrderStatus.READY_FOR_DELIVERY);
    }

    /**
     * TGMS-64 Delivery synchronization contract. Delivery must call this after a Delivery record
     * reaches DELIVERED; no Delivery code may update orders.status directly.
     */
    @Transactional
    public OrderHandoff synchronizeDeliveryCompleted(long changedByUserId, long orderId) {
        return synchronizeProductionStatus(
                changedByUserId, orderId, OrderStatus.READY_FOR_DELIVERY, OrderStatus.COMPLETED);
    }

    private OrderHandoff synchronizeProductionStatus(
            long changedByUserId,
            long orderId,
            OrderStatus expectedCurrent,
            OrderStatus target) {
        long validatedOrderId = requireOrderId(orderId);
        OrderDetail current = orderRepository.findOrderDetail(validatedOrderId, null)
                .orElseThrow(OrderNotFoundException::new);
        if (current.status() == target) {
            return OrderHandoff.from(current);
        }
        if (current.status() != expectedCurrent) {
            throw new OrderStatusTransitionException(current.status(), target);
        }
        boolean updated = orderRepository.updateStatus(validatedOrderId, expectedCurrent, target);
        if (!updated) {
            OrderDetail latest = orderRepository.findOrderDetail(validatedOrderId, null)
                    .orElseThrow(OrderNotFoundException::new);
            if (latest.status() == target) {
                return OrderHandoff.from(latest);
            }
            throw new OrderStatusTransitionException(latest.status(), target);
        }
        orderRepository.recordStatusTransition(validatedOrderId, expectedCurrent, target, changedByUserId);
        return OrderHandoff.from(orderRepository.findOrderDetail(validatedOrderId, null)
                .orElseThrow(OrderNotFoundException::new));
    }

    @Transactional
    public OrderDetail updateStatus(long changedByUserId, long orderId, String requestedStatus) {
        long validatedOrderId = requireOrderId(orderId);
        OrderStatus requested = parseStatusUpdate(requestedStatus);
        OrderDetail current = orderRepository.findOrderDetail(validatedOrderId, null)
                .orElseThrow(OrderNotFoundException::new);

        if (!OrderStatusLifecycle.canStaffTransition(current.status(), requested)) {
            throw OrderStatusTransitionException.forStaff(current.status(), requested);
        }
        if (requested == OrderStatus.CANCELLED) {
            orderRepository.findPaymentByOrderId(validatedOrderId)
                    .filter(payment -> payment.amountPaid().compareTo(BigDecimal.ZERO) > 0)
                    .ifPresent(payment -> { throw new OrderCancellationException(); });
        }

        boolean updated = orderRepository.updateStatus(
                validatedOrderId, current.status(), requested);
        if (!updated) {
            OrderDetail latest = orderRepository.findOrderDetail(validatedOrderId, null)
                    .orElseThrow(OrderNotFoundException::new);
            throw new OrderStatusTransitionException(latest.status(), requested);
        }

        orderRepository.recordStatusTransition(
                validatedOrderId, current.status(), requested, changedByUserId);
        OrderDetail updatedOrder = orderRepository.findOrderDetail(validatedOrderId, null)
                .orElseThrow(OrderNotFoundException::new);
        notificationDispatchService.orderStatus(
                updatedOrder.customerId(),
                updatedOrder.id(),
                updatedOrder.orderNumber(),
                updatedOrder.status());
        return updatedOrder;
    }

    @Transactional
    public CreatedCustomerOrder createOrder(
            Long customerId, List<CreateOrderItemCommand> requestedItems) {
        return createValidatedOrder(customerId, requestedItems);
    }

    @Transactional
    public CreatedCustomerOrder createCustomerOrder(
            long authenticatedCustomerId, List<CreateOrderItemCommand> requestedItems) {
        return createValidatedOrder(authenticatedCustomerId, requestedItems);
    }

    private CreatedCustomerOrder createValidatedOrder(
            Long customerId, List<CreateOrderItemCommand> requestedItems) {
        // TGMS-44: all order-creation paths cross the same validation boundary before
        // the first INSERT, preserving atomicity and identical business rules.
        ValidatedOrder validated = orderValidator.validate(customerId, requestedItems);
        UserAccount customer = validated.customer();

        CustomerOrder order = orderRepository.createOrder(
                customer.id(), generateOrderNumber(), OrderStatus.PENDING);
        List<CreatedOrderItem> savedItems = new ArrayList<>();
        for (ValidatedOrderItem validatedItem : validated.items()) {
            CreateOrderItemCommand requested = validatedItem.requested();
            OrderProductSelection selection = validatedItem.selection();
            CustomerOrderItem savedItem = orderRepository.createOrderItem(
                    order.id(),
                    selection.productId(),
                    selection.variantId(),
                    requested.quantity(),
                    selection.size(),
                    selection.color(),
                    selection.currentPrice());
            savedItems.add(new CreatedOrderItem(savedItem, selection.productName()));
        }

        return new CreatedCustomerOrder(order, toCustomerOption(customer), savedItems);
    }

    private java.util.Optional<OrderBilling> billingFor(long orderId) {
        return orderRepository.findInvoiceByOrderId(orderId).map(invoice -> {
            OrderPaymentRecord payment = orderRepository.findPaymentByOrderId(orderId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Invoice exists without its required payment record."));
            return new OrderBilling(invoice, payment);
        });
    }

    private PaymentUpdate validatePaymentUpdate(
            OrderInvoice invoice,
            OrderPaymentRecord currentPayment,
            String requestedStatus,
            String requestedAmountPaid,
            String requestedMethod,
            String reference,
            String note) {
        Map<String, String> fields = new LinkedHashMap<>();
        OrderPaymentStatus status = parsePaymentStatus(requestedStatus, fields);
        BigDecimal amount = parseAmount(requestedAmountPaid, fields);
        OrderPaymentMethod method = parsePaymentMethod(requestedMethod, fields);
        String normalizedReference = normalizeOptional(reference);
        String normalizedNote = normalizeOptional(note);
        if (normalizedReference != null && normalizedReference.length() > 120) {
            fields.put("paymentReference", "Payment reference must not exceed 120 characters.");
        }
        if (normalizedNote != null && normalizedNote.length() > 500) {
            fields.put("note", "Payment note must not exceed 500 characters.");
        }

        if (status != null && !paymentTransitionAllowed(currentPayment.paymentStatus(), status)) {
            fields.put(
                    "paymentStatus",
                    "Payment status cannot move backward because refund/reversal handling is not enabled.");
        }

        if (status != null && amount != null) {
            int comparedToTotal = amount.compareTo(invoice.totalAmount());
            if (status == OrderPaymentStatus.UNPAID && amount.compareTo(BigDecimal.ZERO) != 0) {
                fields.put("amountPaid", "Unpaid records must have an amount paid of 0.00.");
            } else if (status == OrderPaymentStatus.PARTIALLY_PAID
                    && (amount.compareTo(BigDecimal.ZERO) <= 0 || comparedToTotal >= 0)) {
                fields.put("amountPaid", "Partially paid amount must be greater than 0 and less than the invoice total.");
            } else if (status == OrderPaymentStatus.PAID && comparedToTotal != 0) {
                fields.put("amountPaid", "Paid amount must exactly match the invoice total.");
            }
            if (amount.compareTo(invoice.totalAmount()) > 0) {
                fields.put("amountPaid", "Amount paid cannot exceed the invoice total.");
            }
            if (currentPayment.paymentStatus() == OrderPaymentStatus.PARTIALLY_PAID
                    && status == OrderPaymentStatus.PARTIALLY_PAID
                    && amount.compareTo(currentPayment.amountPaid()) < 0) {
                fields.put(
                        "amountPaid",
                        "Amount paid cannot be reduced because refund/reversal handling is not enabled.");
            }
            if (status != OrderPaymentStatus.UNPAID && method == null && !fields.containsKey("paymentMethod")) {
                fields.put("paymentMethod", "Select how the payment was recorded.");
            }
            if (status == OrderPaymentStatus.UNPAID && method != null) {
                fields.put("paymentMethod", "Unpaid records cannot have a payment method.");
            }
        }
        if (!fields.isEmpty()) {
            throw new OrderBillingValidationException(fields);
        }
        return new PaymentUpdate(status, amount.setScale(2), method, normalizedReference, normalizedNote);
    }

    private boolean paymentTransitionAllowed(
            OrderPaymentStatus current, OrderPaymentStatus requested) {
        if (current == requested) {
            return true;
        }
        return switch (current) {
            case UNPAID -> requested == OrderPaymentStatus.PARTIALLY_PAID
                    || requested == OrderPaymentStatus.PAID;
            case PARTIALLY_PAID -> requested == OrderPaymentStatus.PAID;
            case PAID -> false;
        };
    }

    private OrderPaymentStatus parsePaymentStatus(String value, Map<String, String> fields) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            fields.put("paymentStatus", "Select a payment status.");
            return null;
        }
        try {
            return OrderPaymentStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            fields.put("paymentStatus", "Payment status must be UNPAID, PARTIALLY_PAID, or PAID.");
            return null;
        }
    }

    private BigDecimal parseAmount(String value, Map<String, String> fields) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            fields.put("amountPaid", "Enter the recorded amount paid.");
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(normalized);
            if (amount.scale() > 2 || amount.compareTo(BigDecimal.ZERO) < 0) {
                fields.put("amountPaid", "Amount paid must be a non-negative value with up to 2 decimal places.");
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            fields.put("amountPaid", "Enter a valid payment amount.");
            return null;
        }
    }

    private OrderPaymentMethod parsePaymentMethod(String value, Map<String, String> fields) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return OrderPaymentMethod.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            fields.put("paymentMethod", "Payment method must be CASH, BANK_TRANSFER, or OTHER.");
            return null;
        }
    }

    private OrderQuery readQuery(String search, String status) {
        String normalizedSearch = normalizeOptional(search);
        if (normalizedSearch != null && normalizedSearch.length() > MAX_ORDER_SEARCH_LENGTH) {
            throw new OrderReadValidationException(
                    Map.of("search", "Order search must not exceed 120 characters."));
        }

        OrderStatus parsedStatus = null;
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null) {
            try {
                parsedStatus = OrderStatus.valueOf(normalizedStatus.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new OrderReadValidationException(Map.of(
                        "status",
                        "Status must be PENDING, CONFIRMED, IN_PRODUCTION, READY_FOR_DELIVERY, COMPLETED, or CANCELLED."));
            }
        }
        return new OrderQuery(normalizedSearch, parsedStatus);
    }

    private OrderStatus parseStatusUpdate(String status) {
        String normalized = normalizeOptional(status);
        if (normalized == null) {
            throw new OrderStatusValidationException(
                    Map.of("status", "Select the next order status."));
        }
        try {
            return OrderStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new OrderStatusValidationException(Map.of(
                    "status",
                    "Status must be PENDING, CONFIRMED, IN_PRODUCTION, READY_FOR_DELIVERY, COMPLETED, or CANCELLED."));
        }
    }

    private long requireOrderId(long orderId) {
        if (orderId <= 0) {
            throw new OrderNotFoundException();
        }
        return orderId;
    }

    private long requireCustomerId(long customerId) {
        if (customerId <= 0) {
            throw new OrderNotFoundException();
        }
        return customerId;
    }

    private OrderCustomerOption toCustomerOption(UserAccount account) {
        return new OrderCustomerOption(account.id(), account.fullName(), account.email());
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private String generateInvoiceNumber() {
        return "INV-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }

    private record PaymentUpdate(
            OrderPaymentStatus status,
            BigDecimal amountPaid,
            OrderPaymentMethod method,
            String reference,
            String note) {}

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }
}

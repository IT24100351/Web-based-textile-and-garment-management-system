package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    CustomerOrder createOrder(long customerId, String orderNumber, OrderStatus status);

    CustomerOrderItem createOrderItem(
            long orderId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot);

    List<OrderSummary> findOrders(OrderQuery query, Long customerId);

    Optional<OrderDetail> findOrderDetail(long orderId, Long customerId);

    Optional<OrderInvoice> findInvoiceByOrderId(long orderId);

    Optional<OrderPaymentRecord> findPaymentByOrderId(long orderId);

    OrderInvoice createInvoice(
            long orderId, String invoiceNumber, BigDecimal totalAmount, long issuedByUserId);

    OrderPaymentRecord createInitialPaymentRecord(long orderId, long invoiceId, long recordedByUserId);

    OrderPaymentRecord updatePaymentRecord(
            long orderId,
            OrderPaymentStatus status,
            BigDecimal amountPaid,
            OrderPaymentMethod method,
            String reference,
            String note,
            long recordedByUserId);

    boolean updateStatus(long orderId, OrderStatus expectedStatus, OrderStatus newStatus);

    void recordStatusTransition(
            long orderId, OrderStatus fromStatus, OrderStatus toStatus, long changedByUserId);
}

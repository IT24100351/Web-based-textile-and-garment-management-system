package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CustomerOrder createOrder(long customerId, String orderNumber, OrderStatus status) {
        long id = insertAndReturnId(
                """
                INSERT INTO orders (customer_id, order_number, status)
                VALUES (?, ?, ?)
                """,
                statement -> {
                    statement.setLong(1, customerId);
                    statement.setString(2, orderNumber);
                    statement.setString(3, status.name());
                });

        return jdbcTemplate.queryForObject(
                """
                SELECT id, customer_id, order_number, status, created_at, updated_at
                FROM orders
                WHERE id = ?
                """,
                (resultSet, rowNumber) -> new CustomerOrder(
                        resultSet.getLong("id"),
                        resultSet.getLong("customer_id"),
                        resultSet.getString("order_number"),
                        OrderStatus.valueOf(resultSet.getString("status")),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                id);
    }

    @Override
    public CustomerOrderItem createOrderItem(
            long orderId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot) {
        long id = insertAndReturnId(
                """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    variant_id,
                    quantity,
                    selected_size,
                    selected_color,
                    unit_price_snapshot
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setLong(1, orderId);
                    statement.setLong(2, productId);
                    statement.setLong(3, variantId);
                    statement.setInt(4, quantity);
                    statement.setString(5, selectedSize);
                    statement.setString(6, selectedColor);
                    statement.setBigDecimal(7, unitPriceSnapshot);
                });

        return jdbcTemplate.queryForObject(
                """
                SELECT id, order_id, product_id, variant_id, quantity,
                       selected_size, selected_color, unit_price_snapshot,
                       created_at, updated_at
                FROM order_items
                WHERE id = ?
                """,
                (resultSet, rowNumber) -> new CustomerOrderItem(
                        resultSet.getLong("id"),
                        resultSet.getLong("order_id"),
                        resultSet.getLong("product_id"),
                        resultSet.getLong("variant_id"),
                        resultSet.getInt("quantity"),
                        resultSet.getString("selected_size"),
                        resultSet.getString("selected_color"),
                        resultSet.getBigDecimal("unit_price_snapshot"),
                        timestamp(resultSet.getTimestamp("created_at")),
                        timestamp(resultSet.getTimestamp("updated_at"))),
                id);
    }

    @Override
    public List<OrderSummary> findOrders(OrderQuery query, Long customerId) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.order_number, o.customer_id,
                       u.full_name AS customer_name, u.email AS customer_email,
                       o.status, o.created_at, o.updated_at,
                       COUNT(oi.id) AS item_count,
                       COALESCE(SUM(oi.unit_price_snapshot * oi.quantity), 0.00) AS total_amount
                FROM orders o
                JOIN users u ON u.id = o.customer_id
                LEFT JOIN order_items oi ON oi.order_id = o.id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();

        if (customerId != null) {
            sql.append(" AND o.customer_id = ?\n");
            parameters.add(customerId);
        }
        if (query.search() != null) {
            sql.append("""
                     AND (
                         LOWER(o.order_number) LIKE ?
                         OR LOWER(u.full_name) LIKE ?
                         OR LOWER(u.email) LIKE ?
                     )
                    """);
            String searchPattern = "%" + query.search().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }
        if (query.status() != null) {
            sql.append(" AND o.status = ?\n");
            parameters.add(query.status().name());
        }

        sql.append("""
                GROUP BY o.id, o.order_number, o.customer_id,
                         u.full_name, u.email, o.status, o.created_at, o.updated_at
                ORDER BY o.created_at DESC, o.id DESC
                """);

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) -> new OrderSummary(
                        resultSet.getLong("id"),
                        resultSet.getString("order_number"),
                        resultSet.getLong("customer_id"),
                        resultSet.getString("customer_name"),
                        resultSet.getString("customer_email"),
                        OrderStatus.valueOf(resultSet.getString("status")),
                        timestamp(resultSet.getTimestamp("created_at")),
                        timestamp(resultSet.getTimestamp("updated_at")),
                        Math.toIntExact(resultSet.getLong("item_count")),
                        money(resultSet.getBigDecimal("total_amount"))),
                parameters.toArray());
    }

    @Override
    public Optional<OrderDetail> findOrderDetail(long orderId, Long customerId) {
        StringBuilder sql = new StringBuilder("""
                SELECT o.id, o.order_number, o.customer_id,
                       u.full_name AS customer_name, u.email AS customer_email,
                       o.status, o.created_at, o.updated_at
                FROM orders o
                JOIN users u ON u.id = o.customer_id
                WHERE o.id = ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(orderId);
        if (customerId != null) {
            sql.append(" AND o.customer_id = ?\n");
            parameters.add(customerId);
        }

        List<OrderHeaderRow> headers = jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) -> new OrderHeaderRow(
                        resultSet.getLong("id"),
                        resultSet.getString("order_number"),
                        resultSet.getLong("customer_id"),
                        resultSet.getString("customer_name"),
                        resultSet.getString("customer_email"),
                        OrderStatus.valueOf(resultSet.getString("status")),
                        timestamp(resultSet.getTimestamp("created_at")),
                        timestamp(resultSet.getTimestamp("updated_at"))),
                parameters.toArray());
        if (headers.isEmpty()) {
            return Optional.empty();
        }

        OrderHeaderRow header = headers.get(0);
        List<OrderDetailItem> items = jdbcTemplate.query(
                """
                SELECT oi.id, oi.product_id, oi.variant_id,
                       p.name AS product_name,
                       oi.quantity, oi.selected_size, oi.selected_color,
                       oi.unit_price_snapshot,
                       (oi.unit_price_snapshot * oi.quantity) AS line_total
                FROM order_items oi
                JOIN garment_products p ON p.id = oi.product_id
                WHERE oi.order_id = ?
                ORDER BY oi.id ASC
                """,
                (resultSet, rowNumber) -> new OrderDetailItem(
                        resultSet.getLong("id"),
                        resultSet.getLong("product_id"),
                        resultSet.getLong("variant_id"),
                        resultSet.getString("product_name"),
                        resultSet.getInt("quantity"),
                        resultSet.getString("selected_size"),
                        resultSet.getString("selected_color"),
                        money(resultSet.getBigDecimal("unit_price_snapshot")),
                        money(resultSet.getBigDecimal("line_total"))),
                orderId);

        BigDecimal total = money(items.stream()
                .map(OrderDetailItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<OrderStatusHistoryEntry> statusHistory = jdbcTemplate.query(
                """
                SELECT id, from_status, to_status, changed_by_user_id, changed_at
                FROM order_status_history
                WHERE order_id = ?
                ORDER BY changed_at ASC, id ASC
                """,
                (resultSet, rowNumber) -> new OrderStatusHistoryEntry(
                        resultSet.getLong("id"),
                        OrderStatus.valueOf(resultSet.getString("from_status")),
                        OrderStatus.valueOf(resultSet.getString("to_status")),
                        resultSet.getLong("changed_by_user_id"),
                        timestamp(resultSet.getTimestamp("changed_at"))),
                orderId);
        return Optional.of(new OrderDetail(
                header.id(),
                header.orderNumber(),
                header.customerId(),
                header.customerName(),
                header.customerEmail(),
                header.status(),
                header.createdAt(),
                header.updatedAt(),
                List.copyOf(items),
                total,
                List.copyOf(statusHistory)));
    }


    @Override
    public Optional<OrderInvoice> findInvoiceByOrderId(long orderId) {
        List<OrderInvoice> rows = jdbcTemplate.query(
                """
                SELECT id, order_id, invoice_number, total_amount, issued_by_user_id, issued_at
                FROM order_invoices
                WHERE order_id = ?
                """,
                (resultSet, rowNumber) -> new OrderInvoice(
                        resultSet.getLong("id"),
                        resultSet.getLong("order_id"),
                        resultSet.getString("invoice_number"),
                        money(resultSet.getBigDecimal("total_amount")),
                        resultSet.getLong("issued_by_user_id"),
                        timestamp(resultSet.getTimestamp("issued_at"))),
                orderId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<OrderPaymentRecord> findPaymentByOrderId(long orderId) {
        List<OrderPaymentRecord> rows = jdbcTemplate.query(
                """
                SELECT id, order_id, invoice_id, payment_status, amount_paid,
                       payment_method, payment_reference, note,
                       recorded_by_user_id, recorded_at, updated_at
                FROM order_payment_records
                WHERE order_id = ?
                """,
                (resultSet, rowNumber) -> paymentRecord(resultSet),
                orderId);
        return rows.stream().findFirst();
    }

    @Override
    public OrderInvoice createInvoice(
            long orderId, String invoiceNumber, BigDecimal totalAmount, long issuedByUserId) {
        long id = insertAndReturnId(
                """
                INSERT INTO order_invoices (order_id, invoice_number, total_amount, issued_by_user_id)
                VALUES (?, ?, ?, ?)
                """,
                statement -> {
                    statement.setLong(1, orderId);
                    statement.setString(2, invoiceNumber);
                    statement.setBigDecimal(3, totalAmount);
                    statement.setLong(4, issuedByUserId);
                });
        return findInvoiceByOrderId(orderId).orElseThrow();
    }

    @Override
    public OrderPaymentRecord createInitialPaymentRecord(
            long orderId, long invoiceId, long recordedByUserId) {
        insertAndReturnId(
                """
                INSERT INTO order_payment_records (
                    order_id, invoice_id, payment_status, amount_paid, recorded_by_user_id
                ) VALUES (?, ?, 'UNPAID', 0.00, ?)
                """,
                statement -> {
                    statement.setLong(1, orderId);
                    statement.setLong(2, invoiceId);
                    statement.setLong(3, recordedByUserId);
                });
        return findPaymentByOrderId(orderId).orElseThrow();
    }

    @Override
    public OrderPaymentRecord updatePaymentRecord(
            long orderId,
            OrderPaymentStatus status,
            BigDecimal amountPaid,
            OrderPaymentMethod method,
            String reference,
            String note,
            long recordedByUserId) {
        int changed = jdbcTemplate.update(
                """
                UPDATE order_payment_records
                SET payment_status = ?, amount_paid = ?, payment_method = ?,
                    payment_reference = ?, note = ?, recorded_by_user_id = ?,
                    recorded_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)
                WHERE order_id = ?
                """,
                status.name(),
                amountPaid,
                method == null ? null : method.name(),
                reference,
                note,
                recordedByUserId,
                orderId);
        if (changed != 1) {
            throw new IllegalStateException("Payment record is missing for this order.");
        }
        return findPaymentByOrderId(orderId).orElseThrow();
    }

    private OrderPaymentRecord paymentRecord(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        String method = resultSet.getString("payment_method");
        return new OrderPaymentRecord(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getLong("invoice_id"),
                OrderPaymentStatus.valueOf(resultSet.getString("payment_status")),
                money(resultSet.getBigDecimal("amount_paid")),
                method == null ? null : OrderPaymentMethod.valueOf(method),
                resultSet.getString("payment_reference"),
                resultSet.getString("note"),
                resultSet.getLong("recorded_by_user_id"),
                timestamp(resultSet.getTimestamp("recorded_at")),
                timestamp(resultSet.getTimestamp("updated_at")));
    }

    @Override
    public boolean updateStatus(long orderId, OrderStatus expectedStatus, OrderStatus newStatus) {
        int changed = jdbcTemplate.update(
                """
                UPDATE orders
                SET status = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = ?
                """,
                newStatus.name(),
                orderId,
                expectedStatus.name());
        return changed == 1;
    }

    @Override
    public void recordStatusTransition(
            long orderId, OrderStatus fromStatus, OrderStatus toStatus, long changedByUserId) {
        jdbcTemplate.update(
                """
                INSERT INTO order_status_history (
                    order_id, from_status, to_status, changed_by_user_id
                ) VALUES (?, ?, ?, ?)
                """,
                orderId,
                fromStatus.name(),
                toStatus.name(),
                changedByUserId);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2);
    }

    private java.time.Instant timestamp(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private long insertAndReturnId(String sql, StatementBinder binder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(sql, new String[] {"id"});
                    binder.bind(statement);
                    return statement;
                },
                keyHolder);
        return generatedId(keyHolder);
    }

    static long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        throw new IllegalStateException("Database did not return an order identifier.");
    }

    private record OrderHeaderRow(
            long id,
            String orderNumber,
            long customerId,
            String customerName,
            String customerEmail,
            OrderStatus status,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {}

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws java.sql.SQLException;
    }
}

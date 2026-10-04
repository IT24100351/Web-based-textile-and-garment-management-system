package lk.ac.sliit.tgms.quotation;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQuotationRepository implements QuotationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcQuotationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Quotation createQuotation(long customerId, long issuedByUserId, String quotationNumber) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO quotations (quotation_number, customer_id, issued_by_user_id) VALUES (?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, quotationNumber);
            statement.setLong(2, customerId);
            statement.setLong(3, issuedByUserId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Quotation insert did not return a generated ID.");
        }
        return jdbcTemplate.queryForObject(
                "SELECT id, quotation_number, customer_id, issued_by_user_id, issued_at FROM quotations WHERE id = ?",
                (rs, rowNum) -> new Quotation(
                        rs.getLong("id"),
                        rs.getString("quotation_number"),
                        rs.getLong("customer_id"),
                        rs.getLong("issued_by_user_id"),
                        timestamp(rs.getTimestamp("issued_at"))),
                key.longValue());
    }

    @Override
    public QuotationItem createItem(
            long quotationId,
            long productId,
            long variantId,
            String productNameSnapshot,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO quotation_items (
                        quotation_id, product_id, variant_id, product_name_snapshot,
                        quantity, selected_size, selected_color, unit_price_snapshot
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, quotationId);
            statement.setLong(2, productId);
            statement.setLong(3, variantId);
            statement.setString(4, productNameSnapshot);
            statement.setInt(5, quantity);
            statement.setString(6, selectedSize);
            statement.setString(7, selectedColor);
            statement.setBigDecimal(8, unitPriceSnapshot);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Quotation item insert did not return a generated ID.");
        }
        return jdbcTemplate.queryForObject(
                """
                SELECT id, quotation_id, product_id, variant_id, product_name_snapshot,
                       quantity, selected_size, selected_color, unit_price_snapshot, created_at
                FROM quotation_items WHERE id = ?
                """,
                (rs, rowNum) -> mapItem(rs),
                key.longValue());
    }

    @Override
    public List<QuotationSummary> findAll() {
        return jdbcTemplate.query(
                """
                SELECT q.id, q.quotation_number, q.customer_id,
                       u.full_name AS customer_name, u.email AS customer_email,
                       q.issued_at,
                       COUNT(qi.id) AS item_count,
                       COALESCE(SUM(qi.unit_price_snapshot * qi.quantity), 0.00) AS total_amount
                FROM quotations q
                JOIN users u ON u.id = q.customer_id
                LEFT JOIN quotation_items qi ON qi.quotation_id = q.id
                GROUP BY q.id, q.quotation_number, q.customer_id,
                         u.full_name, u.email, q.issued_at
                ORDER BY q.issued_at DESC, q.id DESC
                """,
                (rs, rowNum) -> new QuotationSummary(
                        rs.getLong("id"),
                        rs.getString("quotation_number"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        timestamp(rs.getTimestamp("issued_at")),
                        Math.toIntExact(rs.getLong("item_count")),
                        money(rs.getBigDecimal("total_amount"))));
    }

    @Override
    public Optional<QuotationDetail> findById(long quotationId) {
        List<QuotationHeader> headers = jdbcTemplate.query(
                """
                SELECT q.id, q.quotation_number, q.customer_id,
                       u.full_name AS customer_name, u.email AS customer_email,
                       q.issued_by_user_id, q.issued_at
                FROM quotations q
                JOIN users u ON u.id = q.customer_id
                WHERE q.id = ?
                """,
                (rs, rowNum) -> new QuotationHeader(
                        rs.getLong("id"),
                        rs.getString("quotation_number"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        rs.getLong("issued_by_user_id"),
                        timestamp(rs.getTimestamp("issued_at"))),
                quotationId);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        QuotationHeader header = headers.get(0);
        List<QuotationItem> items = jdbcTemplate.query(
                """
                SELECT id, quotation_id, product_id, variant_id, product_name_snapshot,
                       quantity, selected_size, selected_color, unit_price_snapshot, created_at
                FROM quotation_items
                WHERE quotation_id = ?
                ORDER BY id ASC
                """,
                (rs, rowNum) -> mapItem(rs),
                quotationId);
        BigDecimal total = money(items.stream()
                .map(QuotationItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return Optional.of(new QuotationDetail(
                header.id(), header.quotationNumber(), header.customerId(),
                header.customerName(), header.customerEmail(), header.issuedByUserId(),
                header.issuedAt(), List.copyOf(items), total));
    }

    private QuotationItem mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QuotationItem(
                rs.getLong("id"),
                rs.getLong("quotation_id"),
                rs.getLong("product_id"),
                rs.getLong("variant_id"),
                rs.getString("product_name_snapshot"),
                rs.getInt("quantity"),
                rs.getString("selected_size"),
                rs.getString("selected_color"),
                money(rs.getBigDecimal("unit_price_snapshot")),
                timestamp(rs.getTimestamp("created_at")));
    }

    private static java.time.Instant timestamp(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private record QuotationHeader(
            long id,
            String quotationNumber,
            long customerId,
            String customerName,
            String customerEmail,
            long issuedByUserId,
            java.time.Instant issuedAt) {}
}

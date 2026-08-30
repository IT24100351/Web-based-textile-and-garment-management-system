package lk.ac.sliit.tgms.delivery;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeliveryRepository implements DeliveryRepository {

    private static final String DELIVERY_COLUMNS = """
            id, delivery_number, order_id, scheduled_at, delivery_address,
            delivery_notes, status, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsActiveByOrderId(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM deliveries WHERE active_order_lock_id = ?",
                Integer.class,
                orderId);
        return count != null && count > 0;
    }

    @Override
    public Optional<DeliveryRecord> findById(long deliveryId) {
        List<DeliveryRecord> deliveries = jdbcTemplate.query(
                "SELECT " + DELIVERY_COLUMNS + " FROM deliveries WHERE id = ?",
                (resultSet, rowNumber) -> mapDelivery(resultSet),
                deliveryId);
        return deliveries.stream().findFirst();
    }

    @Override
    public Optional<DeliveryRecord> findByOrderId(long orderId) {
        List<DeliveryRecord> deliveries = jdbcTemplate.query(
                """
                SELECT %s
                FROM deliveries
                WHERE order_id = ?
                ORDER BY CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END,
                         updated_at DESC, id DESC
                LIMIT 1
                """.formatted(DELIVERY_COLUMNS),
                (resultSet, rowNumber) -> mapDelivery(resultSet),
                orderId);
        return deliveries.stream().findFirst();
    }

    @Override
    public List<DeliveryRecord> findRecords(String search, DeliveryStatus status) {
        StringBuilder sql = new StringBuilder("SELECT " + DELIVERY_COLUMNS + " FROM deliveries WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if (search != null) {
            sql.append(" AND (LOWER(delivery_number) LIKE ? OR LOWER(delivery_address) LIKE ? OR CAST(order_id AS CHAR) LIKE ?)");
            String pattern = "%" + search.toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add("%" + search + "%");
        }
        if (status != null) {
            sql.append(" AND status = ?");
            parameters.add(status.name());
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) -> mapDelivery(resultSet),
                parameters.toArray());
    }

    @Override
    public Optional<DeliveryScheduleConflict> findActiveScheduleConflict(
            Instant windowStartExclusive,
            Instant windowEndExclusive) {
        List<DeliveryScheduleConflict> conflicts = jdbcTemplate.query(
                """
                SELECT delivery_number, scheduled_at
                FROM deliveries
                WHERE status IN ('SCHEDULED', 'OUT_FOR_DELIVERY')
                  AND scheduled_at > ?
                  AND scheduled_at < ?
                ORDER BY scheduled_at, id
                LIMIT 1
                """,
                (resultSet, rowNumber) -> new DeliveryScheduleConflict(
                        resultSet.getString("delivery_number"),
                        resultSet.getTimestamp("scheduled_at").toInstant()),
                Timestamp.from(windowStartExclusive),
                Timestamp.from(windowEndExclusive));
        return conflicts.stream().findFirst();
    }

    @Override
    public boolean updateStatus(long deliveryId, DeliveryStatus expectedCurrent, DeliveryStatus requested) {
        return jdbcTemplate.update(
                """
                UPDATE deliveries
                SET status = ?,
                    active_order_lock_id = CASE WHEN ? = 'CANCELLED' THEN NULL ELSE active_order_lock_id END,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                  AND status = ?
                """,
                requested.name(),
                requested.name(),
                deliveryId,
                expectedCurrent.name()) == 1;
    }

    @Override
    public DeliveryRecord createDelivery(
            long orderId,
            String deliveryNumber,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO deliveries (
                        delivery_number, order_id, active_order_lock_id,
                        scheduled_at, delivery_address, delivery_notes
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setString(1, deliveryNumber);
            statement.setLong(2, orderId);
            statement.setLong(3, orderId);
            statement.setTimestamp(4, Timestamp.from(scheduledAt));
            statement.setString(5, deliveryAddress);
            statement.setString(6, deliveryNotes);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Delivery insert did not return a generated ID.");
        }

        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Created Delivery could not be reloaded."));
    }

    private DeliveryRecord mapDelivery(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new DeliveryRecord(
                resultSet.getLong("id"),
                resultSet.getString("delivery_number"),
                resultSet.getLong("order_id"),
                resultSet.getTimestamp("scheduled_at").toInstant(),
                resultSet.getString("delivery_address"),
                resultSet.getString("delivery_notes"),
                DeliveryStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}

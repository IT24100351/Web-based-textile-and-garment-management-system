package lk.ac.sliit.tgms.report;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportRepository implements ReportRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReportSection orders(Instant fromInclusive, Instant toExclusive, Long customerId) {
        String ownership = customerId == null ? "" : " AND customer_id = ?";
        String sql = """
                SELECT COUNT(*) AS total_created,
                       SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed_count,
                       SUM(CASE WHEN status = 'IN_PRODUCTION' THEN 1 ELSE 0 END) AS in_production_count,
                       SUM(CASE WHEN status = 'READY_FOR_DELIVERY' THEN 1 ELSE 0 END) AS ready_count,
                       SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count,
                       SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
                FROM orders
                WHERE created_at >= ? AND created_at < ?
                """ + ownership;
        Object[] parameters = customerId == null
                ? new Object[] {Timestamp.from(fromInclusive), Timestamp.from(toExclusive)}
                : new Object[] {Timestamp.from(fromInclusive), Timestamp.from(toExclusive), customerId};
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new ReportSection(
                "orders",
                customerId == null ? "Orders" : "Your orders",
                "Orders created in the selected period, grouped by their current stored status.",
                List.of(
                        metric("totalCreated", "Created in period", resultSet.getLong("total_created")),
                        metric("pending", "Pending", resultSet.getLong("pending_count")),
                        metric("confirmed", "Confirmed", resultSet.getLong("confirmed_count")),
                        metric("inProduction", "In production", resultSet.getLong("in_production_count")),
                        metric("readyForDelivery", "Ready for delivery", resultSet.getLong("ready_count")),
                        metric("completed", "Completed", resultSet.getLong("completed_count")),
                        metric("cancelled", "Cancelled", resultSet.getLong("cancelled_count")))),
                parameters);
    }

    @Override
    public ReportSection inventorySnapshot() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS total_materials,
                       SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_count,
                       SUM(CASE WHEN status = 'ACTIVE' AND current_quantity <= low_stock_threshold
                                THEN 1 ELSE 0 END) AS low_stock_count,
                       SUM(CASE WHEN status = 'INACTIVE' THEN 1 ELSE 0 END) AS inactive_count
                FROM inventory_materials
                """,
                (resultSet, rowNumber) -> new ReportSection(
                        "inventory",
                        "Inventory",
                        "Current inventory snapshot. Low stock is current_quantity <= low_stock_threshold and is not date-filtered.",
                        List.of(
                                metric("totalMaterials", "Materials", resultSet.getLong("total_materials")),
                                metric("activeMaterials", "Active", resultSet.getLong("active_count")),
                                metric("lowStock", "Low stock", resultSet.getLong("low_stock_count")),
                                metric("inactiveMaterials", "Inactive", resultSet.getLong("inactive_count")))));
    }

    @Override
    public ReportSection production(Instant fromInclusive, Instant toExclusive) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS total_created,
                       SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                       SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count,
                       SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count
                FROM production_tasks
                WHERE created_at >= ? AND created_at < ?
                """,
                (resultSet, rowNumber) -> new ReportSection(
                        "production",
                        "Production",
                        "Production tasks created in the selected period, grouped by their current stored status.",
                        List.of(
                                metric("totalCreated", "Tasks created", resultSet.getLong("total_created")),
                                metric("pending", "Pending", resultSet.getLong("pending_count")),
                                metric("inProgress", "In progress", resultSet.getLong("in_progress_count")),
                                metric("completed", "Completed", resultSet.getLong("completed_count")))),
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive));
    }

    @Override
    public ReportSection deliveries(Instant fromInclusive, Instant toExclusive) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) AS total_created,
                       SUM(CASE WHEN status = 'SCHEDULED' THEN 1 ELSE 0 END) AS scheduled_count,
                       SUM(CASE WHEN status = 'OUT_FOR_DELIVERY' THEN 1 ELSE 0 END) AS out_count,
                       SUM(CASE WHEN status = 'DELIVERED' THEN 1 ELSE 0 END) AS delivered_count,
                       SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count
                FROM deliveries
                WHERE created_at >= ? AND created_at < ?
                """,
                (resultSet, rowNumber) -> new ReportSection(
                        "delivery",
                        "Delivery",
                        "Delivery records created in the selected period, grouped by their current stored status.",
                        List.of(
                                metric("totalCreated", "Deliveries created", resultSet.getLong("total_created")),
                                metric("scheduled", "Scheduled", resultSet.getLong("scheduled_count")),
                                metric("outForDelivery", "Out for delivery", resultSet.getLong("out_count")),
                                metric("delivered", "Delivered", resultSet.getLong("delivered_count")),
                                metric("cancelled", "Cancelled", resultSet.getLong("cancelled_count")))),
                Timestamp.from(fromInclusive),
                Timestamp.from(toExclusive));
    }

    private ReportMetric metric(String key, String label, long value) {
        return new ReportMetric(key, label, value);
    }
}

package lk.ac.sliit.tgms.production;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductionTaskRepository implements ProductionTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductionTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ProductionTask createTask(long orderId, String taskNumber) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO production_tasks (task_number, order_id) VALUES (?, ?)",
                    new String[] {"id"});
            statement.setString(1, taskNumber);
            statement.setLong(2, orderId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Production task insert did not return a generated ID.");
        }

        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Created production task could not be reloaded."));
    }

    @Override
    public int startPendingTask(long taskId) {
        return jdbcTemplate.update(
                """
                UPDATE production_tasks
                SET status = 'IN_PROGRESS',
                    started_at = CURRENT_TIMESTAMP(6),
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                  AND status = 'PENDING'
                """,
                taskId);
    }

    @Override
    public int completeInProgressTask(long taskId) {
        return jdbcTemplate.update(
                """
                UPDATE production_tasks
                SET status = 'COMPLETED',
                    completed_at = CURRENT_TIMESTAMP(6),
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                  AND status = 'IN_PROGRESS'
                """,
                taskId);
    }

    @Override
    public long countIncompleteTasksForOrder(long orderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_tasks WHERE order_id = ? AND status <> 'COMPLETED'",
                Long.class,
                orderId);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<ProductionTask> findById(long taskId) {
        return findTask(taskId, false);
    }

    @Override
    public Optional<ProductionTask> findByIdForUpdate(long taskId) {
        return findTask(taskId, true);
    }

    private Optional<ProductionTask> findTask(long taskId, boolean forUpdate) {
        String sql = """
                SELECT id, task_number, order_id, status,
                       started_at, completed_at, created_at, updated_at,
                       quality_control_result, quality_checked_by_user_id, quality_checked_at
                FROM production_tasks
                WHERE id = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<ProductionTask> tasks = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new ProductionTask(
                        resultSet.getLong("id"),
                        resultSet.getString("task_number"),
                        resultSet.getLong("order_id"),
                        ProductionTaskStatus.valueOf(resultSet.getString("status")),
                        resultSet.getTimestamp("started_at") == null
                                ? null : resultSet.getTimestamp("started_at").toInstant(),
                        resultSet.getTimestamp("completed_at") == null
                                ? null : resultSet.getTimestamp("completed_at").toInstant(),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant(),
                        ProductionQualityControlResult.valueOf(resultSet.getString("quality_control_result")),
                        resultSet.getObject("quality_checked_by_user_id") == null
                                ? null : resultSet.getLong("quality_checked_by_user_id"),
                        resultSet.getTimestamp("quality_checked_at") == null
                                ? null : resultSet.getTimestamp("quality_checked_at").toInstant()),
                taskId);
        return tasks.stream().findFirst();
    }


    @Override
    public List<ProductionTask> findRecords(ProductionTaskRecordView view) {
        String predicate = switch (view) {
            case ACTIVE -> " WHERE status IN ('PENDING', 'IN_PROGRESS')";
            case COMPLETED -> " WHERE status = 'COMPLETED'";
            case ALL -> "";
        };
        return jdbcTemplate.query(
                """
                SELECT id, task_number, order_id, status,
                       started_at, completed_at, created_at, updated_at,
                       quality_control_result, quality_checked_by_user_id, quality_checked_at
                FROM production_tasks
                """ + predicate + " ORDER BY created_at DESC, id DESC",
                (resultSet, rowNumber) -> new ProductionTask(
                        resultSet.getLong("id"),
                        resultSet.getString("task_number"),
                        resultSet.getLong("order_id"),
                        ProductionTaskStatus.valueOf(resultSet.getString("status")),
                        resultSet.getTimestamp("started_at") == null
                                ? null : resultSet.getTimestamp("started_at").toInstant(),
                        resultSet.getTimestamp("completed_at") == null
                                ? null : resultSet.getTimestamp("completed_at").toInstant(),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant(),
                        ProductionQualityControlResult.valueOf(resultSet.getString("quality_control_result")),
                        resultSet.getObject("quality_checked_by_user_id") == null
                                ? null : resultSet.getLong("quality_checked_by_user_id"),
                        resultSet.getTimestamp("quality_checked_at") == null
                                ? null : resultSet.getTimestamp("quality_checked_at").toInstant()));
    }

    @Override
    public int updateQualityControl(
            long taskId,
            ProductionQualityControlResult result,
            long checkedByUserId) {
        return jdbcTemplate.update(
                """
                UPDATE production_tasks
                SET quality_control_result = ?,
                    quality_checked_by_user_id = ?,
                    quality_checked_at = CURRENT_TIMESTAMP(6),
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                  AND status = 'IN_PROGRESS'
                """,
                result.name(), checkedByUserId, taskId);
    }

    @Override
    public Optional<ProductionTaskWorkDetails> findWorkDetails(long taskId) {
        List<ProductionTaskWorkDetails> details = jdbcTemplate.query(
                """
                SELECT production_task_id, work_details, work_assignment, work_notes,
                       created_at, updated_at
                FROM production_task_details
                WHERE production_task_id = ?
                """,
                (resultSet, rowNumber) -> new ProductionTaskWorkDetails(
                        resultSet.getLong("production_task_id"),
                        resultSet.getString("work_details"),
                        resultSet.getString("work_assignment"),
                        resultSet.getString("work_notes"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                taskId);
        return details.stream().findFirst();
    }

    @Override
    public ProductionTaskWorkDetails saveWorkDetails(
            long taskId,
            String workDetails,
            String workAssignment,
            String workNotes) {
        int updated = jdbcTemplate.update(
                """
                UPDATE production_task_details
                SET work_details = ?,
                    work_assignment = ?,
                    work_notes = ?,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE production_task_id = ?
                """,
                workDetails, workAssignment, workNotes, taskId);

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production_task_details (
                        production_task_id, work_details, work_assignment, work_notes
                    ) VALUES (?, ?, ?, ?)
                    """,
                    taskId, workDetails, workAssignment, workNotes);
        }

        return findWorkDetails(taskId)
                .orElseThrow(() -> new IllegalStateException("Saved production task details could not be reloaded."));
    }
    @Override
    public List<ProductionTaskMaterialRequirement> findMaterialRequirements(long taskId) {
        return jdbcTemplate.query(
                """
                SELECT production_task_id, inventory_material_id, required_quantity,
                       created_at, updated_at
                FROM production_task_material_requirements
                WHERE production_task_id = ?
                ORDER BY inventory_material_id
                """,
                (resultSet, rowNumber) -> new ProductionTaskMaterialRequirement(
                        resultSet.getLong("production_task_id"),
                        resultSet.getLong("inventory_material_id"),
                        resultSet.getBigDecimal("required_quantity"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()),
                taskId);
    }

    @Override
    public void replaceMaterialRequirements(
            long taskId,
            List<ProductionTaskMaterialRequirementCommand> requirements) {
        jdbcTemplate.update(
                "DELETE FROM production_task_material_requirements WHERE production_task_id = ?",
                taskId);
        for (ProductionTaskMaterialRequirementCommand requirement : requirements) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production_task_material_requirements (
                        production_task_id, inventory_material_id, required_quantity
                    ) VALUES (?, ?, ?)
                    """,
                    taskId, requirement.inventoryMaterialId(), requirement.requiredQuantity());
        }
    }

    @Override
    public List<ProductionTaskMaterialUsage> findMaterialUsage(long taskId) {
        return jdbcTemplate.query(
                """
                SELECT id, production_task_id, inventory_material_id, quantity_used,
                       recorded_by_user_id, recorded_at
                FROM production_task_material_usage
                WHERE production_task_id = ?
                ORDER BY inventory_material_id
                """,
                (resultSet, rowNumber) -> new ProductionTaskMaterialUsage(
                        resultSet.getLong("id"),
                        resultSet.getLong("production_task_id"),
                        resultSet.getLong("inventory_material_id"),
                        resultSet.getBigDecimal("quantity_used"),
                        resultSet.getLong("recorded_by_user_id"),
                        resultSet.getTimestamp("recorded_at").toInstant()),
                taskId);
    }

    @Override
    public ProductionTaskMaterialUsage createMaterialUsage(
            long taskId,
            long inventoryMaterialId,
            BigDecimal quantityUsed,
            long recordedByUserId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO production_task_material_usage (
                        production_task_id, inventory_material_id, quantity_used, recorded_by_user_id
                    ) VALUES (?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, taskId);
            statement.setLong(2, inventoryMaterialId);
            statement.setBigDecimal(3, quantityUsed);
            statement.setLong(4, recordedByUserId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Production material usage insert did not return a generated ID.");
        }
        return findMaterialUsage(taskId).stream()
                .filter(usage -> usage.id() == key.longValue())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Recorded production material usage could not be reloaded."));
    }

}

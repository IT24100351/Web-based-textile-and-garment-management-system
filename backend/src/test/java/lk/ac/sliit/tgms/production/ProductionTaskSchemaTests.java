package lk.ac.sliit.tgms.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class ProductionTaskSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void taskUsesStableIdsValidOrderLinkControlledDefaultStatusAndProgressTimestamps()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "production-task-customer@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-PRODUCTION-001", "CONFIRMED");

            long firstTaskId = insertTask(
                    connection,
                    "PRD-20260823-0001",
                    orderId,
                    null,
                    null,
                    null);
            long secondTaskId = insertTask(
                    connection,
                    "PRD-20260823-0002",
                    orderId,
                    null,
                    null,
                    null);

            assertThat(firstTaskId).isPositive().isNotEqualTo(secondTaskId);

            ProductionTaskRow first = readTask(connection, firstTaskId);
            assertThat(first.taskNumber()).isEqualTo("PRD-20260823-0001");
            assertThat(first.orderId()).isEqualTo(orderId);
            assertThat(first.status()).isEqualTo(ProductionTaskStatus.PENDING.name());
            assertThat(first.startedAt()).isNull();
            assertThat(first.completedAt()).isNull();
            assertThat(first.createdAt()).isNotNull();
            assertThat(first.updatedAt()).isNotNull();
        }
    }

    @Test
    void everyDocumentedProductionStatusIsAcceptedWithConsistentTimestamps() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "production-status-customer@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-PRODUCTION-STATUS", "CONFIRMED");
            Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Instant completedAt = startedAt.plus(2, ChronoUnit.HOURS);

            long pendingId = insertTask(
                    connection,
                    "PRD-STATUS-PENDING",
                    orderId,
                    ProductionTaskStatus.PENDING.name(),
                    null,
                    null);
            long inProgressId = insertTask(
                    connection,
                    "PRD-STATUS-IN-PROGRESS",
                    orderId,
                    ProductionTaskStatus.IN_PROGRESS.name(),
                    startedAt,
                    null);
            long completedId = insertTask(
                    connection,
                    "PRD-STATUS-COMPLETED",
                    orderId,
                    ProductionTaskStatus.COMPLETED.name(),
                    startedAt,
                    completedAt);

            assertThat(readTask(connection, pendingId).status())
                    .isEqualTo(ProductionTaskStatus.PENDING.name());
            assertThat(readTask(connection, inProgressId).status())
                    .isEqualTo(ProductionTaskStatus.IN_PROGRESS.name());
            ProductionTaskRow completed = readTask(connection, completedId);
            assertThat(completed.status()).isEqualTo(ProductionTaskStatus.COMPLETED.name());
            assertThat(completed.startedAt()).isNotNull();
            assertThat(completed.completedAt()).isNotNull();
            assertThat(completed.completedAt()).isAfterOrEqualTo(completed.startedAt());
        }
    }

    @Test
    void taskRejectsBrokenOrderDuplicateOrBlankNumberUnknownStatusAndInvalidProgressDates()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "production-integrity-customer@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-PRODUCTION-INTEGRITY", "CONFIRMED");
            insertTask(connection, "PRD-INTEGRITY-001", orderId, null, null, null);

            assertThatThrownBy(() -> insertTask(
                            connection,
                            "PRD-BROKEN-ORDER",
                            Long.MAX_VALUE,
                            null,
                            null,
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTask(
                            connection,
                            "PRD-INTEGRITY-001",
                            orderId,
                            null,
                            null,
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTask(connection, "   ", orderId, null, null, null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTask(
                            connection,
                            "PRD-UNKNOWN-STATUS",
                            orderId,
                            "UNKNOWN",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);

            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            assertThatThrownBy(() -> insertTask(
                            connection,
                            "PRD-IN-PROGRESS-WITHOUT-START",
                            orderId,
                            ProductionTaskStatus.IN_PROGRESS.name(),
                            null,
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTask(
                            connection,
                            "PRD-COMPLETED-WITHOUT-END",
                            orderId,
                            ProductionTaskStatus.COMPLETED.name(),
                            now,
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTask(
                            connection,
                            "PRD-END-BEFORE-START",
                            orderId,
                            ProductionTaskStatus.COMPLETED.name(),
                            now,
                            now.minus(1, ChronoUnit.HOURS)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "DELETE FROM orders WHERE id = ?", orderId))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void productionTaskDetailsAreOneToOneRequiredAndHistoryPreserving() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "production-details-customer@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-PRODUCTION-DETAILS", "CONFIRMED");
            long taskId = insertTask(
                    connection, "PRD-DETAILS-001", orderId, null, null, null);

            execute(
                    connection,
                    "INSERT INTO production_task_details (production_task_id, work_details, work_assignment, work_notes) VALUES (?, ?, ?, ?)",
                    taskId,
                    "Cut and stitch the garments from the linked order.",
                    "Sewing Line A",
                    "Inspect collars before finishing.");

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT work_details, work_assignment, work_notes FROM production_task_details WHERE production_task_id = ?")) {
                statement.setLong(1, taskId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("work_details")).contains("Cut and stitch");
                    assertThat(resultSet.getString("work_assignment")).isEqualTo("Sewing Line A");
                    assertThat(resultSet.getString("work_notes")).isEqualTo("Inspect collars before finishing.");
                }
            }

            assertThatThrownBy(() -> execute(
                            connection,
                            "INSERT INTO production_task_details (production_task_id, work_details, work_assignment) VALUES (?, ?, ?)",
                            taskId, "Duplicate details", "Another team"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "INSERT INTO production_task_details (production_task_id, work_details, work_assignment) VALUES (?, ?, ?)",
                            Long.MAX_VALUE, "Broken task", "Team"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "UPDATE production_task_details SET work_details = '   ' WHERE production_task_id = ?",
                            taskId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "DELETE FROM production_tasks WHERE id = ?", taskId))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "production_task_schema_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
    }

    private long insertUser(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'CUSTOMER')
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, "$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            statement.setString(3, "Production Task Customer");
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertOrder(Connection connection, long customerId, String orderNumber, String status)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, order_number, status) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.setString(2, orderNumber);
            statement.setString(3, status);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertTask(
            Connection connection,
            String taskNumber,
            long orderId,
            String status,
            Instant startedAt,
            Instant completedAt)
            throws SQLException {
        String sql = status == null
                ? """
                  INSERT INTO production_tasks (task_number, order_id)
                  VALUES (?, ?)
                  """
                : """
                  INSERT INTO production_tasks (
                      task_number, order_id, status, started_at, completed_at
                  )
                  VALUES (?, ?, ?, ?, ?)
                  """;
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, taskNumber);
            statement.setLong(2, orderId);
            if (status != null) {
                statement.setString(3, status);
                statement.setTimestamp(4, startedAt == null ? null : Timestamp.from(startedAt));
                statement.setTimestamp(5, completedAt == null ? null : Timestamp.from(completedAt));
            }
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private ProductionTaskRow readTask(Connection connection, long taskId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT task_number, order_id, status, started_at, completed_at, created_at, updated_at
                FROM production_tasks
                WHERE id = ?
                """)) {
            statement.setLong(1, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new ProductionTaskRow(
                        resultSet.getString("task_number"),
                        resultSet.getLong("order_id"),
                        resultSet.getString("status"),
                        toInstant(resultSet.getTimestamp("started_at")),
                        toInstant(resultSet.getTimestamp("completed_at")),
                        toInstant(resultSet.getTimestamp("created_at")),
                        toInstant(resultSet.getTimestamp("updated_at")));
            }
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ProductionTaskRow(
            String taskNumber,
            long orderId,
            String status,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt) {}

    private record MigratedDatabase(Connection connection, Liquibase liquibase)
            implements AutoCloseable {

        @Override
        public void close() throws Exception {
            liquibase.close();
        }
    }
}

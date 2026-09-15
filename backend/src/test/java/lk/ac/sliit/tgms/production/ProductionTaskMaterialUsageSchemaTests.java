package lk.ac.sliit.tgms.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class ProductionTaskMaterialUsageSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void usageReferencesApprovedRequirementAndPreventsDuplicateDeductionHistory() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "usage-customer@example.com", "CUSTOMER");
            long recorderId = insertUser(connection, "usage-manager@example.com", "PRODUCTION_MANAGER");
            long orderId = insertOrder(connection, customerId);
            long taskId = insertTask(connection, orderId);
            long materialId = insertInventoryMaterial(connection);
            execute(connection,
                    "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (?, ?, 12.500)",
                    taskId, materialId);

            long usageId = insertUsage(connection, taskId, materialId, new BigDecimal("12.500"), recorderId);
            assertThat(usageId).isPositive();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT quantity_used, recorded_by_user_id FROM production_task_material_usage WHERE id = ?")) {
                statement.setLong(1, usageId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getBigDecimal("quantity_used")).isEqualByComparingTo("12.500");
                    assertThat(resultSet.getLong("recorded_by_user_id")).isEqualTo(recorderId);
                }
            }

            assertThatThrownBy(() -> insertUsage(
                            connection, taskId, materialId, new BigDecimal("12.500"), recorderId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertUsage(
                            connection, taskId, materialId + 9999, new BigDecimal("1.000"), recorderId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertUsage(
                            connection, taskId, materialId, BigDecimal.ZERO, recorderId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertUsage(
                            connection, taskId, materialId, new BigDecimal("1.000"), recorderId + 9999))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "DELETE FROM production_task_material_requirements WHERE production_task_id = ? AND inventory_material_id = ?",
                            taskId,
                            materialId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "DELETE FROM users WHERE id = ?", recorderId))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "production_usage_schema_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
    }

    private long insertUser(Connection connection, String email, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'x', 'Usage User', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, role);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertOrder(Connection connection, long customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, order_number, status) VALUES (?, 'ORD-USAGE-SCHEMA', 'CONFIRMED')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertTask(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO production_tasks (task_number, order_id, status, started_at) VALUES ('PRD-USAGE-SCHEMA', ?, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, orderId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertInventoryMaterial(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO inventory_materials (material_code, material_name, material_type, unit_of_measure, current_quantity, low_stock_threshold, status) VALUES ('MAT-USAGE-001', 'Usage Fabric', 'FABRIC', 'metre', 100.000, 10.000, 'ACTIVE')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertUsage(
            Connection connection,
            long taskId,
            long materialId,
            BigDecimal quantity,
            long recorderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO production_task_material_usage (production_task_id, inventory_material_id, quantity_used, recorded_by_user_id) VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, taskId);
            statement.setLong(2, materialId);
            statement.setBigDecimal(3, quantity);
            statement.setLong(4, recorderId);
            statement.executeUpdate();
            return generatedId(statement);
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

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("Generated ID was not returned.");
            return keys.getLong(1);
        }
    }

    private record MigratedDatabase(Connection connection, Liquibase liquibase) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            liquibase.close();
            connection.close();
        }
    }
}

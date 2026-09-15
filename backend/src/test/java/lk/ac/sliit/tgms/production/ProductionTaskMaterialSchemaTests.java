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

class ProductionTaskMaterialSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void taskMaterialRequirementsReferenceRealInventoryAndRejectDuplicatesAndInvalidQuantity()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection);
            long orderId = insertOrder(connection, customerId);
            long taskId = insertTask(connection, orderId);
            long firstMaterialId = insertInventoryMaterial(connection, "MAT-PROD-001", "FABRIC");
            long secondMaterialId = insertInventoryMaterial(connection, "MAT-PROD-002", "RAW_MATERIAL");

            insertRequirement(connection, taskId, firstMaterialId, new BigDecimal("25.500"));
            insertRequirement(connection, taskId, secondMaterialId, new BigDecimal("3.000"));

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT inventory_material_id, required_quantity FROM production_task_material_requirements WHERE production_task_id = ? ORDER BY inventory_material_id")) {
                statement.setLong(1, taskId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getLong("inventory_material_id")).isEqualTo(firstMaterialId);
                    assertThat(resultSet.getBigDecimal("required_quantity")).isEqualByComparingTo("25.500");
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getLong("inventory_material_id")).isEqualTo(secondMaterialId);
                    assertThat(resultSet.next()).isFalse();
                }
            }

            assertThatThrownBy(() -> insertRequirement(
                            connection, taskId, firstMaterialId, new BigDecimal("1.000")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertRequirement(
                            connection, taskId, Long.MAX_VALUE, new BigDecimal("1.000")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertRequirement(
                            connection, Long.MAX_VALUE, firstMaterialId, new BigDecimal("1.000")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertRequirement(
                            connection, taskId, firstMaterialId, BigDecimal.ZERO))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "DELETE FROM inventory_materials WHERE id = ?", firstMaterialId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(connection, "DELETE FROM production_tasks WHERE id = ?", taskId))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "production_material_schema_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
    }

    private long insertUser(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (email, password_hash, full_name, role) VALUES ('prod-mat@example.com', 'x', 'Prod Mat Customer', 'CUSTOMER')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertOrder(Connection connection, long customerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, order_number, status) VALUES (?, 'ORD-PROD-MAT', 'CONFIRMED')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertTask(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO production_tasks (task_number, order_id) VALUES ('PRD-MAT-SCHEMA', ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, orderId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertInventoryMaterial(Connection connection, String code, String type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO inventory_materials (material_code, material_name, material_type, unit_of_measure, current_quantity, low_stock_threshold, status) VALUES (?, ?, ?, 'metre', 100.000, 10.000, 'ACTIVE')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, code + " name");
            statement.setString(3, type);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private void insertRequirement(
            Connection connection,
            long taskId,
            long materialId,
            BigDecimal quantity) throws SQLException {
        execute(
                connection,
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (?, ?, ?)",
                taskId,
                materialId,
                quantity);
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

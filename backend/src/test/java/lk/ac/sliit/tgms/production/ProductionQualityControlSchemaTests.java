package lk.ac.sliit.tgms.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class ProductionQualityControlSchemaTests {

    @Test
    void qualityControlDefaultsPendingAndRequiresValidCheckerForPassOrFail() throws Exception {
        String url = "jdbc:h2:mem:production_qc_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new ClassLoaderResourceAccessor(),
                    database)) {
                liquibase.update(new Contexts(), new LabelExpression());

                execute(connection, "INSERT INTO users (id, email, password_hash, full_name, role, is_active) VALUES (1, 'qc-customer@example.com', 'x', 'Customer', 'CUSTOMER', TRUE)");
                execute(connection, "INSERT INTO users (id, email, password_hash, full_name, role, is_active) VALUES (2, 'qc-manager@example.com', 'x', 'Manager', 'PRODUCTION_MANAGER', TRUE)");
                execute(connection, "INSERT INTO orders (id, customer_id, order_number, status) VALUES (10, 1, 'ORD-QC-10', 'IN_PRODUCTION')");
                execute(connection, "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (20, 'PRD-QC-20', 10, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT quality_control_result, quality_checked_by_user_id, quality_checked_at FROM production_tasks WHERE id = 20");
                        ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("quality_control_result")).isEqualTo("PENDING");
                    assertThat(resultSet.getObject("quality_checked_by_user_id")).isNull();
                    assertThat(resultSet.getTimestamp("quality_checked_at")).isNull();
                }

                execute(connection, "UPDATE production_tasks SET quality_control_result = 'PASSED', quality_checked_by_user_id = 2, quality_checked_at = CURRENT_TIMESTAMP(6) WHERE id = 20");
                assertThatThrownBy(() -> execute(connection,
                        "UPDATE production_tasks SET quality_control_result = 'UNKNOWN' WHERE id = 20"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> execute(connection,
                        "UPDATE production_tasks SET quality_control_result = 'FAILED', quality_checked_by_user_id = NULL, quality_checked_at = NULL WHERE id = 20"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> execute(connection, "DELETE FROM users WHERE id = 2"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}

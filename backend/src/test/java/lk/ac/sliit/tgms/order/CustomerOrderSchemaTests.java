package lk.ac.sliit.tgms.order;

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
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class CustomerOrderSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void orderHeaderUsesStableIdentifiersCustomerLinkControlledDefaultStatusAndTimestamps()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "order-customer@example.com", "CUSTOMER");
            long firstOrderId = insertOrder(connection, customerId, "ORD-20260823-0001", null);
            long secondOrderId = insertOrder(
                    connection,
                    customerId,
                    "ORD-20260823-0002",
                    OrderStatus.CONFIRMED.name());

            assertThat(firstOrderId).isPositive().isNotEqualTo(secondOrderId);

            OrderRow first = readOrder(connection, firstOrderId);
            assertThat(first.customerId()).isEqualTo(customerId);
            assertThat(first.orderNumber()).isEqualTo("ORD-20260823-0001");
            assertThat(first.status()).isEqualTo(OrderStatus.PENDING.name());
            assertThat(first.createdAt()).isNotNull();
            assertThat(first.updatedAt()).isNotNull();

            OrderRow second = readOrder(connection, secondOrderId);
            assertThat(second.status()).isEqualTo(OrderStatus.CONFIRMED.name());
        }
    }

    @Test
    void orderHeaderRejectsBrokenCustomerDuplicateNumberBlankNumberAndUnknownStatus()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "order-integrity@example.com", "CUSTOMER");
            insertOrder(connection, customerId, "ORD-INTEGRITY-001", null);

            assertThatThrownBy(() -> insertOrder(
                            connection,
                            Long.MAX_VALUE,
                            "ORD-BROKEN-CUSTOMER",
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrder(
                            connection,
                            customerId,
                            "ORD-INTEGRITY-001",
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrder(connection, customerId, "   ", null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrder(
                            connection,
                            customerId,
                            "ORD-UNKNOWN-STATUS",
                            "UNKNOWN"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "DELETE FROM users WHERE id = ?",
                            customerId))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void everyDocumentedOrderStatusIsAcceptedByTheSchema() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "order-status@example.com", "CUSTOMER");

            int index = 1;
            for (OrderStatus status : OrderStatus.values()) {
                long orderId = insertOrder(
                        connection,
                        customerId,
                        "ORD-STATUS-" + index++,
                        status.name());
                assertThat(readOrder(connection, orderId).status()).isEqualTo(status.name());
            }
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "customer_order_schema_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase =
                new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
    }

    private long insertUser(Connection connection, String email, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, "$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            statement.setString(3, "Order Customer");
            statement.setString(4, role);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private long insertOrder(
            Connection connection, long customerId, String orderNumber, String status)
            throws SQLException {
        String sql = status == null
                ? "INSERT INTO orders (customer_id, order_number) VALUES (?, ?)"
                : "INSERT INTO orders (customer_id, order_number, status) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.setString(2, orderNumber);
            if (status != null) {
                statement.setString(3, status);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
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

    private OrderRow readOrder(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT customer_id, order_number, status, created_at, updated_at
                FROM orders
                WHERE id = ?
                """)) {
            statement.setLong(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                return new OrderRow(
                        resultSet.getLong("customer_id"),
                        resultSet.getString("order_number"),
                        resultSet.getString("status"),
                        createdAt == null ? null : createdAt.toInstant(),
                        updatedAt == null ? null : updatedAt.toInstant());
            }
        }
    }

    private record OrderRow(
            long customerId,
            String orderNumber,
            String status,
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

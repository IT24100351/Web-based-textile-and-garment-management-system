package lk.ac.sliit.tgms.delivery;

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

class DeliveryRecordSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void deliveryHasStableIdOrderLinkScheduleRequiredDetailsDefaultStatusAndTimestamps()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long firstCustomerId = insertUser(connection, "delivery-one@example.com");
            long firstOrderId = insertOrder(
                    connection, firstCustomerId, "ORD-DELIVERY-001", "READY_FOR_DELIVERY");
            long secondCustomerId = insertUser(connection, "delivery-two@example.com");
            long secondOrderId = insertOrder(
                    connection, secondCustomerId, "ORD-DELIVERY-002", "READY_FOR_DELIVERY");
            Instant firstSchedule = Instant.now()
                    .plus(1, ChronoUnit.DAYS)
                    .truncatedTo(ChronoUnit.MILLIS);
            Instant secondSchedule = firstSchedule.plus(2, ChronoUnit.HOURS);

            long firstDeliveryId = insertDelivery(
                    connection,
                    "DLV-20260824-0001",
                    firstOrderId,
                    firstSchedule,
                    "25 Galle Road, Colombo",
                    "Call the customer before arrival.",
                    null);
            long secondDeliveryId = insertDelivery(
                    connection,
                    "DLV-20260824-0002",
                    secondOrderId,
                    secondSchedule,
                    "18 Temple Road, Kandy",
                    null,
                    null);

            assertThat(firstDeliveryId).isPositive().isNotEqualTo(secondDeliveryId);

            DeliveryRow delivery = readDelivery(connection, firstDeliveryId);
            assertThat(delivery.deliveryNumber()).isEqualTo("DLV-20260824-0001");
            assertThat(delivery.orderId()).isEqualTo(firstOrderId);
            assertThat(delivery.scheduledAt()).isEqualTo(firstSchedule);
            assertThat(delivery.deliveryAddress()).isEqualTo("25 Galle Road, Colombo");
            assertThat(delivery.deliveryNotes()).isEqualTo("Call the customer before arrival.");
            assertThat(delivery.status()).isEqualTo(DeliveryStatus.SCHEDULED.name());
            assertThat(delivery.createdAt()).isNotNull();
            assertThat(delivery.updatedAt()).isNotNull();

            assertThat(readDelivery(connection, secondDeliveryId).deliveryNotes()).isNull();
        }
    }

    @Test
    void everyControlledDeliveryStatusIsAccepted() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            Instant scheduledAt = Instant.now()
                    .plus(1, ChronoUnit.DAYS)
                    .truncatedTo(ChronoUnit.MILLIS);

            int suffix = 1;
            for (DeliveryStatus status : DeliveryStatus.values()) {
                long customerId = insertUser(
                        connection, "delivery-status-" + suffix + "@example.com");
                long orderId = insertOrder(
                        connection,
                        customerId,
                        "ORD-DELIVERY-STATUS-" + suffix,
                        "READY_FOR_DELIVERY");
                long deliveryId = insertDelivery(
                        connection,
                        "DLV-STATUS-" + suffix,
                        orderId,
                        scheduledAt.plus(suffix, ChronoUnit.HOURS),
                        "Delivery address " + suffix,
                        null,
                        status.name());

                assertThat(readDelivery(connection, deliveryId).status()).isEqualTo(status.name());
                suffix++;
            }
        }
    }

    @Test
    void deliveryRejectsBrokenOrDuplicateLinksBlankRequiredDataAndUnknownStatus()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "delivery-integrity@example.com");
            long orderId = insertOrder(
                    connection, customerId, "ORD-DELIVERY-INTEGRITY", "READY_FOR_DELIVERY");
            Instant scheduledAt = Instant.now()
                    .plus(1, ChronoUnit.DAYS)
                    .truncatedTo(ChronoUnit.MILLIS);

            insertDelivery(
                    connection,
                    "DLV-INTEGRITY-001",
                    orderId,
                    scheduledAt,
                    "10 Main Street, Colombo",
                    null,
                    null);

            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-BROKEN-ORDER",
                            Long.MAX_VALUE,
                            scheduledAt,
                            "Broken order address",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-DUPLICATE-ORDER",
                            orderId,
                            scheduledAt,
                            "Another address",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);

            long anotherCustomerId = insertUser(connection, "delivery-integrity-two@example.com");
            long anotherOrderId = insertOrder(
                    connection,
                    anotherCustomerId,
                    "ORD-DELIVERY-INTEGRITY-2",
                    "READY_FOR_DELIVERY");
            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-INTEGRITY-001",
                            anotherOrderId,
                            scheduledAt,
                            "Another address",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);

            long blankNumberOrderId = newReadyOrder(connection, "blank-number");
            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "   ",
                            blankNumberOrderId,
                            scheduledAt,
                            "Valid address",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);

            long blankAddressOrderId = newReadyOrder(connection, "blank-address");
            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-BLANK-ADDRESS",
                            blankAddressOrderId,
                            scheduledAt,
                            "   ",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);

            long unknownStatusOrderId = newReadyOrder(connection, "unknown-status");
            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-UNKNOWN-STATUS",
                            unknownStatusOrderId,
                            scheduledAt,
                            "Valid address",
                            null,
                            "UNKNOWN"))
                    .isInstanceOf(SQLException.class);

            long nullScheduleOrderId = newReadyOrder(connection, "null-schedule");
            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-NULL-SCHEDULE",
                            nullScheduleOrderId,
                            null,
                            "Valid address",
                            null,
                            null))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> execute(connection, "DELETE FROM orders WHERE id = ?", orderId))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void cancelledDeliveryReleasesOrderLockWhilePreservingHistory() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "delivery-cancel-replace@example.com");
            long orderId = insertOrder(
                    connection, customerId, "ORD-DELIVERY-CANCEL-REPLACE", "READY_FOR_DELIVERY");
            Instant firstSchedule = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

            long cancelledId = insertDelivery(
                    connection,
                    "DLV-CANCELLED-HISTORY",
                    orderId,
                    firstSchedule,
                    "Old incorrect address",
                    null,
                    "CANCELLED");
            long replacementId = insertDelivery(
                    connection,
                    "DLV-REPLACEMENT",
                    orderId,
                    firstSchedule.plus(2, ChronoUnit.HOURS),
                    "Correct replacement address",
                    null,
                    "SCHEDULED");

            assertThat(cancelledId).isNotEqualTo(replacementId);
            assertThat(readDelivery(connection, cancelledId).status()).isEqualTo("CANCELLED");
            assertThat(readDelivery(connection, replacementId).status()).isEqualTo("SCHEDULED");

            assertThatThrownBy(() -> insertDelivery(
                            connection,
                            "DLV-SECOND-ACTIVE",
                            orderId,
                            firstSchedule.plus(4, ChronoUnit.HOURS),
                            "Another active address",
                            null,
                            "SCHEDULED"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "delivery_schema_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase =
                new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
    }

    private long newReadyOrder(Connection connection, String suffix) throws SQLException {
        long customerId = insertUser(connection, "delivery-" + suffix + "@example.com");
        return insertOrder(
                connection,
                customerId,
                "ORD-DELIVERY-" + suffix.toUpperCase().replace('-', '_'),
                "READY_FOR_DELIVERY");
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
            statement.setString(3, "Delivery Customer");
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertOrder(
            Connection connection, long customerId, String orderNumber, String status)
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

    private long insertDelivery(
            Connection connection,
            String deliveryNumber,
            long orderId,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes,
            String status)
            throws SQLException {
        String sql = status == null
                ? """
                  INSERT INTO deliveries
                      (delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, delivery_notes)
                  VALUES (?, ?, ?, ?, ?, ?)
                  """
                : """
                  INSERT INTO deliveries
                      (delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, delivery_notes, status)
                  VALUES (?, ?, ?, ?, ?, ?, ?)
                  """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, deliveryNumber);
            statement.setLong(2, orderId);
            if ("CANCELLED".equals(status)) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, orderId);
            }
            statement.setTimestamp(4, scheduledAt == null ? null : Timestamp.from(scheduledAt));
            statement.setString(5, deliveryAddress);
            statement.setString(6, deliveryNotes);
            if (status != null) {
                statement.setString(7, status);
            }
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private DeliveryRow readDelivery(Connection connection, long deliveryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT delivery_number, order_id, scheduled_at, delivery_address, delivery_notes,
                       status, created_at, updated_at
                FROM deliveries
                WHERE id = ?
                """)) {
            statement.setLong(1, deliveryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new DeliveryRow(
                        resultSet.getString("delivery_number"),
                        resultSet.getLong("order_id"),
                        resultSet.getTimestamp("scheduled_at").toInstant(),
                        resultSet.getString("delivery_address"),
                        resultSet.getString("delivery_notes"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant());
            }
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

    private record DeliveryRow(
            String deliveryNumber,
            long orderId,
            Instant scheduledAt,
            String deliveryAddress,
            String deliveryNotes,
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

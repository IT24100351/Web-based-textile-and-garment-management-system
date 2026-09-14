package lk.ac.sliit.tgms.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class MaterialSupplySchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void supplyHasStableIdSupplierLinkMaterialCommercialAndDeliveryData()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long supplierId = insertSupplier(connection, "supply-schema-one@example.com");
            long supplyId = insertSupply(
                    connection,
                    supplierId,
                    "FAB-COT-001",
                    "Cotton twill fabric",
                    "Durable 240 GSM navy cotton twill",
                    new BigDecimal("1250.750"),
                    "metre",
                    new BigDecimal("845.50"),
                    7,
                    "Delivered to the main receiving bay",
                    null);

            MaterialSupplyRow supply = readSupply(connection, supplyId);
            assertThat(supply.id()).isPositive();
            assertThat(supply.supplierId()).isEqualTo(supplierId);
            assertThat(supply.materialCode()).isEqualTo("FAB-COT-001");
            assertThat(supply.materialName()).isEqualTo("Cotton twill fabric");
            assertThat(supply.materialDescription())
                    .isEqualTo("Durable 240 GSM navy cotton twill");
            assertThat(supply.quantity()).isEqualByComparingTo("1250.750");
            assertThat(supply.unitOfMeasure()).isEqualTo("metre");
            assertThat(supply.unitPrice()).isEqualByComparingTo("845.50");
            assertThat(supply.deliveryLeadTimeDays()).isEqualTo(7);
            assertThat(supply.deliveryNotes())
                    .isEqualTo("Delivered to the main receiving bay");
            assertThat(supply.status()).isEqualTo("ACTIVE");
            assertThat(supply.createdAt()).isNotNull();
            assertThat(supply.updatedAt()).isNotNull();

            assertThat(materialSupplyColumns(connection))
                    .containsExactly(
                            "CREATED_AT",
                            "DELIVERY_LEAD_TIME_DAYS",
                            "DELIVERY_NOTES",
                            "ID",
                            "MATERIAL_CODE",
                            "MATERIAL_DESCRIPTION",
                            "MATERIAL_NAME",
                            "QUANTITY",
                            "STATUS",
                            "SUPPLIER_ID",
                            "UNIT_OF_MEASURE",
                            "UNIT_PRICE",
                            "UPDATED_AT");

            assertThatThrownBy(() -> deleteSupplier(connection, supplierId))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void supplyRejectsInvalidOwnershipTextNumbersStatusAndDuplicateSupplierCode()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long supplierId = insertSupplier(connection, "supply-schema-two@example.com");
            long otherSupplierId = insertSupplier(
                    connection, "supply-schema-three@example.com");
            insertValidSupply(connection, supplierId, "THREAD-001");

            assertThatThrownBy(() -> insertValidSupply(
                            connection, Long.MAX_VALUE, "ORPHAN-001"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertValidSupply(
                            connection, supplierId, "THREAD-001"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "NEG-QTY",
                            "Invalid quantity",
                            null,
                            new BigDecimal("-0.001"),
                            "cone",
                            new BigDecimal("100.00"),
                            2,
                            null,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "NEG-PRICE",
                            "Invalid price",
                            null,
                            BigDecimal.ONE,
                            "unit",
                            new BigDecimal("-0.01"),
                            2,
                            null,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "NEG-DELIVERY",
                            "Invalid delivery lead time",
                            null,
                            BigDecimal.ONE,
                            "unit",
                            BigDecimal.ONE,
                            -1,
                            null,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "BAD-STATUS",
                            "Invalid status",
                            null,
                            BigDecimal.ONE,
                            "unit",
                            BigDecimal.ONE,
                            1,
                            null,
                            "UNKNOWN"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "   ",
                            "Blank code",
                            null,
                            BigDecimal.ONE,
                            "unit",
                            BigDecimal.ONE,
                            1,
                            null,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "BLANK-NAME",
                            "   ",
                            null,
                            BigDecimal.ONE,
                            "unit",
                            BigDecimal.ONE,
                            1,
                            null,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSupply(
                            connection,
                            supplierId,
                            "BLANK-UNIT",
                            "Blank unit",
                            null,
                            BigDecimal.ONE,
                            "   ",
                            BigDecimal.ONE,
                            1,
                            null,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);

            long zeroValueSupplyId = insertSupply(
                    connection,
                    otherSupplierId,
                    "THREAD-001",
                    "Temporarily unavailable thread",
                    null,
                    BigDecimal.ZERO,
                    "cone",
                    BigDecimal.ZERO,
                    0,
                    null,
                    "INACTIVE");
            assertThat(readSupply(connection, zeroValueSupplyId).supplierId())
                    .isEqualTo(otherSupplierId);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "material_supply_schema_"
                + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase =
                new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
    }

    private long insertSupplier(Connection connection, String email) throws SQLException {
        long userId = insertAndReturnId(
                connection,
                """
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'SUPPLIER')
                """,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                "Material Supplier Account");
        return insertAndReturnId(
                connection,
                """
                INSERT INTO supplier_profiles
                    (user_id, business_name, contact_phone, address)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                "Material Supplier " + userId,
                "011 234 5678",
                "Colombo");
    }

    private long insertValidSupply(Connection connection, long supplierId, String materialCode)
            throws SQLException {
        return insertSupply(
                connection,
                supplierId,
                materialCode,
                "Polyester thread",
                null,
                new BigDecimal("25.000"),
                "cone",
                new BigDecimal("475.00"),
                3,
                null,
                "ACTIVE");
    }

    private long insertSupply(
            Connection connection,
            long supplierId,
            String materialCode,
            String materialName,
            String description,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes,
            String status) throws SQLException {
        String sql = """
                INSERT INTO material_supplies
                    (supplier_id, material_code, material_name, material_description,
                     quantity, unit_of_measure, unit_price, delivery_lead_time_days,
                     delivery_notes%s)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?%s)
                """.formatted(
                status == null ? "" : ", status",
                status == null ? "" : ", ?");
        var values = new ArrayList<Object>();
        values.add(supplierId);
        values.add(materialCode);
        values.add(materialName);
        values.add(description);
        values.add(quantity);
        values.add(unitOfMeasure);
        values.add(unitPrice);
        values.add(deliveryLeadTimeDays);
        values.add(deliveryNotes);
        if (status != null) {
            values.add(status);
        }
        return insertAndReturnId(connection, sql, values.toArray());
    }

    private MaterialSupplyRow readSupply(Connection connection, long supplyId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id, supplier_id, material_code, material_name, material_description,
                       quantity, unit_of_measure, unit_price, delivery_lead_time_days,
                       delivery_notes, status, created_at, updated_at
                FROM material_supplies
                WHERE id = ?
                """)) {
            statement.setLong(1, supplyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new MaterialSupplyRow(
                        resultSet.getLong("id"),
                        resultSet.getLong("supplier_id"),
                        resultSet.getString("material_code"),
                        resultSet.getString("material_name"),
                        resultSet.getString("material_description"),
                        resultSet.getBigDecimal("quantity"),
                        resultSet.getString("unit_of_measure"),
                        resultSet.getBigDecimal("unit_price"),
                        resultSet.getInt("delivery_lead_time_days"),
                        resultSet.getString("delivery_notes"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant());
            }
        }
    }

    private long insertAndReturnId(Connection connection, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private void deleteSupplier(Connection connection, long supplierId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM supplier_profiles WHERE id = ?")) {
            statement.setLong(1, supplierId);
            statement.executeUpdate();
        }
    }

    private List<String> materialSupplyColumns(Connection connection) throws SQLException {
        String query = """
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_schema) = 'public'
                  AND LOWER(table_name) = 'material_supplies'
                ORDER BY column_name
                """;
        var columns = new ArrayList<String>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString(1));
            }
        }
        return columns;
    }

    private void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private record MaterialSupplyRow(
            long id,
            long supplierId,
            String materialCode,
            String materialName,
            String materialDescription,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal unitPrice,
            int deliveryLeadTimeDays,
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

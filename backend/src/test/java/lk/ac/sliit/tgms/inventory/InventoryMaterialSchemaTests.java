package lk.ac.sliit.tgms.inventory;

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

class InventoryMaterialSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void inventoryMaterialHasStableIdDetailsQuantityThresholdStatusAndSupplyLink()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long supplierId = insertSupplier(connection, "inventory-schema-one@example.com");
            long supplyId = insertSupply(connection, supplierId, "SUP-FAB-001");
            long materialId = insertMaterial(
                    connection,
                    supplyId,
                    "INV-FAB-001",
                    "Cotton twill fabric",
                    "Main store 240 GSM cotton twill",
                    "FABRIC",
                    "metre",
                    new BigDecimal("840.250"),
                    new BigDecimal("100.000"),
                    null);

            InventoryMaterialRow material = readMaterial(connection, materialId);
            assertThat(material.id()).isPositive();
            assertThat(material.sourceMaterialSupplyId()).isEqualTo(supplyId);
            assertThat(material.materialCode()).isEqualTo("INV-FAB-001");
            assertThat(material.materialName()).isEqualTo("Cotton twill fabric");
            assertThat(material.materialDescription())
                    .isEqualTo("Main store 240 GSM cotton twill");
            assertThat(material.materialType()).isEqualTo("FABRIC");
            assertThat(material.unitOfMeasure()).isEqualTo("metre");
            assertThat(material.currentQuantity()).isEqualByComparingTo("840.250");
            assertThat(material.lowStockThreshold()).isEqualByComparingTo("100.000");
            assertThat(material.status()).isEqualTo("ACTIVE");
            assertThat(material.createdAt()).isNotNull();
            assertThat(material.updatedAt()).isNotNull();

            assertThat(inventoryMaterialColumns(connection))
                    .containsExactly(
                            "CREATED_AT",
                            "CURRENT_QUANTITY",
                            "ID",
                            "LOW_STOCK_THRESHOLD",
                            "MATERIAL_CODE",
                            "MATERIAL_DESCRIPTION",
                            "MATERIAL_NAME",
                            "MATERIAL_TYPE",
                            "SOURCE_MATERIAL_SUPPLY_ID",
                            "STATUS",
                            "UNIT_OF_MEASURE",
                            "UPDATED_AT");

            assertThatThrownBy(() -> deleteSupply(connection, supplyId))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void inventoryMaterialRejectsInvalidQuantitiesTextStatusTypeDuplicatesAndOrphans()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long supplierId = insertSupplier(connection, "inventory-schema-two@example.com");
            long supplyId = insertSupply(connection, supplierId, "SUP-RAW-001");
            insertValidMaterial(connection, supplyId, "INV-RAW-001");

            assertThatThrownBy(() -> insertValidMaterial(
                            connection, Long.MAX_VALUE, "INV-ORPHAN-001"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertValidMaterial(
                            connection, supplyId, "INV-RAW-001"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "NEG-QTY",
                            "Invalid quantity",
                            null,
                            "RAW_MATERIAL",
                            "roll",
                            new BigDecimal("-0.001"),
                            BigDecimal.ZERO,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "NEG-THRESHOLD",
                            "Invalid threshold",
                            null,
                            "RAW_MATERIAL",
                            "roll",
                            BigDecimal.ZERO,
                            new BigDecimal("-0.001"),
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "BAD-TYPE",
                            "Invalid type",
                            null,
                            "TRIM",
                            "unit",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "BAD-STATUS",
                            "Invalid status",
                            null,
                            "FABRIC",
                            "metre",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            "UNKNOWN"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "   ",
                            "Blank code",
                            null,
                            "FABRIC",
                            "metre",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "BLANK-NAME",
                            "   ",
                            null,
                            "FABRIC",
                            "metre",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMaterial(
                            connection,
                            supplyId,
                            "BLANK-UNIT",
                            "Blank unit",
                            null,
                            "FABRIC",
                            "   ",
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            "ACTIVE"))
                    .isInstanceOf(SQLException.class);

            long zeroMaterialId = insertMaterial(
                    connection,
                    null,
                    "INV-ZERO-001",
                    "Zero stock lining",
                    null,
                    "FABRIC",
                    "metre",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "INACTIVE");
            assertThat(readMaterial(connection, zeroMaterialId).sourceMaterialSupplyId())
                    .isNull();
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "inventory_material_schema_"
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
                "Inventory Supplier Account");
        return insertAndReturnId(
                connection,
                """
                INSERT INTO supplier_profiles
                    (user_id, business_name, contact_phone, address)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                "Inventory Supplier " + userId,
                "011 234 5678",
                "Colombo");
    }

    private long insertSupply(Connection connection, long supplierId, String materialCode)
            throws SQLException {
        return insertAndReturnId(
                connection,
                """
                INSERT INTO material_supplies
                    (supplier_id, material_code, material_name, quantity, unit_of_measure,
                     unit_price, delivery_lead_time_days)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                supplierId,
                materialCode,
                "Supplier fabric",
                new BigDecimal("500.000"),
                "metre",
                new BigDecimal("750.00"),
                5);
    }

    private long insertValidMaterial(Connection connection, Long supplyId, String materialCode)
            throws SQLException {
        return insertMaterial(
                connection,
                supplyId,
                materialCode,
                "Polyester lining",
                null,
                "RAW_MATERIAL",
                "roll",
                new BigDecimal("25.000"),
                new BigDecimal("5.000"),
                "ACTIVE");
    }

    private long insertMaterial(
            Connection connection,
            Long supplyId,
            String materialCode,
            String materialName,
            String description,
            String materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            BigDecimal lowStockThreshold,
            String status) throws SQLException {
        String sql = """
                INSERT INTO inventory_materials
                    (source_material_supply_id, material_code, material_name,
                     material_description, material_type, unit_of_measure,
                     current_quantity, low_stock_threshold%s)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?%s)
                """.formatted(
                status == null ? "" : ", status",
                status == null ? "" : ", ?");
        var values = new ArrayList<Object>();
        values.add(supplyId);
        values.add(materialCode);
        values.add(materialName);
        values.add(description);
        values.add(materialType);
        values.add(unitOfMeasure);
        values.add(currentQuantity);
        values.add(lowStockThreshold);
        if (status != null) {
            values.add(status);
        }
        return insertAndReturnId(connection, sql, values.toArray());
    }

    private InventoryMaterialRow readMaterial(Connection connection, long materialId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id, source_material_supply_id, material_code, material_name,
                       material_description, material_type, unit_of_measure,
                       current_quantity, low_stock_threshold, status,
                       created_at, updated_at
                FROM inventory_materials
                WHERE id = ?
                """)) {
            statement.setLong(1, materialId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                Long supplyId = resultSet.getLong("source_material_supply_id");
                if (resultSet.wasNull()) {
                    supplyId = null;
                }
                return new InventoryMaterialRow(
                        resultSet.getLong("id"),
                        supplyId,
                        resultSet.getString("material_code"),
                        resultSet.getString("material_name"),
                        resultSet.getString("material_description"),
                        resultSet.getString("material_type"),
                        resultSet.getString("unit_of_measure"),
                        resultSet.getBigDecimal("current_quantity"),
                        resultSet.getBigDecimal("low_stock_threshold"),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant());
            }
        }
    }

    private List<String> inventoryMaterialColumns(Connection connection) throws SQLException {
        String query = """
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_schema) = 'public'
                  AND LOWER(table_name) = 'inventory_materials'
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

    private long insertAndReturnId(Connection connection, String sql, Object... values)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private void deleteSupply(Connection connection, long supplyId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("DELETE FROM material_supplies WHERE id = ?")) {
            statement.setLong(1, supplyId);
            statement.executeUpdate();
        }
    }

    private record InventoryMaterialRow(
            long id,
            Long sourceMaterialSupplyId,
            String materialCode,
            String materialName,
            String materialDescription,
            String materialType,
            String unitOfMeasure,
            BigDecimal currentQuantity,
            BigDecimal lowStockThreshold,
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

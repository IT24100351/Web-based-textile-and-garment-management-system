package lk.ac.sliit.tgms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";
    private static final String URL =
            "jdbc:h2:mem:migration_workflow;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @Test
    void migrationsApplyToFreshDatabaseAndEnforceCurrentSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            try (Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(executedChangeSetCount(connection)).isEqualTo(29);
                assertThat(applicationTables(connection))
                        .containsExactly(
                                "DELIVERIES",
                                "EMAIL_VERIFICATION_CODES",
                                "GARMENT_CATEGORIES",
                                "GARMENT_PRODUCTS",
                                "GARMENT_PRODUCT_VARIANTS",
                                "INVENTORY_MATERIALS",
                                "MATERIAL_SUPPLIES",
                                "NOTIFICATIONS",
                                "ORDERS",
                                "ORDER_INVOICES",
                                "ORDER_ITEMS",
                                "ORDER_PAYMENT_RECORDS",
                                "ORDER_STATUS_HISTORY",
                                "PASSWORD_RESET_TOKENS",
                                "PRODUCTION_TASKS",
                                "PRODUCTION_TASK_DETAILS",
                                "PRODUCTION_TASK_MATERIAL_REQUIREMENTS",
                                "PRODUCTION_TASK_MATERIAL_USAGE",
                                "QUOTATIONS",
                                "QUOTATION_ITEMS",
                                "SUPPLIER_PROFILES",
                                "USERS");
                assertInventoryMaterialStatusConstraint(connection);
                assertCurrentSupplyAndProductStatusConstraints(connection);
            }
        }
    }



    @Test
    void orderSnapshotMigrationBackfillsLegacyNameAndImageTogether() throws Exception {
        String url = "jdbc:h2:mem:legacy_order_snapshot;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(13, new Contexts(), new LabelExpression());

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                            VALUES (7901, 'legacy-order@example.com', 'not-used', 'Legacy Order Customer', 'CUSTOMER', TRUE)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO garment_categories (id, name, status)
                            VALUES (7902, 'Legacy Snapshot Category', 'ACTIVE')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO garment_products (id, category_id, name, image_url, status)
                            VALUES (7903, 7902, 'Original Legacy Product', '/products/original-legacy.jpg', 'ACTIVE')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO garment_product_variants (id, product_id, size, color, price, status)
                            VALUES (7904, 7903, 'M', 'Blue', 25.00, 'AVAILABLE')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO orders (id, customer_id, order_number, status)
                            VALUES (7905, 7901, 'LEGACY-SNAPSHOT-ORDER', 'PENDING')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO order_items
                                (id, order_id, product_id, variant_id, quantity, selected_size, selected_color, unit_price_snapshot)
                            VALUES (7906, 7905, 7903, 7904, 1, 'M', 'Blue', 25.00)
                            """);
                }

                liquibase.update(1, new Contexts(), new LabelExpression());

                assertThat(queryString(connection,
                        "SELECT product_name_snapshot FROM order_items WHERE id = 7906"))
                        .isEqualTo("Original Legacy Product");
                assertThat(queryString(connection,
                        "SELECT product_image_url_snapshot FROM order_items WHERE id = 7906"))
                        .isEqualTo("/products/original-legacy.jpg");
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM order_items WHERE id = 7906 AND product_id = 7903 AND variant_id = 7904"))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void inventoryLegacyCleanupDeletesUnusedRowsAndPreservesProductionHistory() throws Exception {
        String url = "jdbc:h2:mem:legacy_inventory_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(22, new Contexts(), new LabelExpression());

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                            VALUES (8001, 'legacy-production@example.com', 'not-used', 'Legacy Customer', 'CUSTOMER', TRUE)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO orders (id, customer_id, order_number, status)
                            VALUES (8002, 8001, 'LEGACY-INVENTORY-ORDER', 'IN_PRODUCTION')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO production_tasks (id, task_number, order_id, status, started_at)
                            VALUES (8003, 'LEGACY-INVENTORY-TASK', 8002, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))
                            """);
                    statement.executeUpdate("""
                            INSERT INTO inventory_materials
                                (id, material_code, material_name, material_type, unit_of_measure, status)
                            VALUES
                                (8004, 'LEGACY-INV-UNUSED', 'Unused legacy inventory', 'FABRIC', 'm', 'DISCONTINUED'),
                                (8005, 'LEGACY-INV-USED', 'Referenced legacy inventory', 'FABRIC', 'm', 'DISCONTINUED')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO production_task_material_requirements
                                (production_task_id, inventory_material_id, required_quantity)
                            VALUES (8003, 8005, 1.000)
                            """);
                }

                liquibase.update(1, new Contexts(), new LabelExpression());

                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM inventory_materials WHERE id = 8004")).isZero();
                assertThat(queryString(connection,
                        "SELECT status FROM inventory_materials WHERE id = 8005")).isEqualTo("INACTIVE");
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM production_task_material_requirements WHERE production_task_id = 8003 AND inventory_material_id = 8005"))
                        .isEqualTo(1);
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM production_tasks WHERE id = 8003")).isEqualTo(1);
            }
        }
    }

    @Test
    void materialSupplyLegacyCleanupDeletesOnlyUnreferencedDiscontinuedRows() throws Exception {
        String url = "jdbc:h2:mem:legacy_supply_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(10, new Contexts(), new LabelExpression());

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                            VALUES (8101, 'legacy-supply@example.com', 'not-used', 'Legacy Supplier', 'SUPPLIER', TRUE)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO supplier_profiles (id, user_id, business_name, contact_phone, address)
                            VALUES (8201, 8101, 'Legacy Supply Co', '0110000000', 'Colombo')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO material_supplies
                                (id, supplier_id, material_code, material_name, quantity, unit_of_measure,
                                 unit_price, delivery_lead_time_days, status)
                            VALUES
                                (8301, 8201, 'LEGACY-UNUSED', 'Unused legacy supply', 1, 'm', 1, 1, 'DISCONTINUED'),
                                (8302, 8201, 'LEGACY-USED', 'Referenced legacy supply', 1, 'm', 1, 1, 'DISCONTINUED')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO inventory_materials
                                (id, source_material_supply_id, material_code, material_name, material_type,
                                 unit_of_measure, current_quantity, low_stock_threshold, status)
                            VALUES (8401, 8302, 'LEGACY-INV', 'Legacy inventory', 'FABRIC', 'm', 1, 0, 'ACTIVE')
                            """);
                }

                liquibase.update(1, new Contexts(), new LabelExpression());

                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM material_supplies WHERE id = 8301")).isZero();
                assertThat(queryString(connection,
                        "SELECT status FROM material_supplies WHERE id = 8302")).isEqualTo("INACTIVE");
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM inventory_materials WHERE id = 8401 AND source_material_supply_id = 8302"))
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void productLegacyCleanupDeletesUnusedRowsAndPreservesOrderAndQuotationHistory() throws Exception {
        String url = "jdbc:h2:mem:legacy_product_cleanup;MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(16, new Contexts(), new LabelExpression());

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                            VALUES
                                (8501, 'legacy-customer@example.com', 'not-used', 'Legacy Customer', 'CUSTOMER', TRUE),
                                (8502, 'legacy-sales@example.com', 'not-used', 'Legacy Sales', 'SALES_OFFICER', TRUE)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO garment_categories (id, name, status)
                            VALUES (8601, 'Legacy Products', 'ACTIVE')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO garment_products (id, category_id, name, status)
                            VALUES
                                (8701, 8601, 'Unused legacy product', 'DISCONTINUED'),
                                (8702, 8601, 'Ordered legacy product', 'DISCONTINUED'),
                                (8703, 8601, 'Quoted legacy product', 'DISCONTINUED')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO garment_product_variants (id, product_id, size, color, price, status)
                            VALUES
                                (8801, 8701, 'M', 'Grey', 10, 'DISCONTINUED'),
                                (8802, 8702, 'M', 'Blue', 20, 'DISCONTINUED'),
                                (8803, 8703, 'L', 'White', 30, 'DISCONTINUED')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO orders (id, customer_id, order_number, status)
                            VALUES (8901, 8501, 'LEGACY-ORDER', 'PENDING')
                            """);
                    statement.executeUpdate("""
                            INSERT INTO order_items
                                (id, order_id, product_id, variant_id, quantity, selected_size, selected_color,
                                 unit_price_snapshot, product_name_snapshot, product_image_url_snapshot)
                            VALUES (8911, 8901, 8702, 8802, 1, 'M', 'Blue', 20,
                                    'Ordered legacy product', NULL)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO quotations (id, quotation_number, customer_id, issued_by_user_id)
                            VALUES (8921, 'LEGACY-QUOTE', 8501, 8502)
                            """);
                    statement.executeUpdate("""
                            INSERT INTO quotation_items
                                (id, quotation_id, product_id, variant_id, product_name_snapshot, quantity,
                                 selected_size, selected_color, unit_price_snapshot)
                            VALUES (8931, 8921, 8703, 8803, 'Quoted legacy product', 1, 'L', 'White', 30)
                            """);
                }

                liquibase.update(1, new Contexts(), new LabelExpression());

                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM garment_products WHERE id = 8701")).isZero();
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM garment_product_variants WHERE id = 8801")).isZero();
                assertThat(queryString(connection,
                        "SELECT status FROM garment_products WHERE id = 8702")).isEqualTo("INACTIVE");
                assertThat(queryString(connection,
                        "SELECT status FROM garment_product_variants WHERE id = 8802")).isEqualTo("UNAVAILABLE");
                assertThat(queryString(connection,
                        "SELECT status FROM garment_products WHERE id = 8703")).isEqualTo("INACTIVE");
                assertThat(queryString(connection,
                        "SELECT status FROM garment_product_variants WHERE id = 8803")).isEqualTo("UNAVAILABLE");
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM order_items WHERE id = 8911 AND product_id = 8702")).isEqualTo(1);
                assertThat(queryCount(connection,
                        "SELECT COUNT(*) FROM quotation_items WHERE id = 8931 AND product_id = 8703")).isEqualTo(1);
            }
        }
    }

    private void assertInventoryMaterialStatusConstraint(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO inventory_materials
                        (material_code, material_name, material_type, unit_of_measure, status)
                    VALUES ('TEST-ACTIVE', 'Test Active', 'FABRIC', 'm', 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO inventory_materials
                        (material_code, material_name, material_type, unit_of_measure, status)
                    VALUES ('TEST-INACTIVE', 'Test Inactive', 'FABRIC', 'm', 'INACTIVE')
                    """);
        }

        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO inventory_materials
                            (material_code, material_name, material_type, unit_of_measure, status)
                        VALUES ('TEST-DISCONTINUED', 'Test Discontinued', 'FABRIC', 'm', 'DISCONTINUED')
                        """);
            }
        }).isInstanceOf(SQLException.class);
    }

    private void assertCurrentSupplyAndProductStatusConstraints(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                    VALUES (9101, 'migration-supplier@example.com', 'not-used', 'Migration Supplier', 'SUPPLIER', TRUE)
                    """);
            statement.executeUpdate("""
                    INSERT INTO supplier_profiles (id, user_id, business_name, contact_phone, address)
                    VALUES (9201, 9101, 'Migration Textiles', '0110000000', 'Colombo')
                    """);
            statement.executeUpdate("""
                    INSERT INTO material_supplies
                        (id, supplier_id, material_code, material_name, quantity, unit_of_measure,
                         unit_price, delivery_lead_time_days, status)
                    VALUES (9301, 9201, 'MS-ACTIVE', 'Active Supply', 10, 'm', 12.50, 2, 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO material_supplies
                        (id, supplier_id, material_code, material_name, quantity, unit_of_measure,
                         unit_price, delivery_lead_time_days, status)
                    VALUES (9302, 9201, 'MS-INACTIVE', 'Inactive Supply', 10, 'm', 12.50, 2, 'INACTIVE')
                    """);

            statement.executeUpdate("""
                    INSERT INTO garment_categories (id, name, status)
                    VALUES (9401, 'Migration Category', 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO garment_products (id, category_id, name, status)
                    VALUES (9501, 9401, 'Active Product', 'ACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO garment_products (id, category_id, name, status)
                    VALUES (9502, 9401, 'Inactive Product', 'INACTIVE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO garment_product_variants
                        (id, product_id, size, color, price, status)
                    VALUES (9601, 9501, 'M', 'Blue', 99.00, 'AVAILABLE')
                    """);
            statement.executeUpdate("""
                    INSERT INTO garment_product_variants
                        (id, product_id, size, color, price, status)
                    VALUES (9602, 9502, 'L', 'Black', 109.00, 'UNAVAILABLE')
                    """);
        }

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO material_supplies
                    (id, supplier_id, material_code, material_name, quantity, unit_of_measure,
                     unit_price, delivery_lead_time_days, status)
                VALUES (9303, 9201, 'MS-DISCONTINUED', 'Old Supply', 10, 'm', 12.50, 2, 'DISCONTINUED')
                """))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO garment_products (id, category_id, name, status)
                VALUES (9503, 9401, 'Old Product', 'DISCONTINUED')
                """))
                .isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO garment_product_variants
                    (id, product_id, size, color, price, status)
                VALUES (9603, 9501, 'XL', 'Grey', 119.00, 'DISCONTINUED')
                """))
                .isInstanceOf(SQLException.class);
    }

    private void executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long queryCount(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private long executedChangeSetCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private java.util.List<String> applicationTables(Connection connection) throws Exception {
        String query = """
                SELECT table_name
                FROM information_schema.tables
                WHERE LOWER(table_schema) = 'public'
                  AND LOWER(table_name) NOT IN ('databasechangelog', 'databasechangeloglock')
                ORDER BY table_name
                """;
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            java.util.List<String> tables = new java.util.ArrayList<>();
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
            return tables;
        }
    }
}

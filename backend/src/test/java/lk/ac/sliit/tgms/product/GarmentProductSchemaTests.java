package lk.ac.sliit.tgms.product;

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

class GarmentProductSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void catalogUsesStableGeneratedIdsAndExactDecimalPrices() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long categoryId = insertAndReturnId(
                    connection,
                    "INSERT INTO garment_categories (name) VALUES (?)",
                    "T-Shirts");
            long productId = insertAndReturnId(
                    connection,
                    "INSERT INTO garment_products (category_id, name, image_url) VALUES (?, ?, ?)",
                    categoryId,
                    "Classic Crew Neck",
                    "/products/demo/colombo-classic-polo.jpg");
            long firstVariantId = insertAndReturnId(
                    connection,
                    """
                    INSERT INTO garment_product_variants (product_id, size, color, price)
                    VALUES (?, ?, ?, ?)
                    """,
                    productId,
                    "M",
                    "Navy",
                    new BigDecimal("2499.90"));
            long secondVariantId = insertAndReturnId(
                    connection,
                    """
                    INSERT INTO garment_product_variants (product_id, size, color, price, status)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    productId,
                    "L",
                    "Navy",
                    new BigDecimal("2499.90"),
                    VariantStatus.UNAVAILABLE.name());

            assertThat(categoryId).isPositive();
            assertThat(productId).isPositive();
            assertThat(firstVariantId).isPositive().isNotEqualTo(secondVariantId);
            assertThat(readPrice(connection, firstVariantId))
                    .isEqualByComparingTo("2499.90");
            assertThat(readProductImageUrl(connection, productId))
                    .isEqualTo("/products/demo/colombo-classic-polo.jpg");
        }
    }

    @Test
    void catalogRejectsInvalidRelationshipsValuesAndDuplicateVariants() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long categoryId = insertAndReturnId(
                    connection,
                    "INSERT INTO garment_categories (name) VALUES (?)",
                    "Formal Wear");
            long productId = insertAndReturnId(
                    connection,
                    "INSERT INTO garment_products (category_id, name) VALUES (?, ?)",
                    categoryId,
                    "Oxford Shirt");
            insertAndReturnId(
                    connection,
                    """
                    INSERT INTO garment_product_variants (product_id, size, color, price)
                    VALUES (?, ?, ?, ?)
                    """,
                    productId,
                    "S",
                    "White",
                    new BigDecimal("3990.00"));

            assertThatThrownBy(() -> execute(
                            connection,
                            """
                            INSERT INTO garment_product_variants (product_id, size, color, price)
                            VALUES (?, ?, ?, ?)
                            """,
                            productId,
                            "S",
                            "White",
                            new BigDecimal("4500.00")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            """
                            INSERT INTO garment_product_variants (product_id, size, color, price)
                            VALUES (?, ?, ?, ?)
                            """,
                            productId,
                            "M",
                            "White",
                            BigDecimal.ZERO))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "INSERT INTO garment_products (category_id, name) VALUES (?, ?)",
                            Long.MAX_VALUE,
                            "Orphan Product"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            """
                            INSERT INTO garment_product_variants
                                (product_id, size, color, price, status)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                            productId,
                            "XL",
                            "White",
                            new BigDecimal("3990.00"),
                            "UNKNOWN"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "garment_product_schema_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        Liquibase liquibase =
                new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
        liquibase.update(new Contexts(), new LabelExpression());
        return new MigratedDatabase(connection, liquibase);
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

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private BigDecimal readPrice(Connection connection, long variantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT price FROM garment_product_variants WHERE id = ?")) {
            statement.setLong(1, variantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBigDecimal("price");
            }
        }
    }

    private String readProductImageUrl(Connection connection, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT image_url FROM garment_products WHERE id = ?")) {
            statement.setLong(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("image_url");
            }
        }
    }

    private record MigratedDatabase(Connection connection, Liquibase liquibase)
            implements AutoCloseable {

        @Override
        public void close() throws Exception {
            liquibase.close();
        }
    }
}

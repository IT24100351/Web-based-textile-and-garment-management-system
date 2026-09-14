package lk.ac.sliit.tgms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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

class CustomerOrderItemSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void orderCanContainMultipleProductVariantItemsWithStableSnapshots() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "item-customer@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-ITEMS-001");
            ProductFixture shirt = insertProduct(
                    connection,
                    "Shirts",
                    "Classic Shirt",
                    "M",
                    "Blue",
                    new BigDecimal("2490.00"));
            ProductFixture trouser = insertProduct(
                    connection,
                    "Trousers",
                    "Formal Trouser",
                    "32",
                    "Black",
                    new BigDecimal("3990.00"));

            long firstItemId = insertOrderItem(
                    connection,
                    orderId,
                    shirt.productId(),
                    shirt.variantId(),
                    2,
                    shirt.size(),
                    shirt.color(),
                    shirt.price());
            long secondItemId = insertOrderItem(
                    connection,
                    orderId,
                    trouser.productId(),
                    trouser.variantId(),
                    1,
                    trouser.size(),
                    trouser.color(),
                    trouser.price());

            assertThat(firstItemId).isPositive().isNotEqualTo(secondItemId);
            assertThat(countOrderItems(connection, orderId)).isEqualTo(2);

            OrderItemRow first = readOrderItem(connection, firstItemId);
            assertThat(first.orderId()).isEqualTo(orderId);
            assertThat(first.productId()).isEqualTo(shirt.productId());
            assertThat(first.variantId()).isEqualTo(shirt.variantId());
            assertThat(first.quantity()).isEqualTo(2);
            assertThat(first.selectedSize()).isEqualTo("M");
            assertThat(first.selectedColor()).isEqualTo("Blue");
            assertThat(first.unitPriceSnapshot()).isEqualByComparingTo("2490.00");
            assertThat(first.createdAt()).isNotNull();
            assertThat(first.updatedAt()).isNotNull();
        }
    }

    @Test
    void brokenReferencesAndInvalidLineValuesAreRejected() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "item-integrity@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-ITEMS-002");
            ProductFixture product = insertProduct(
                    connection,
                    "Jackets",
                    "Light Jacket",
                    "L",
                    "Grey",
                    new BigDecimal("5500.00"));

            ProductFixture otherProduct = insertProduct(
                    connection,
                    "Hoodies",
                    "Zip Hoodie",
                    "M",
                    "Black",
                    new BigDecimal("4800.00"));

            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            Long.MAX_VALUE,
                            product.productId(),
                            product.variantId(),
                            1,
                            product.size(),
                            product.color(),
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            Long.MAX_VALUE,
                            product.variantId(),
                            1,
                            product.size(),
                            product.color(),
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            Long.MAX_VALUE,
                            1,
                            product.size(),
                            product.color(),
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            otherProduct.variantId(),
                            1,
                            otherProduct.size(),
                            otherProduct.color(),
                            otherProduct.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            product.variantId(),
                            0,
                            product.size(),
                            product.color(),
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            product.variantId(),
                            -1,
                            product.size(),
                            product.color(),
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            product.variantId(),
                            1,
                            "   ",
                            product.color(),
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            product.variantId(),
                            1,
                            product.size(),
                            "   ",
                            product.price()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOrderItem(
                            connection,
                            orderId,
                            product.productId(),
                            product.variantId(),
                            1,
                            product.size(),
                            product.color(),
                            BigDecimal.ZERO))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void catalogEditsDoNotRewriteHistoricalOrderItemSnapshots() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "item-history@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-ITEMS-003");
            ProductFixture product = insertProduct(
                    connection,
                    "T-Shirts",
                    "Logo Tee",
                    "S",
                    "White",
                    new BigDecimal("1800.00"));
            long itemId = insertOrderItem(
                    connection,
                    orderId,
                    product.productId(),
                    product.variantId(),
                    3,
                    product.size(),
                    product.color(),
                    product.price());

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    UPDATE garment_product_variants
                    SET size = ?, color = ?, price = ?, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ?
                    """)) {
                statement.setString(1, "M");
                statement.setString(2, "Navy");
                statement.setBigDecimal(3, new BigDecimal("2100.00"));
                statement.setLong(4, product.variantId());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }

            OrderItemRow historical = readOrderItem(connection, itemId);
            assertThat(historical.selectedSize()).isEqualTo("S");
            assertThat(historical.selectedColor()).isEqualTo("White");
            assertThat(historical.unitPriceSnapshot()).isEqualByComparingTo("1800.00");
        }
    }

    @Test
    void referencedOrderAndCatalogRowsCannotBeDeletedWhileOrderHistoryExists() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long customerId = insertUser(connection, "item-history-fk@example.com");
            long orderId = insertOrder(connection, customerId, "ORD-ITEMS-004");
            ProductFixture product = insertProduct(
                    connection,
                    "Shorts",
                    "Training Shorts",
                    "L",
                    "Black",
                    new BigDecimal("2200.00"));
            insertOrderItem(
                    connection,
                    orderId,
                    product.productId(),
                    product.variantId(),
                    1,
                    product.size(),
                    product.color(),
                    product.price());

            assertThatThrownBy(() -> execute(connection, "DELETE FROM orders WHERE id = ?", orderId))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "DELETE FROM garment_product_variants WHERE id = ?",
                            product.variantId()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> execute(
                            connection,
                            "DELETE FROM garment_products WHERE id = ?",
                            product.productId()))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "customer_order_item_schema_"
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

    private long insertUser(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'CUSTOMER')
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, "$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            statement.setString(3, "Order Item Customer");
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertOrder(Connection connection, long customerId, String orderNumber)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders (customer_id, order_number) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, customerId);
            statement.setString(2, orderNumber);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private ProductFixture insertProduct(
            Connection connection,
            String categoryName,
            String productName,
            String size,
            String color,
            BigDecimal price)
            throws SQLException {
        long categoryId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO garment_categories (name) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, categoryName);
            statement.executeUpdate();
            categoryId = generatedId(statement);
        }

        long productId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO garment_products (category_id, name) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, categoryId);
            statement.setString(2, productName);
            statement.executeUpdate();
            productId = generatedId(statement);
        }

        long variantId;
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO garment_product_variants (product_id, size, color, price)
                VALUES (?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, productId);
            statement.setString(2, size);
            statement.setString(3, color);
            statement.setBigDecimal(4, price);
            statement.executeUpdate();
            variantId = generatedId(statement);
        }

        return new ProductFixture(productId, variantId, size, color, price);
    }

    private long insertOrderItem(
            Connection connection,
            long orderId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    variant_id,
                    quantity,
                    selected_size,
                    selected_color,
                    unit_price_snapshot
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, orderId);
            statement.setLong(2, productId);
            statement.setLong(3, variantId);
            statement.setInt(4, quantity);
            statement.setString(5, selectedSize);
            statement.setString(6, selectedColor);
            statement.setBigDecimal(7, unitPriceSnapshot);
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

    private int countOrderItems(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?")) {
            statement.setLong(1, orderId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private OrderItemRow readOrderItem(Connection connection, long itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT order_id,
                       product_id,
                       variant_id,
                       quantity,
                       selected_size,
                       selected_color,
                       unit_price_snapshot,
                       created_at,
                       updated_at
                FROM order_items
                WHERE id = ?
                """)) {
            statement.setLong(1, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                return new OrderItemRow(
                        resultSet.getLong("order_id"),
                        resultSet.getLong("product_id"),
                        resultSet.getLong("variant_id"),
                        resultSet.getInt("quantity"),
                        resultSet.getString("selected_size"),
                        resultSet.getString("selected_color"),
                        resultSet.getBigDecimal("unit_price_snapshot"),
                        createdAt == null ? null : createdAt.toInstant(),
                        updatedAt == null ? null : updatedAt.toInstant());
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

    private record ProductFixture(
            long productId,
            long variantId,
            String size,
            String color,
            BigDecimal price) {}

    private record OrderItemRow(
            long orderId,
            long productId,
            long variantId,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot,
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

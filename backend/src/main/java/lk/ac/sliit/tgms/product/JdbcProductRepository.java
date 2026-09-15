package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final RowMapper<ProductCategory> CATEGORY_MAPPER = (resultSet, rowNumber) ->
            new ProductCategory(
                    resultSet.getLong("id"),
                    resultSet.getString("name"),
                    resultSet.getString("description"),
                    CategoryStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());

    private static final String PRODUCT_DETAILS_SELECT = """
            SELECT p.id AS product_id,
                   p.category_id,
                   p.name AS product_name,
                   p.description AS product_description,
                   p.image_url AS product_image_url,
                   p.status AS product_status,
                   p.created_at AS product_created_at,
                   p.updated_at AS product_updated_at,
                   c.id AS category_id_value,
                   c.name AS category_name,
                   c.description AS category_description,
                   c.status AS category_status,
                   c.created_at AS category_created_at,
                   c.updated_at AS category_updated_at,
                   v.id AS variant_id,
                   v.size AS variant_size,
                   v.color AS variant_color,
                   v.price AS variant_price,
                   v.status AS variant_status,
                   v.created_at AS variant_created_at,
                   v.updated_at AS variant_updated_at
            FROM garment_products p
            JOIN garment_categories c ON c.id = p.category_id
            JOIN garment_product_variants v ON v.product_id = p.id
            """;

    private static final String PRODUCT_DETAILS_QUERY = PRODUCT_DETAILS_SELECT + """
            WHERE p.id = ?
            ORDER BY v.id
            """;

    private static final String PUBLIC_CATALOG_QUERY_BASE = PRODUCT_DETAILS_SELECT + """
            WHERE c.status = 'ACTIVE'
              AND p.status = 'ACTIVE'
              AND v.status = 'AVAILABLE'
            """;

    private static final String PUBLIC_CATALOG_ORDER = """
            ORDER BY c.name, p.name, p.id, v.id
            """;

    private static final String PUBLIC_CATALOG_PRODUCT_QUERY = PUBLIC_CATALOG_QUERY_BASE + """
             AND p.id = ?
            """ + PUBLIC_CATALOG_ORDER;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProductCategory> findCategoryByName(String categoryName) {
        return jdbcTemplate.query(
                        """
                        SELECT id, name, description, status, created_at, updated_at
                        FROM garment_categories
                        WHERE LOWER(name) = LOWER(?)
                        """,
                        CATEGORY_MAPPER,
                        categoryName)
                .stream()
                .findFirst();
    }

    @Override
    public long createCategory(String categoryName) {
        return insertAndReturnId(
                "INSERT INTO garment_categories (name) VALUES (?)",
                statement -> statement.setString(1, categoryName));
    }

    @Override
    public long createProduct(
            long categoryId, String productName, String description, String imageUrl) {
        return insertAndReturnId(
                """
                INSERT INTO garment_products (category_id, name, description, image_url, status)
                VALUES (?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setLong(1, categoryId);
                    statement.setString(2, productName);
                    statement.setString(3, description);
                    statement.setString(4, imageUrl);
                    statement.setString(5, ProductStatus.ACTIVE.name());
                });
    }

    @Override
    public long createVariant(
            long productId,
            String size,
            String color,
            BigDecimal price,
            VariantStatus status) {
        return insertAndReturnId(
                """
                INSERT INTO garment_product_variants
                    (product_id, size, color, price, status)
                VALUES (?, ?, ?, ?, ?)
                """,
                statement -> {
                    statement.setLong(1, productId);
                    statement.setString(2, size);
                    statement.setString(3, color);
                    statement.setBigDecimal(4, price);
                    statement.setString(5, status.name());
                });
    }

    @Override
    public int updateProduct(
            long productId,
            long categoryId,
            String productName,
            String description,
            String imageUrl) {
        return jdbcTemplate.update(
                """
                UPDATE garment_products
                SET category_id = ?, name = ?, description = ?, image_url = ?,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                categoryId,
                productName,
                description,
                imageUrl,
                productId);
    }

    @Override
    public int updateVariant(
            long productId,
            long variantId,
            String size,
            String color,
            BigDecimal price,
            VariantStatus status) {
        return jdbcTemplate.update(
                """
                UPDATE garment_product_variants
                SET size = ?, color = ?, price = ?, status = ?,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND product_id = ?
                """,
                size,
                color,
                price,
                status.name(),
                variantId,
                productId);
    }

    @Override
    public int discontinueProduct(long productId) {
        return jdbcTemplate.update(
                """
                UPDATE garment_products
                SET status = 'DISCONTINUED', updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status <> 'DISCONTINUED'
                """,
                productId);
    }

    @Override
    public int discontinueVariants(long productId) {
        return jdbcTemplate.update(
                """
                UPDATE garment_product_variants
                SET status = 'DISCONTINUED', updated_at = CURRENT_TIMESTAMP(6)
                WHERE product_id = ? AND status <> 'DISCONTINUED'
                """,
                productId);
    }

    @Override
    public Optional<GarmentProductDetails> findProductById(long productId) {
        return jdbcTemplate.query(PRODUCT_DETAILS_QUERY, resultSet -> {
            if (!resultSet.next()) {
                return Optional.empty();
            }

            ProductCategory category = mapCategory(resultSet);
            GarmentProduct product = mapProduct(resultSet);
            var variants = new ArrayList<ProductVariant>();
            do {
                variants.add(mapVariant(resultSet));
            } while (resultSet.next());

            return Optional.of(new GarmentProductDetails(category, product, variants));
        }, productId);
    }

    @Override
    public Optional<GarmentProductDetails> findPublicCatalogProductById(long productId) {
        return jdbcTemplate.query(
                PUBLIC_CATALOG_PRODUCT_QUERY,
                resultSet -> {
                    return mapPublicCatalogProducts(resultSet).stream().findFirst();
                },
                productId);
    }

    @Override
    public List<GarmentProductDetails> findPublicCatalog(ProductCatalogFilter filter) {
        var query = new StringBuilder(PUBLIC_CATALOG_QUERY_BASE);
        var parameters = new ArrayList<Object>();
        appendTextFilter(query, parameters, filter.search(),
                " AND LOCATE(LOWER(?), LOWER(p.name)) > 0\n");
        appendTextFilter(query, parameters, filter.category(),
                " AND LOWER(c.name) = LOWER(?)\n");
        appendTextFilter(query, parameters, filter.size(),
                " AND LOWER(v.size) = LOWER(?)\n");
        appendTextFilter(query, parameters, filter.color(),
                " AND LOWER(v.color) = LOWER(?)\n");
        if (filter.availability() != null) {
            query.append(" AND v.status = ?\n");
            parameters.add(filter.availability().name());
        }
        query.append(PUBLIC_CATALOG_ORDER);

        return jdbcTemplate.query(
                query.toString(), this::mapPublicCatalogProducts, parameters.toArray());
    }

    private void appendTextFilter(
            StringBuilder query,
            List<Object> parameters,
            String value,
            String predicate) {
        if (value != null) {
            query.append(predicate);
            parameters.add(value);
        }
    }

    private List<GarmentProductDetails> mapPublicCatalogProducts(ResultSet resultSet)
            throws SQLException {
        var products = new ArrayList<GarmentProductDetails>();
        ProductCategory category = null;
        GarmentProduct product = null;
        var variants = new ArrayList<ProductVariant>();

        while (resultSet.next()) {
            long productId = resultSet.getLong("product_id");
            if (product != null && product.id() != productId) {
                products.add(new GarmentProductDetails(category, product, variants));
                variants = new ArrayList<>();
            }
            if (product == null || product.id() != productId) {
                category = mapCategory(resultSet);
                product = mapProduct(resultSet);
            }
            variants.add(mapVariant(resultSet));
        }

        if (product != null) {
            products.add(new GarmentProductDetails(category, product, variants));
        }
        return List.copyOf(products);
    }

    private ProductCategory mapCategory(ResultSet resultSet) throws SQLException {
        return new ProductCategory(
                resultSet.getLong("category_id_value"),
                resultSet.getString("category_name"),
                resultSet.getString("category_description"),
                CategoryStatus.valueOf(resultSet.getString("category_status")),
                instant(resultSet, "category_created_at"),
                instant(resultSet, "category_updated_at"));
    }

    private GarmentProduct mapProduct(ResultSet resultSet) throws SQLException {
        return new GarmentProduct(
                resultSet.getLong("product_id"),
                resultSet.getLong("category_id"),
                resultSet.getString("product_name"),
                resultSet.getString("product_description"),
                resultSet.getString("product_image_url"),
                ProductStatus.valueOf(resultSet.getString("product_status")),
                instant(resultSet, "product_created_at"),
                instant(resultSet, "product_updated_at"));
    }

    private ProductVariant mapVariant(ResultSet resultSet) throws SQLException {
        return new ProductVariant(
                resultSet.getLong("variant_id"),
                resultSet.getLong("product_id"),
                resultSet.getString("variant_size"),
                resultSet.getString("variant_color"),
                resultSet.getBigDecimal("variant_price"),
                VariantStatus.valueOf(resultSet.getString("variant_status")),
                instant(resultSet, "variant_created_at"),
                instant(resultSet, "variant_updated_at"));
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp.toInstant();
    }

    private long insertAndReturnId(String sql, StatementBinder binder) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(sql, new String[] {"id"});
                    binder.bind(statement);
                    return statement;
                },
                keyHolder);
        return generatedId(keyHolder);
    }

    static long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        throw new IllegalStateException("Database did not return a product ID.");
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}

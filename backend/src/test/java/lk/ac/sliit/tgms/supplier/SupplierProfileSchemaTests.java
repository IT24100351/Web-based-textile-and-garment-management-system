package lk.ac.sliit.tgms.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

class SupplierProfileSchemaTests {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.xml";

    @Test
    void profileHasStableIdRequiredContactDataAndAuthenticatedAccountLink()
            throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long userId = insertSupplierUser(connection, "supplier-one@example.com");
            long supplierId = insertAndReturnId(
                    connection,
                    """
                    INSERT INTO supplier_profiles
                        (user_id, business_name, contact_phone, address)
                    VALUES (?, ?, ?, ?)
                    """,
                    userId,
                    "Lanka Textile Supplies",
                    "+94 77 123 4567",
                    "18 Industrial Estate, Colombo");

            SupplierProfile profile = readProfile(connection, supplierId);
            assertThat(profile.id()).isPositive();
            assertThat(profile.userId()).isEqualTo(userId);
            assertThat(profile.businessName()).isEqualTo("Lanka Textile Supplies");
            assertThat(profile.contactPhone()).isEqualTo("+94 77 123 4567");
            assertThat(profile.address()).isEqualTo("18 Industrial Estate, Colombo");
            assertThat(profile.createdAt()).isNotNull();
            assertThat(profile.updatedAt()).isNotNull();

            assertThat(profileColumns(connection))
                    .containsExactly(
                            "ADDRESS",
                            "BUSINESS_NAME",
                            "CONTACT_PHONE",
                            "CREATED_AT",
                            "ID",
                            "UPDATED_AT",
                            "USER_ID")
                    .doesNotContain("EMAIL", "PASSWORD_HASH", "ROLE", "IS_ACTIVE");
        }
    }

    @Test
    void profileRejectsOrphanDuplicateAndBlankIdentityOrContactValues() throws Exception {
        try (MigratedDatabase database = migratedDatabase()) {
            Connection connection = database.connection();
            long userId = insertSupplierUser(connection, "supplier-two@example.com");
            insertAndReturnId(
                    connection,
                    """
                    INSERT INTO supplier_profiles
                        (user_id, business_name, contact_phone, address)
                    VALUES (?, ?, ?, ?)
                    """,
                    userId,
                    "Southern Fabric Traders",
                    "011 234 5678",
                    "Galle Road, Matara");

            assertThatThrownBy(() -> insertProfile(
                            connection,
                            userId,
                            "Duplicate Profile",
                            "011 000 0000",
                            "Colombo"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertProfile(
                            connection,
                            Long.MAX_VALUE,
                            "Orphan Supplier",
                            "011 000 0000",
                            "Colombo"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertProfile(
                            connection,
                            insertSupplierUser(connection, "blank-name@example.com"),
                            "   ",
                            "011 000 0000",
                            "Colombo"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertProfile(
                            connection,
                            insertSupplierUser(connection, "blank-phone@example.com"),
                            "Phone Test Supplier",
                            "   ",
                            "Colombo"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertProfile(
                            connection,
                            insertSupplierUser(connection, "blank-address@example.com"),
                            "Address Test Supplier",
                            "011 000 0000",
                            "   "))
                    .isInstanceOf(SQLException.class);
        }
    }

    private MigratedDatabase migratedDatabase() throws Exception {
        String databaseName = "supplier_profile_schema_"
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

    private long insertSupplierUser(Connection connection, String email) throws SQLException {
        return insertAndReturnId(
                connection,
                """
                INSERT INTO users (email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'SUPPLIER')
                """,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                "Supplier Account Holder");
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

    private void insertProfile(
            Connection connection,
            long userId,
            String businessName,
            String contactPhone,
            String address) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO supplier_profiles
                    (user_id, business_name, contact_phone, address)
                VALUES (?, ?, ?, ?)
                """)) {
            bind(statement, new Object[] {userId, businessName, contactPhone, address});
            statement.executeUpdate();
        }
    }

    private SupplierProfile readProfile(Connection connection, long supplierId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id, user_id, business_name, contact_phone, address,
                       created_at, updated_at
                FROM supplier_profiles
                WHERE id = ?
                """)) {
            statement.setLong(1, supplierId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new SupplierProfile(
                        resultSet.getLong("id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("business_name"),
                        resultSet.getString("contact_phone"),
                        resultSet.getString("address"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant());
            }
        }
    }

    private List<String> profileColumns(Connection connection) throws SQLException {
        String query = """
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_schema) = 'public'
                  AND LOWER(table_name) = 'supplier_profiles'
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

    private record MigratedDatabase(Connection connection, Liquibase liquibase)
            implements AutoCloseable {

        @Override
        public void close() throws Exception {
            liquibase.close();
        }
    }
}

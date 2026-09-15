package lk.ac.sliit.tgms.supplier;

import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSupplierProfileRepository implements SupplierProfileRepository {

    private static final RowMapper<SupplierProfile> PROFILE_MAPPER = (resultSet, rowNumber) ->
            new SupplierProfile(
                    resultSet.getLong("id"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("business_name"),
                    resultSet.getString("contact_phone"),
                    resultSet.getString("address"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public JdbcSupplierProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SupplierProfile> findByUserId(long userId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, business_name, contact_phone, address,
                               created_at, updated_at
                        FROM supplier_profiles
                        WHERE user_id = ?
                        """,
                        PROFILE_MAPPER,
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public long create(long userId, String businessName, String contactPhone, String address) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO supplier_profiles
                                (user_id, business_name, contact_phone, address)
                            VALUES (?, ?, ?, ?)
                            """,
                            new String[] {"id"});
                    statement.setLong(1, userId);
                    statement.setString(2, businessName);
                    statement.setString(3, contactPhone);
                    statement.setString(4, address);
                    return statement;
                },
                keyHolder);
        return generatedId(keyHolder);
    }

    @Override
    public int update(long userId, String businessName, String contactPhone, String address) {
        return jdbcTemplate.update(
                """
                UPDATE supplier_profiles
                SET business_name = ?, contact_phone = ?, address = ?,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE user_id = ?
                """,
                businessName,
                contactPhone,
                address,
                userId);
    }

    private long generatedId(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            return keys.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase("id"))
                    .map(Map.Entry::getValue)
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .findFirst()
                    .map(Number::longValue)
                    .orElseThrow(() -> new IllegalStateException(
                            "Database did not return a supplier profile ID."));
        }
        throw new IllegalStateException("Database did not return a supplier profile ID.");
    }
}

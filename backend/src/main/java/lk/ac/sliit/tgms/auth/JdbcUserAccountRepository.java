package lk.ac.sliit.tgms.auth;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserAccountRepository implements UserAccountRepository {

    private static final String SELECT_ACCOUNT = """
            SELECT id, email, password_hash, full_name, role, is_active
            FROM users
            """;

    private static final RowMapper<UserAccount> ACCOUNT_MAPPER = (resultSet, rowNumber) ->
            new UserAccount(
                    resultSet.getLong("id"),
                    resultSet.getString("email"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("full_name"),
                    UserRole.valueOf(resultSet.getString("role")),
                    resultSet.getBoolean("is_active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findByEmail(String normalizedEmail) {
        return jdbcTemplate.query(
                        SELECT_ACCOUNT + " WHERE email = ?", ACCOUNT_MAPPER, normalizedEmail)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return jdbcTemplate.query(SELECT_ACCOUNT + " WHERE id = ?", ACCOUNT_MAPPER, id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<UserAccountSecurityState> findSecurityStateById(long id) {
        return jdbcTemplate.query(
                        "SELECT id, role, is_active, session_version FROM users WHERE id = ?",
                        (resultSet, rowNumber) -> new UserAccountSecurityState(
                                resultSet.getLong("id"),
                                UserRole.valueOf(resultSet.getString("role")),
                                resultSet.getBoolean("is_active"),
                                resultSet.getInt("session_version")),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public List<UserAccount> findActiveByRole(UserRole role, String search) {
        StringBuilder sql = new StringBuilder(
                SELECT_ACCOUNT + " WHERE role = ? AND is_active = TRUE");
        List<Object> parameters = new ArrayList<>();
        parameters.add(role.name());
        if (search != null) {
            sql.append(" AND (LOCATE(LOWER(?), LOWER(full_name)) > 0 "
                    + "OR LOCATE(LOWER(?), LOWER(email)) > 0)");
            parameters.add(search);
            parameters.add(search);
        }
        sql.append(" ORDER BY full_name, id");
        return jdbcTemplate.query(sql.toString(), ACCOUNT_MAPPER, parameters.toArray());
    }

    @Override
    public UserAccount create(
            String fullName, String normalizedEmail, String passwordHash, UserRole role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            """
                            INSERT INTO users (
                                email, password_hash, full_name, role, email_verified_at
                            ) VALUES (?, ?, ?, ?, ?)
                            """,
                            new String[] {"id"});
                    statement.setString(1, normalizedEmail);
                    statement.setString(2, passwordHash);
                    statement.setString(3, fullName);
                    statement.setString(4, role.name());
                    statement.setTimestamp(
                            5,
                            role == UserRole.CUSTOMER ? null : Timestamp.from(Instant.now()));
                    return statement;
                },
                keyHolder);

        long id = generatedId(keyHolder);
        return new UserAccount(id, normalizedEmail, passwordHash, fullName, role, true);
    }

    @Override
    public UserAccount updateProfile(long userId, String fullName) {
        int updated = jdbcTemplate.update(
                "UPDATE users SET full_name = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                fullName,
                userId);
        if (updated != 1) {
            throw new IllegalStateException("Expected exactly one user profile to be updated.");
        }
        return findById(userId)
                .orElseThrow(() -> new IllegalStateException("Updated user profile could not be reloaded."));
    }

    @Override
    public void updatePasswordHash(long userId, String passwordHash) {
        int updated = jdbcTemplate.update(
                """
                UPDATE users
                SET password_hash = ?, session_version = session_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                passwordHash,
                userId);
        if (updated != 1) {
            throw new IllegalStateException("Expected exactly one user password to be updated.");
        }
    }

    @Override
    public boolean isEmailVerified(long userId) {
        Boolean verified = jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?",
                Boolean.class,
                userId);
        return Boolean.TRUE.equals(verified);
    }

    @Override
    public void markEmailVerified(long userId, Instant verifiedAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE users
                SET email_verified_at = COALESCE(email_verified_at, ?),
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?
                """,
                Timestamp.from(verifiedAt),
                userId);
        if (updated != 1) {
            throw new IllegalStateException("Expected exactly one user email to be verified.");
        }
    }

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }

        throw new IllegalStateException("Database did not return a user ID.");
    }
}

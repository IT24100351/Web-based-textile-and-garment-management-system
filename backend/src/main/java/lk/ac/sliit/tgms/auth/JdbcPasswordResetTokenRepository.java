package lk.ac.sliit.tgms.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void invalidateUnusedForUser(long userId, Instant usedAt) {
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL",
                Timestamp.from(usedAt),
                userId);
    }

    @Override
    public void create(long userId, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId,
                tokenHash,
                Timestamp.from(expiresAt));
    }

    @Override
    public Optional<Instant> findLatestCreatedAt(long userId) {
        return jdbcTemplate.query(
                        """
                        SELECT created_at
                        FROM password_reset_tokens
                        WHERE user_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNumber) -> resultSet.getTimestamp("created_at").toInstant(),
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PasswordResetToken> findByHashForUpdate(String tokenHash) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, token_hash, expires_at, used_at
                        FROM password_reset_tokens
                        WHERE token_hash = ?
                        FOR UPDATE
                        """,
                        (resultSet, rowNumber) -> new PasswordResetToken(
                                resultSet.getLong("id"),
                                resultSet.getLong("user_id"),
                                resultSet.getString("token_hash"),
                                resultSet.getTimestamp("expires_at").toInstant(),
                                resultSet.getTimestamp("used_at") == null
                                        ? null
                                        : resultSet.getTimestamp("used_at").toInstant()),
                        tokenHash)
                .stream()
                .findFirst();
    }

    @Override
    public void markUsed(long tokenId, Instant usedAt) {
        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET used_at = ? WHERE id = ? AND used_at IS NULL",
                Timestamp.from(usedAt),
                tokenId);
    }
}

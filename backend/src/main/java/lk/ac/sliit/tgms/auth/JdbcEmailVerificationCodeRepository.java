package lk.ac.sliit.tgms.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEmailVerificationCodeRepository implements EmailVerificationCodeRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcEmailVerificationCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void invalidateUnusedForUser(long userId, Instant usedAt) {
        jdbcTemplate.update(
                """
                UPDATE email_verification_codes
                SET used_at = ?
                WHERE user_id = ? AND used_at IS NULL
                """,
                Timestamp.from(usedAt),
                userId);
    }

    @Override
    public void create(
            long userId,
            String codeSalt,
            String codeHash,
            Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO email_verification_codes (
                    user_id, code_salt, code_hash, expires_at
                ) VALUES (?, ?, ?, ?)
                """,
                userId,
                codeSalt,
                codeHash,
                Timestamp.from(expiresAt));
    }

    @Override
    public Optional<EmailVerificationCode> findLatestUnusedForUserForUpdate(long userId) {
        return jdbcTemplate.query(
                        """
                        SELECT id, user_id, code_salt, code_hash, expires_at,
                               failed_attempts, used_at, created_at
                        FROM email_verification_codes
                        WHERE user_id = ? AND used_at IS NULL
                        ORDER BY created_at DESC, id DESC
                        FOR UPDATE
                        """,
                        (resultSet, rowNumber) -> new EmailVerificationCode(
                                resultSet.getLong("id"),
                                resultSet.getLong("user_id"),
                                resultSet.getString("code_salt"),
                                resultSet.getString("code_hash"),
                                resultSet.getTimestamp("expires_at").toInstant(),
                                resultSet.getInt("failed_attempts"),
                                resultSet.getTimestamp("used_at") == null
                                        ? null
                                        : resultSet.getTimestamp("used_at").toInstant(),
                                resultSet.getTimestamp("created_at").toInstant()),
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Instant> findLatestCreatedAt(long userId) {
        return jdbcTemplate.query(
                        """
                        SELECT created_at
                        FROM email_verification_codes
                        WHERE user_id = ?
                        ORDER BY created_at DESC, id DESC
                        """,
                        (resultSet, rowNumber) -> resultSet.getTimestamp("created_at").toInstant(),
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public void incrementFailedAttempts(long codeId, Instant usedAtWhenLocked) {
        jdbcTemplate.update(
                """
                UPDATE email_verification_codes
                SET failed_attempts = failed_attempts + 1,
                    used_at = COALESCE(used_at, ?)
                WHERE id = ? AND used_at IS NULL
                """,
                usedAtWhenLocked == null ? null : Timestamp.from(usedAtWhenLocked),
                codeId);
    }

    @Override
    public void markUsed(long codeId, Instant usedAt) {
        jdbcTemplate.update(
                """
                UPDATE email_verification_codes
                SET used_at = ?
                WHERE id = ? AND used_at IS NULL
                """,
                Timestamp.from(usedAt),
                codeId);
    }
}

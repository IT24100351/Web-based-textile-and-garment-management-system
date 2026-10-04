package lk.ac.sliit.tgms.auth;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository {

    void invalidateUnusedForUser(long userId, Instant usedAt);

    void create(
            long userId,
            String codeSalt,
            String codeHash,
            Instant expiresAt);

    Optional<EmailVerificationCode> findLatestUnusedForUserForUpdate(long userId);

    Optional<Instant> findLatestCreatedAt(long userId);

    void incrementFailedAttempts(long codeId, Instant usedAtWhenLocked);

    void markUsed(long codeId, Instant usedAt);
}

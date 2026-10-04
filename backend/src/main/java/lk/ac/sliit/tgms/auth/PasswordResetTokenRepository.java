package lk.ac.sliit.tgms.auth;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository {

    void invalidateUnusedForUser(long userId, Instant usedAt);

    void create(long userId, String tokenHash, Instant expiresAt);

    Optional<Instant> findLatestCreatedAt(long userId);

    Optional<PasswordResetToken> findByHashForUpdate(String tokenHash);

    void markUsed(long tokenId, Instant usedAt);
}

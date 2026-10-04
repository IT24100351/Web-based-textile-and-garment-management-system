package lk.ac.sliit.tgms.auth;

import java.time.Instant;

public record PasswordResetToken(
        long id,
        long userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt) {}

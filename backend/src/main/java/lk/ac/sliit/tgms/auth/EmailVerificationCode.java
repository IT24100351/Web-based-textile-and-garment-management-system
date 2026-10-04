package lk.ac.sliit.tgms.auth;

import java.time.Instant;

public record EmailVerificationCode(
        long id,
        long userId,
        String codeSalt,
        String codeHash,
        Instant expiresAt,
        int failedAttempts,
        Instant usedAt,
        Instant createdAt) {}

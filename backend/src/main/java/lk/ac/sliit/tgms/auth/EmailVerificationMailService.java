package lk.ac.sliit.tgms.auth;

import java.time.Instant;

public interface EmailVerificationMailService {

    void sendVerificationCode(
            String recipient,
            String fullName,
            String verificationCode,
            Instant expiresAt);
}

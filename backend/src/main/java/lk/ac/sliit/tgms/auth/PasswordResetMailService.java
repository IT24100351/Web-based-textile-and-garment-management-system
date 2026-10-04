package lk.ac.sliit.tgms.auth;

import java.time.Instant;

public interface PasswordResetMailService {

    void sendPasswordReset(String recipient, String fullName, String resetUrl, Instant expiresAt);
}

package lk.ac.sliit.tgms.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "tgms.auth.mail.smtp-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class DisabledPasswordResetMailService implements PasswordResetMailService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DisabledPasswordResetMailService.class);

    @Override
    public void sendPasswordReset(
            String recipient, String fullName, String resetUrl, Instant expiresAt) {
        // Deliberately never log the recipient or raw reset URL/token.
        LOGGER.warn("Password reset email was not sent because SMTP delivery is disabled.");
    }
}

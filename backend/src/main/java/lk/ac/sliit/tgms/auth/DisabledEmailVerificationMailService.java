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
public class DisabledEmailVerificationMailService implements EmailVerificationMailService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DisabledEmailVerificationMailService.class);

    @Override
    public void sendVerificationCode(
            String recipient,
            String fullName,
            String verificationCode,
            Instant expiresAt) {
        // Deliberately never log the recipient or raw verification code.
        LOGGER.warn("Email verification code was not sent because SMTP delivery is disabled.");
    }
}

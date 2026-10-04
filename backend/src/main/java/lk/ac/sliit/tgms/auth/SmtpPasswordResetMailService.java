package lk.ac.sliit.tgms.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "tgms.auth.mail.smtp-enabled", havingValue = "true")
public class SmtpPasswordResetMailService implements PasswordResetMailService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SmtpPasswordResetMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpPasswordResetMailService(
            JavaMailSender mailSender,
            @Value("${tgms.auth.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordReset(
            String recipient, String fullName, String resetUrl, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("Reset your LankaWear Apparel password");
        message.setText("Hello " + fullName + ",\n\n"
                + "A password reset was requested for your LankaWear Apparel account.\n"
                + "Use this single-use link before " + expiresAt + ":\n\n"
                + resetUrl + "\n\n"
                + "If you did not request this change, you can ignore this email.\n"
                + "For your security, do not share this link with anyone.\n");
        mailSender.send(message);
        LOGGER.info("SMTP accepted a password reset message for delivery.");
    }
}

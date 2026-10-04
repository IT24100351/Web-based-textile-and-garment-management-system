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
public class SmtpEmailVerificationMailService implements EmailVerificationMailService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SmtpEmailVerificationMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailVerificationMailService(
            JavaMailSender mailSender,
            @Value("${tgms.auth.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationCode(
            String recipient,
            String fullName,
            String verificationCode,
            Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject("Verify your LankaWear Apparel account");
        message.setText("Hello " + fullName + ",\n\n"
                + "Your LankaWear Apparel verification code is:\n\n"
                + verificationCode + "\n\n"
                + "Enter this code before " + expiresAt + ".\n"
                + "If you did not create this account, you can ignore this email.\n"
                + "For your security, do not share this code with anyone.\n");
        mailSender.send(message);
        LOGGER.info("SMTP accepted an email verification message for delivery.");
    }
}

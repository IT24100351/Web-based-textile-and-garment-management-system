package lk.ac.sliit.tgms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private final UserAccountRepository userAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Executor mailExecutor;
    private final Duration tokenLifetime;
    private final Duration resendInterval;
    private final String clientOrigin;

    public PasswordResetService(
            UserAccountRepository userAccountRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetMailService mailService,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            @Qualifier("passwordResetMailExecutor") Executor mailExecutor,
            @Value("${tgms.auth.reset.ttl-minutes:30}") long tokenLifetimeMinutes,
            @Value("${tgms.auth.reset.resend-seconds:60}") long resendSeconds,
            @Value("${tgms.client-origin}") String clientOrigin) {
        if (tokenLifetimeMinutes < 5 || tokenLifetimeMinutes > 120) {
            throw new IllegalStateException(
                    "PASSWORD_RESET_TTL_MINUTES must be between 5 and 120 minutes.");
        }
        if (resendSeconds < 30 || resendSeconds > 600) {
            throw new IllegalStateException(
                    "PASSWORD_RESET_RESEND_SECONDS must be between 30 and 600 seconds.");
        }
        this.userAccountRepository = userAccountRepository;
        this.tokenRepository = tokenRepository;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.mailExecutor = mailExecutor;
        this.tokenLifetime = Duration.ofMinutes(tokenLifetimeMinutes);
        this.resendInterval = Duration.ofSeconds(resendSeconds);
        this.clientOrigin = clientOrigin;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = AuthService.normalizeEmail(email);

        // Generate and hash a candidate on every request so unknown-account requests follow the
        // same cryptographic path. The API response remains identical in every case.
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        var account = userAccountRepository.findByEmail(normalizedEmail)
                .filter(UserAccount::active);

        if (account.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        boolean throttled = tokenRepository.findLatestCreatedAt(account.get().id())
                .map(createdAt -> createdAt.isAfter(now.minus(resendInterval)))
                .orElse(false);
        if (throttled) {
            return;
        }

        Instant expiresAt = now.plus(tokenLifetime);
        try {
            tokenRepository.invalidateUnusedForUser(account.get().id(), now);
            tokenRepository.create(account.get().id(), tokenHash, expiresAt);
            String resetUrl = UriComponentsBuilder.fromUriString(clientOrigin)
                    .path("/reset-password")
                    .queryParam("token", rawToken)
                    .build()
                    .toUriString();
            scheduleMailAfterCommit(account.get(), resetUrl, expiresAt);
        } catch (RuntimeException exception) {
            // Keep the same public response for known and unknown accounts. Never log the
            // recipient, raw token, reset URL, or exception message. The exception escapes this
            // transactional service so the failed token issuance is rolled back before the
            // controller converts it to the generic request response.
            LOGGER.error(
                    "Password reset processing failed for user ID {} ({}).",
                    account.get().id(),
                    exception.getClass().getSimpleName());
            throw new PasswordResetRequestProcessingException(exception);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        passwordPolicy.validate(newPassword);
        String tokenHash = hashToken(rawToken);
        Instant now = Instant.now();

        PasswordResetToken token = tokenRepository.findByHashForUpdate(tokenHash)
                .orElseThrow(InvalidPasswordResetException::new);
        if (token.usedAt() != null || !token.expiresAt().isAfter(now)) {
            throw new InvalidPasswordResetException();
        }

        UserAccount account = userAccountRepository.findById(token.userId())
                .filter(UserAccount::active)
                .orElseThrow(InvalidPasswordResetException::new);

        String passwordHash = passwordEncoder.encode(newPassword);
        userAccountRepository.updatePasswordHash(account.id(), passwordHash);
        tokenRepository.markUsed(token.id(), now);
        tokenRepository.invalidateUnusedForUser(account.id(), now);
    }

    private void scheduleMailAfterCommit(
            UserAccount account, String resetUrl, Instant expiresAt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mailExecutor.execute(() -> sendMailSafely(account, resetUrl, expiresAt));
                } catch (RuntimeException exception) {
                    LOGGER.error(
                            "Password reset mail scheduling failed for user ID {} ({}).",
                            account.id(),
                            exception.getClass().getSimpleName());
                }
            }
        });
    }

    private void sendMailSafely(UserAccount account, String resetUrl, Instant expiresAt) {
        try {
            mailService.sendPasswordReset(
                    account.email(), account.fullName(), resetUrl, expiresAt);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Password reset email delivery failed for user ID {} ({}).",
                    account.id(),
                    exception.getClass().getSimpleName());
        }
    }

    static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            throw new InvalidPasswordResetException();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }
}

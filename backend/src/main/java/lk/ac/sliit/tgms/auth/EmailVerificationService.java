package lk.ac.sliit.tgms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EmailVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final int SALT_BYTES = 16;

    private final UserAccountRepository userAccountRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final EmailVerificationMailService mailService;
    private final Executor mailExecutor;
    private final Duration codeLifetime;
    private final Duration resendInterval;
    private final int maxAttempts;

    public EmailVerificationService(
            UserAccountRepository userAccountRepository,
            EmailVerificationCodeRepository codeRepository,
            EmailVerificationMailService mailService,
            @Qualifier("passwordResetMailExecutor") Executor mailExecutor,
            @Value("${tgms.auth.verification.ttl-minutes:15}") long codeLifetimeMinutes,
            @Value("${tgms.auth.verification.resend-seconds:60}") long resendSeconds,
            @Value("${tgms.auth.verification.max-attempts:5}") int maxAttempts) {
        if (codeLifetimeMinutes < 5 || codeLifetimeMinutes > 60) {
            throw new IllegalStateException(
                    "EMAIL_VERIFICATION_TTL_MINUTES must be between 5 and 60 minutes.");
        }
        if (resendSeconds < 30 || resendSeconds > 600) {
            throw new IllegalStateException(
                    "EMAIL_VERIFICATION_RESEND_SECONDS must be between 30 and 600 seconds.");
        }
        if (maxAttempts < 3 || maxAttempts > 10) {
            throw new IllegalStateException(
                    "EMAIL_VERIFICATION_MAX_ATTEMPTS must be between 3 and 10.");
        }
        this.userAccountRepository = userAccountRepository;
        this.codeRepository = codeRepository;
        this.mailService = mailService;
        this.mailExecutor = mailExecutor;
        this.codeLifetime = Duration.ofMinutes(codeLifetimeMinutes);
        this.resendInterval = Duration.ofSeconds(resendSeconds);
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void issueForRegisteredCustomer(UserAccount account) {
        if (account.role() != UserRole.CUSTOMER || !account.active()) {
            throw new IllegalArgumentException(
                    "Email verification can only be issued for an active customer.");
        }
        issueCode(account, Instant.now());
    }

    @Transactional
    public void requestNewCode(String email) {
        String normalizedEmail = AuthService.normalizeEmail(email);

        // Perform code-generation work before the lookup so unknown and known requests follow a
        // similar path. The public response is deliberately identical for every account state.
        CodeMaterial codeMaterial = generateCodeMaterial();
        var account = userAccountRepository.findByEmail(normalizedEmail)
                .filter(UserAccount::active)
                .filter(candidate -> candidate.role() == UserRole.CUSTOMER)
                .filter(candidate -> !userAccountRepository.isEmailVerified(candidate.id()));

        if (account.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        boolean throttled = codeRepository.findLatestCreatedAt(account.get().id())
                .map(createdAt -> createdAt.isAfter(now.minus(resendInterval)))
                .orElse(false);
        if (throttled) {
            return;
        }

        issueCode(account.get(), now, codeMaterial);
    }

    @Transactional(noRollbackFor = InvalidEmailVerificationException.class)
    public void confirmCode(String email, String code) {
        String normalizedEmail = AuthService.normalizeEmail(email);
        UserAccount account = userAccountRepository.findByEmail(normalizedEmail)
                .filter(UserAccount::active)
                .filter(candidate -> candidate.role() == UserRole.CUSTOMER)
                .filter(candidate -> !userAccountRepository.isEmailVerified(candidate.id()))
                .orElseThrow(InvalidEmailVerificationException::new);

        Instant now = Instant.now();
        EmailVerificationCode storedCode = codeRepository
                .findLatestUnusedForUserForUpdate(account.id())
                .orElseThrow(InvalidEmailVerificationException::new);

        if (!storedCode.expiresAt().isAfter(now)
                || storedCode.failedAttempts() >= maxAttempts) {
            codeRepository.markUsed(storedCode.id(), now);
            throw new InvalidEmailVerificationException();
        }

        String candidateHash = hashCode(code, storedCode.codeSalt());
        boolean matches = MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.US_ASCII),
                storedCode.codeHash().getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            int failedAttempts = storedCode.failedAttempts() + 1;
            codeRepository.incrementFailedAttempts(
                    storedCode.id(),
                    failedAttempts >= maxAttempts ? now : null);
            throw new InvalidEmailVerificationException();
        }

        userAccountRepository.markEmailVerified(account.id(), now);
        codeRepository.markUsed(storedCode.id(), now);
        codeRepository.invalidateUnusedForUser(account.id(), now);
    }

    private void issueCode(UserAccount account, Instant now) {
        issueCode(account, now, generateCodeMaterial());
    }

    private void issueCode(UserAccount account, Instant now, CodeMaterial codeMaterial) {
        Instant expiresAt = now.plus(codeLifetime);
        try {
            codeRepository.invalidateUnusedForUser(account.id(), now);
            codeRepository.create(
                    account.id(),
                    codeMaterial.salt(),
                    codeMaterial.hash(),
                    expiresAt);
            scheduleMailAfterCommit(account, codeMaterial.rawCode(), expiresAt);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Email verification processing failed for user ID {} ({}).",
                    account.id(),
                    exception.getClass().getSimpleName());
            throw new EmailVerificationRequestProcessingException(exception);
        }
    }

    private void scheduleMailAfterCommit(
            UserAccount account,
            String rawCode,
            Instant expiresAt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mailExecutor.execute(() -> sendMailSafely(account, rawCode, expiresAt));
                } catch (RuntimeException exception) {
                    LOGGER.error(
                            "Email verification mail scheduling failed for user ID {} ({}).",
                            account.id(),
                            exception.getClass().getSimpleName());
                }
            }
        });
    }

    private void sendMailSafely(UserAccount account, String rawCode, Instant expiresAt) {
        try {
            mailService.sendVerificationCode(
                    account.email(), account.fullName(), rawCode, expiresAt);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Email verification delivery failed for user ID {} ({}).",
                    account.id(),
                    exception.getClass().getSimpleName());
        }
    }

    static String hashCode(String code, String salt) {
        if (code == null || !code.matches("\\d{6}")
                || salt == null || !salt.matches("[0-9a-f]{32}")) {
            throw new InvalidEmailVerificationException();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (salt + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private CodeMaterial generateCodeMaterial() {
        String rawCode = "%06d".formatted(SECURE_RANDOM.nextInt(CODE_BOUND));
        byte[] saltBytes = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        return new CodeMaterial(rawCode, salt, hashCode(rawCode, salt));
    }

    private record CodeMaterial(String rawCode, String salt, String hash) {}
}

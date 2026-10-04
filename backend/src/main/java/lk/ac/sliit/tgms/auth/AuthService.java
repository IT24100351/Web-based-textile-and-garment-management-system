package lk.ac.sliit.tgms.auth;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailVerificationService emailVerificationService;
    private final String dummyPasswordHash;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            EmailVerificationService emailVerificationService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailVerificationService = emailVerificationService;
        this.dummyPasswordHash = passwordEncoder.encode("tgms-invalid-password-placeholder");
    }

    @Transactional
    public UserAccount registerCustomer(String fullName, String email, String password) {
        UserAccount account = createAccount(fullName, email, password, UserRole.CUSTOMER);
        emailVerificationService.issueForRegisteredCustomer(account);
        return account;
    }

    @Transactional
    public UserAccount createInternalAccount(
            String fullName, String email, String password, UserRole role) {
        if (role == null || role == UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Internal account provisioning requires a staff role.");
        }
        return createAccount(fullName, email, password, role);
    }

    private UserAccount createAccount(
            String fullName, String email, String password, UserRole role) {
        passwordPolicy.validate(password);

        String normalizedEmail = normalizeEmail(email);
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new EmailAlreadyRegisteredException();
        }

        String normalizedName = normalizeName(fullName);
        String passwordHash = passwordEncoder.encode(password);

        try {
            return userAccountRepository.create(normalizedName, normalizedEmail, passwordHash, role);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    public UserAccount login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        var account = userAccountRepository.findByEmail(normalizedEmail);
        String storedHash = account.map(UserAccount::passwordHash).orElse(dummyPasswordHash);
        String passwordCandidate =
                passwordPolicy.isBcryptCompatible(password) ? password : "invalid-password";
        boolean passwordMatches = passwordEncoder.matches(passwordCandidate, storedHash);

        if (account.isEmpty() || !account.get().active() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        if (account.get().role() == UserRole.CUSTOMER
                && !userAccountRepository.isEmailVerified(account.get().id())) {
            throw new EmailNotVerifiedException();
        }

        return account.get();
    }

    public UserAccount requireActiveUser(long userId) {
        return userAccountRepository.findById(userId)
                .filter(UserAccount::active)
                .orElseThrow(InvalidSessionException::new);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String fullName) {
        return Normalizer.normalize(fullName.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
    }
}

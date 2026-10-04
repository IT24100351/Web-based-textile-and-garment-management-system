package lk.ac.sliit.tgms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTests {

    private UserAccountRepository repository;
    private PasswordEncoder passwordEncoder;
    private EmailVerificationService emailVerificationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        emailVerificationService = mock(EmailVerificationService.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(
                repository,
                passwordEncoder,
                new PasswordPolicy(),
                emailVerificationService);
    }

    @Test
    void publicRegistrationAlwaysCreatesANormalizedCustomerAccount() {
        when(repository.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        when(repository.create(
                        eq("Nimsara Perera"),
                        eq("customer@example.com"),
                        any(String.class),
                        eq(UserRole.CUSTOMER)))
                .thenAnswer(invocation -> new UserAccount(
                        10L,
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(0),
                        invocation.getArgument(3),
                        true));

        UserAccount account = authService.registerCustomer(
                "  Nimsara   Perera  ", " Customer@Example.COM ", "secure-pass-123");

        assertThat(account.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(account.email()).isEqualTo("customer@example.com");
        assertThat(account.fullName()).isEqualTo("Nimsara Perera");
        assertThat(account.passwordHash()).isNotEqualTo("secure-pass-123");
        assertThat(passwordEncoder.matches("secure-pass-123", account.passwordHash())).isTrue();
        verify(repository).create(
                eq("Nimsara Perera"),
                eq("customer@example.com"),
                any(String.class),
                eq(UserRole.CUSTOMER));
        verify(emailVerificationService).issueForRegisteredCustomer(account);
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        when(repository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(account("stored-hash", true, UserRole.CUSTOMER)));

        assertThatThrownBy(() -> authService.registerCustomer(
                        "Customer", "customer@example.com", "secure-pass-123"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void validActiveInternalAccountCanUseTheCommonLogin() {
        String hash = passwordEncoder.encode("secure-pass-123");
        when(repository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(account(hash, true, UserRole.ADMINISTRATOR)));

        UserAccount account = authService.login(" CUSTOMER@example.com ", "secure-pass-123");

        assertThat(account.id()).isEqualTo(10L);
        assertThat(account.role()).isEqualTo(UserRole.ADMINISTRATOR);
    }

    @Test
    void customerCannotLoginUntilEmailIsVerified() {
        String hash = passwordEncoder.encode("secure-pass-123");
        UserAccount customer = account(hash, true, UserRole.CUSTOMER);
        when(repository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(customer));
        when(repository.isEmailVerified(customer.id())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                        "customer@example.com", "secure-pass-123"))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage("Verify your email address before signing in.");

        when(repository.isEmailVerified(customer.id())).thenReturn(true);
        assertThat(authService.login("customer@example.com", "secure-pass-123"))
                .isEqualTo(customer);
    }

    @Test
    void unknownWrongPasswordAndInactiveAccountsShareOneCredentialError() {
        when(repository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login("unknown@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        String hash = passwordEncoder.encode("secure-pass-123");
        when(repository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(account(hash, true, UserRole.CUSTOMER)));
        assertThatThrownBy(() -> authService.login("customer@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");

        when(repository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(account(hash, false, UserRole.CUSTOMER)));
        assertThatThrownBy(() -> authService.login("customer@example.com", "secure-pass-123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password.");
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() {
        String password = "é".repeat(37);

        assertThatThrownBy(() -> authService.registerCustomer(
                        "Customer", "customer@example.com", password))
                .isInstanceOf(PasswordPolicyException.class);
    }

    private UserAccount account(String passwordHash, boolean active, UserRole role) {
        return new UserAccount(
                10L,
                "customer@example.com",
                passwordHash,
                "Customer",
                role,
                active);
    }
}

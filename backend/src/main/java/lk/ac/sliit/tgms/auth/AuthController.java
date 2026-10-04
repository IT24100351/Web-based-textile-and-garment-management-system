package lk.ac.sliit.tgms.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthTokenService authTokenService;
    private final AuthCookieService authCookieService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(
            AuthService authService,
            AuthTokenService authTokenService,
            AuthCookieService authCookieService,
            PasswordResetService passwordResetService,
            EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.authTokenService = authTokenService;
        this.authCookieService = authCookieService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserAccount account = authService.registerCustomer(
                request.fullName(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(
                        "Account created. Check your email for the verification code.",
                        UserResponse.from(account)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount account = authService.login(request.email(), request.password());
        String token = authTokenService.issue(account);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.createSessionCookie(token))
                .body(new AuthResponse("Login successful.", UserResponse.from(account)));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearSessionCookie())
                .body(new MessageResponse("Logged out successfully."));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        try {
            passwordResetService.requestReset(request.email());
        } catch (PasswordResetRequestProcessingException ignored) {
            // Deliberately preserve the same public response when reset issuance/delivery fails.
        }
        return ResponseEntity.accepted()
                .body(new MessageResponse(
                        "If an active account matches that email, a password reset link will be sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmation request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(new MessageResponse(
                "Password updated successfully. You can now sign in with your new password."));
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<MessageResponse> resendEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request) {
        try {
            emailVerificationService.requestNewCode(request.email());
        } catch (EmailVerificationRequestProcessingException ignored) {
            // Preserve one response for unknown, verified, throttled, and delivery-failed states.
        }
        return ResponseEntity.accepted()
                .body(new MessageResponse(
                        "If the account is eligible, a new verification code will be sent."));
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<MessageResponse> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmation request) {
        emailVerificationService.confirmCode(request.email(), request.code());
        return ResponseEntity.ok(new MessageResponse(
                "Email verified successfully. You can now sign in."));
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return authenticatedUser(jwt);
    }

    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return new SessionResponse(false, null);
        }
        return new SessionResponse(true, authenticatedUser(jwt));
    }

    private UserResponse authenticatedUser(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidSessionException();
        }
        return UserResponse.from(authService.requireActiveUser(userId));
    }

    public record RegisterRequest(
            @NotBlank(message = "Full name is required.")
                    @Size(max = 120, message = "Full name must not exceed 120 characters.")
                    String fullName,
            @NotBlank(message = "Email is required.")
                    @Email(message = "Enter a valid email address.")
                    @Size(max = 254, message = "Email must not exceed 254 characters.")
                    String email,
            @NotBlank(message = "Password is required.")
                    @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters.")
                    String password) {
        @Override
        public String toString() {
            return "RegisterRequest[fullName=<redacted>, email=<redacted>, password=<redacted>]";
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.")
                    @Email(message = "Enter a valid email address.")
                    @Size(max = 254, message = "Email must not exceed 254 characters.")
                    String email,
            @NotBlank(message = "Password is required.")
                    @Size(max = 72, message = "Password must not exceed 72 characters.")
                    String password) {
        @Override
        public String toString() {
            return "LoginRequest[email=<redacted>, password=<redacted>]";
        }
    }

    public record PasswordResetRequest(
            @NotBlank(message = "Email is required.")
                    @Email(message = "Enter a valid email address.")
                    @Size(max = 254, message = "Email must not exceed 254 characters.")
                    String email) {
        @Override
        public String toString() {
            return "PasswordResetRequest[email=<redacted>]";
        }
    }

    public record PasswordResetConfirmation(
            @NotBlank(message = "Reset token is required.")
                    @Size(max = 512, message = "Reset token is invalid.")
                    String token,
            @NotBlank(message = "Password is required.")
                    @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters.")
                    String password) {
        @Override
        public String toString() {
            return "PasswordResetConfirmation[token=<redacted>, password=<redacted>]";
        }
    }

    public record EmailVerificationRequest(
            @NotBlank(message = "Email is required.")
                    @Email(message = "Enter a valid email address.")
                    @Size(max = 254, message = "Email must not exceed 254 characters.")
                    String email) {
        @Override
        public String toString() {
            return "EmailVerificationRequest[email=<redacted>]";
        }
    }

    public record EmailVerificationConfirmation(
            @NotBlank(message = "Email is required.")
                    @Email(message = "Enter a valid email address.")
                    @Size(max = 254, message = "Email must not exceed 254 characters.")
                    String email,
            @NotBlank(message = "Verification code is required.")
                    @Pattern(regexp = "\\d{6}", message = "Enter the six-digit verification code.")
                    String code) {
        @Override
        public String toString() {
            return "EmailVerificationConfirmation[email=<redacted>, code=<redacted>]";
        }
    }

    public record AuthResponse(String message, UserResponse user) {}

    public record SessionResponse(boolean authenticated, UserResponse user) {}

    public record MessageResponse(String message) {}

    public record UserResponse(long id, String fullName, String email, UserRole role) {
        static UserResponse from(UserAccount account) {
            return new UserResponse(
                    account.id(), account.fullName(), account.email(), account.role());
        }
    }
}

package lk.ac.sliit.tgms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class EmailVerificationApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM email_verification_codes");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void registrationCreatesUnverifiedCustomerAndLoginRequiresVerification() throws Exception {
        registerCustomer("new.customer@example.com");

        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT id, email_verified_at FROM users WHERE email = ?",
                "new.customer@example.com");
        assertThat(account.get("email_verified_at")).isNull();

        Map<String, Object> storedCode = jdbcTemplate.queryForMap(
                """
                SELECT code_salt, code_hash, failed_attempts, used_at
                FROM email_verification_codes
                WHERE user_id = ?
                """,
                account.get("id"));
        assertThat(storedCode.get("code_salt").toString()).hasSize(32);
        assertThat(storedCode.get("code_hash").toString()).hasSize(64);
        assertThat(((Number) storedCode.get("failed_attempts")).intValue()).isZero();
        assertThat(storedCode.get("used_at")).isNull();

        login("new.customer@example.com").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));

        mockMvc.perform(post("/api/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new.customer@example.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If the account is eligible, a new verification code will be sent."));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM email_verification_codes",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void validCodeVerifiesOnceAndEnablesLogin() throws Exception {
        registerCustomer("new.customer@example.com");
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                "new.customer@example.com");
        replaceCode(userId, "428196", "0123456789abcdef0123456789abcdef");

        confirm("new.customer@example.com", "000000")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EMAIL_VERIFICATION"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT failed_attempts FROM email_verification_codes WHERE user_id = ?",
                        Integer.class,
                        userId))
                .isEqualTo(1);

        confirm("new.customer@example.com", "428196")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Email verified successfully. You can now sign in."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_verified_at IS NOT NULL FROM users WHERE id = ?",
                        Boolean.class,
                        userId))
                .isTrue();
        login("new.customer@example.com").andExpect(status().isOk());
        confirm("new.customer@example.com", "428196")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EMAIL_VERIFICATION"));
    }

    @Test
    void resendAndConfirmRemainPublicWhenBrowserCarriesAnExpiredSessionCookie() throws Exception {
        registerCustomer("stale-cookie@example.com");
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "stale-cookie@example.com");
        replaceCode(userId, "428196", "abcdef0123456789abcdef0123456789");
        Cookie staleSession = new Cookie(AuthCookieService.COOKIE_NAME, "expired-or-invalid-token");

        mockMvc.perform(post("/api/auth/email-verification/resend")
                        .cookie(staleSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stale-cookie@example.com\"}"))
                .andExpect(status().isAccepted());

        replaceCode(userId, "428196", "abcdef0123456789abcdef0123456789");
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .cookie(staleSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"stale-cookie@example.com\",\"code\":\"428196\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void codeLocksAfterMaximumFailedAttempts() throws Exception {
        registerCustomer("new.customer@example.com");
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                "new.customer@example.com");
        replaceCode(userId, "428196", "fedcba9876543210fedcba9876543210");

        for (int attempt = 0; attempt < 5; attempt++) {
            confirm("new.customer@example.com", "000000")
                    .andExpect(status().isBadRequest());
        }

        Map<String, Object> state = jdbcTemplate.queryForMap(
                """
                SELECT failed_attempts, used_at
                FROM email_verification_codes
                WHERE user_id = ?
                """,
                userId);
        assertThat(((Number) state.get("failed_attempts")).intValue()).isEqualTo(5);
        assertThat(state.get("used_at")).isNotNull();
        confirm("new.customer@example.com", "428196")
                .andExpect(status().isBadRequest());
        login("new.customer@example.com").andExpect(status().isForbidden());
    }

    private void registerCustomer(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"New Customer",
                                  "email":"%s",
                                  "password":"secure-pass-123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(
                        "Account created. Check your email for the verification code."));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"secure-pass-123"}
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String email, String code)
            throws Exception {
        return mockMvc.perform(post("/api/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","code":"%s"}
                        """.formatted(email, code)));
    }

    private void replaceCode(long userId, String rawCode, String salt) {
        jdbcTemplate.update("DELETE FROM email_verification_codes WHERE user_id = ?", userId);
        jdbcTemplate.update(
                """
                INSERT INTO email_verification_codes (
                    user_id, code_salt, code_hash, expires_at
                ) VALUES (?, ?, ?, ?)
                """,
                userId,
                salt,
                EmailVerificationService.hashCode(rawCode, salt),
                Timestamp.from(Instant.now().plusSeconds(600)));
    }
}

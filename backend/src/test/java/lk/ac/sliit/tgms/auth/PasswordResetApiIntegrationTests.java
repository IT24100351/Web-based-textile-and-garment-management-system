package lk.ac.sliit.tgms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PasswordResetApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void resetRequestUsesOneEnumerationSafeResponseAndStoresOnlyAHash() throws Exception {
        long userId = createActiveCustomer("customer@example.com", "old-password-123");

        String requestBody = """
                {"email":"customer@example.com"}
                """;
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If an active account matches that email, a password reset link will be sent."));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT user_id, token_hash, used_at FROM password_reset_tokens");
        assertThat(((Number) stored.get("user_id")).longValue()).isEqualTo(userId);
        assertThat(stored.get("token_hash").toString()).hasSize(64);
        assertThat(stored.get("token_hash").toString()).doesNotContain("customer@example.com");
        assertThat(stored.get("used_at")).isNull();

        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If an active account matches that email, a password reset link will be sent."));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM password_reset_tokens", Integer.class))
                .isZero();

        jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = ?", userId);
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If an active account matches that email, a password reset link will be sent."));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM password_reset_tokens", Integer.class))
                .isZero();
    }

    @Test
    void repeatedResetRequestsAreThrottledWithoutCreatingAnotherValidToken() throws Exception {
        createActiveCustomer("customer@example.com", "old-password-123");
        String requestBody = """
                {"email":"customer@example.com"}
                """;

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());
        String firstTokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM password_reset_tokens", String.class);

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("If an active account matches that email, a password reset link will be sent."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM password_reset_tokens", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT token_hash FROM password_reset_tokens", String.class))
                .isEqualTo(firstTokenHash);
    }

    @Test
    void validTokenChangesPasswordAndCanOnlyBeUsedOnce() throws Exception {
        long userId = createActiveCustomer("customer@example.com", "old-password-123");
        Integer originalSessionVersion = jdbcTemplate.queryForObject(
                "SELECT session_version FROM users WHERE id = ?", Integer.class, userId);
        String rawToken = "single-use-reset-token-value";
        insertResetToken(userId, rawToken, Instant.now().plusSeconds(600));
        MvcResult oldLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com","password":"old-password-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie oldSession = oldLogin.getResponse().getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(oldSession).isNotNull();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token":"single-use-reset-token-value",
                                  "password":"new-secure-password-456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Password updated successfully. You can now sign in with your new password."));

        Map<String, Object> state = jdbcTemplate.queryForMap(
                """
                SELECT u.password_hash, t.used_at
                FROM users u
                JOIN password_reset_tokens t ON t.user_id = u.id
                WHERE u.id = ?
                """,
                userId);
        assertThat(state.get("password_hash").toString())
                .isNotEqualTo("new-secure-password-456");
        assertThat(passwordEncoder.matches(
                        "new-secure-password-456", state.get("password_hash").toString()))
                .isTrue();
        assertThat(state.get("used_at")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT session_version FROM users WHERE id = ?", Integer.class, userId))
                .isEqualTo(originalSessionVersion + 1);

        mockMvc.perform(get("/api/profile").cookie(oldSession))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com","password":"old-password-123"}
                                """))
                .andExpect(status().isUnauthorized());

        MvcResult newLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com","password":"new-secure-password-456"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Cookie newSession = newLogin.getResponse().getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(newSession).isNotNull();
        mockMvc.perform(get("/api/profile").cookie(newSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token":"single-use-reset-token-value",
                                  "password":"another-password-789"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD_RESET"));
    }

    @Test
    void expiredUnknownAndInactiveAccountTokensShareTheSameSafeFailure() throws Exception {
        long userId = createActiveCustomer("customer@example.com", "old-password-123");
        insertResetToken(userId, "expired-reset-token-value", Instant.now().minusSeconds(1));

        assertInvalidReset("expired-reset-token-value");
        assertInvalidReset("completely-unknown-reset-token-value");

        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        insertResetToken(userId, "inactive-account-reset-token", Instant.now().plusSeconds(600));
        jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = ?", userId);
        assertInvalidReset("inactive-account-reset-token");
    }

    @Test
    void successfulResetInvalidatesEveryOtherUnusedTokenForTheAccount() throws Exception {
        long userId = createActiveCustomer("customer@example.com", "old-password-123");
        insertResetToken(userId, "first-reset-token-value", Instant.now().plusSeconds(600));
        insertResetToken(userId, "second-reset-token-value", Instant.now().plusSeconds(600));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token":"second-reset-token-value",
                                  "password":"new-secure-password-456"
                                }
                                """))
                .andExpect(status().isOk());

        Integer totalOpenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL",
                Integer.class,
                userId);
        assertThat(totalOpenCount).isZero();
        assertInvalidReset("first-reset-token-value");
    }

    private void assertInvalidReset(String rawToken) throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"new-secure-password-456"}
                                """.formatted(rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD_RESET"))
                .andExpect(jsonPath("$.error.message")
                        .value("This password reset link is invalid or has expired. Request a new reset link."));
    }

    private long createActiveCustomer(String email, String password) {
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    email, password_hash, full_name, role, is_active, email_verified_at
                )
                VALUES (?, ?, 'Reset Test Customer', 'CUSTOMER', TRUE, CURRENT_TIMESTAMP(6))
                """,
                email,
                passwordEncoder.encode(password));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertResetToken(long userId, String rawToken, Instant expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """,
                userId,
                PasswordResetService.hashToken(rawToken),
                Timestamp.from(expiresAt));
    }
}

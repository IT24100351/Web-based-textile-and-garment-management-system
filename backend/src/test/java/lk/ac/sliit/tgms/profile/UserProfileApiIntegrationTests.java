package lk.ac.sliit.tgms.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import lk.ac.sliit.tgms.auth.AuthCookieService;
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
class UserProfileApiIntegrationTests {

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
    void authenticatedUserCanLoadAndUpdateOnlyOwnPermittedProfileField() throws Exception {
        Cookie session = registerAndLogin(
                "Original Customer", "original@example.com", "secure-pass-123");

        Map<String, Object> original = jdbcTemplate.queryForMap(
                "SELECT id, email, password_hash, role, is_active FROM users WHERE email = ?",
                "original@example.com");
        long originalId = ((Number) original.get("id")).longValue();
        String originalEmail = (String) original.get("email");
        String originalPasswordHash = (String) original.get("password_hash");

        mockMvc.perform(get("/api/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.fullName").value("Original Customer"))
                .andExpect(jsonPath("$.email").value("original@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.active").doesNotExist());

        mockMvc.perform(put("/api/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 999999,
                                  "fullName": "  Updated    Customer  ",
                                  "email": "attacker@example.com",
                                  "role": "ADMINISTRATOR",
                                  "isActive": false,
                                  "password": "attacker-controlled",
                                  "passwordHash": "attacker-controlled"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.fullName").value("Updated Customer"))
                .andExpect(jsonPath("$.email").value(originalEmail))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT id, email, full_name, password_hash, role, is_active FROM users WHERE id = ?",
                originalId);
        assertThat(((Number) stored.get("id")).longValue()).isEqualTo(originalId);
        assertThat(stored.get("email")).isEqualTo(originalEmail);
        assertThat(stored.get("full_name")).isEqualTo("Updated Customer");
        assertThat(stored.get("password_hash")).isEqualTo(originalPasswordHash);
        assertThat(stored.get("role")).isEqualTo("CUSTOMER");
        assertThat(stored.get("is_active")).isEqualTo(true);

        mockMvc.perform(get("/api/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Customer"))
                .andExpect(jsonPath("$.email").value(originalEmail));
    }

    @Test
    void profileRoutesRequireAuthenticationAndValidateEditableField() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Someone"}
                                """))
                .andExpect(status().isUnauthorized());

        Cookie session = registerAndLogin(
                "Validation Customer", "validation@example.com", "secure-pass-123");
        mockMvc.perform(put("/api/profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.fullName").exists());

        mockMvc.perform(put("/api/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"secure-pass-123","newPassword":"new-secure-pass-456"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanChangeOwnPasswordUsingCurrentPassword() throws Exception {
        String email = "password-change@example.com";
        String currentPassword = "secure-pass-123";
        String newPassword = "new-secure-pass-456";
        Cookie session = registerAndLogin("Password Customer", email, currentPassword);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        Integer originalSessionVersion = jdbcTemplate.queryForObject(
                "SELECT session_version FROM users WHERE email = ?", Integer.class, email);
        mockMvc.perform(get("/api/profile").cookie(session))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                """
                INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """,
                userId,
                "a".repeat(64));

        MvcResult passwordChange = mockMvc.perform(put("/api/profile/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """).formatted(currentPassword, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Password changed successfully. Please sign in again."))
                .andReturn();
        Cookie clearedSession = passwordChange.getResponse()
                .getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(clearedSession).isNotNull();
        assertThat(clearedSession.getMaxAge()).isZero();

        String updatedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(updatedHash).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(newPassword, updatedHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT session_version FROM users WHERE email = ?", Integer.class, email))
                .isEqualTo(originalSessionVersion + 1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM password_reset_tokens
                WHERE user_id = ? AND used_at IS NOT NULL
                """,
                Integer.class,
                userId)).isEqualTo(1);

        mockMvc.perform(get("/api/profile").cookie(session))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"email":"%s","password":"%s"}
                                """).formatted(email, currentPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        MvcResult newLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"email":"%s","password":"%s"}
                                """).formatted(email, newPassword)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie newSession = newLogin.getResponse().getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(newSession).isNotNull();
        mockMvc.perform(get("/api/profile").cookie(newSession))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeRejectsWrongCurrentPasswordWeakPasswordAndPasswordReuse() throws Exception {
        String email = "password-validation@example.com";
        String currentPassword = "secure-pass-123";
        Cookie session = registerAndLogin("Password Validation", email, currentPassword);
        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);

        mockMvc.perform(put("/api/profile/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"new-secure-pass-456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.currentPassword")
                        .value("Current password is incorrect."));

        mockMvc.perform(put("/api/profile/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"currentPassword":"%s","newPassword":"short"}
                                """).formatted(currentPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.newPassword").exists());

        mockMvc.perform(put("/api/profile/password")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """).formatted(currentPassword, currentPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.newPassword")
                        .value("New password must be different from the current password."));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email))
                .isEqualTo(originalHash);
    }

    private Cookie registerAndLogin(String fullName, String email, String password) throws Exception {
        register(fullName, email, password);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"email":"%s","password":"%s"}
                                """).formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(session).isNotNull();
        return session;
    }

    private void register(String fullName, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"fullName":"%s","email":"%s","password":"%s"}
                                """).formatted(fullName, email, password)))
                .andExpect(status().isCreated());
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
    }
}

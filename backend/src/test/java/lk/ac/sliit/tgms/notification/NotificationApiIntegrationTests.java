package lk.ac.sliit.tgms.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.AuthTokenService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class NotificationApiIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'tgms73-api-%'");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void recipientSeesAndMutatesOnlyOwnNotifications() throws Exception {
        UserAccount first = insertUser(73001, "tgms73-api-first@example.com", UserRole.CUSTOMER);
        UserAccount second = insertUser(73002, "tgms73-api-second@example.com", UserRole.CUSTOMER);
        long firstNotification = insertNotification(first.id(), "ORDER_STATUS", "ORDER", 101);
        long secondNotification = insertNotification(second.id(), "DELIVERY_STATUS", "DELIVERY", 202);

        Cookie firstSession = sessionFor(first);
        mockMvc.perform(get("/api/notifications").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].id").value(firstNotification))
                .andExpect(jsonPath("$.notifications[0].sourceRecordId").value(101))
                .andExpect(jsonPath("$.notifications[0].recipientUserId").doesNotExist());

        mockMvc.perform(patch("/api/notifications/{id}/read", secondNotification).cookie(firstSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT read_at IS NULL FROM notifications WHERE id = ?",
                        Boolean.class,
                        secondNotification))
                .isTrue();

        mockMvc.perform(patch("/api/notifications/{id}/read", firstNotification).cookie(firstSession))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").queryParam("unreadOnly", "true").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.notifications.length()").value(0));
    }

    @Test
    void notificationRoutesRequireAuthenticationAndValidateFilters() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());

        UserAccount user = insertUser(73003, "tgms73-api-filter@example.com", UserRole.SALES_OFFICER);
        Cookie session = sessionFor(user);
        mockMvc.perform(get("/api/notifications")
                        .queryParam("unreadOnly", "maybe")
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/notifications")
                        .queryParam("limit", "1000")
                        .cookie(session))
                .andExpect(status().isBadRequest());
    }

    private UserAccount insertUser(long id, String email, UserRole role) {
        String hash = "$2a$10$01234567890123456789012345678901234567890123456789012";
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, ?, ?, ?, ?)",
                id, email, hash, "TGMS 73 User " + id, role.name());
        return new UserAccount(id, email, hash, "TGMS 73 User " + id, role, true);
    }

    private long insertNotification(long recipientId, String kind, String module, long sourceId) {
        jdbcTemplate.update(
                """
                INSERT INTO notifications
                    (recipient_user_id, kind, title, message, source_module, source_record_id)
                VALUES (?, ?, 'Status changed', 'Stored module event changed.', ?, ?)
                """,
                recipientId, kind, module, sourceId);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM notifications WHERE recipient_user_id = ?", Long.class, recipientId);
    }

    private Cookie sessionFor(UserAccount account) {
        Cookie cookie = new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
        cookie.setPath("/");
        return cookie;
    }
}

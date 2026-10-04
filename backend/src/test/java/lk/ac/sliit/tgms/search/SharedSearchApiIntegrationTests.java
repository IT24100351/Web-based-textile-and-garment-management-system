package lk.ac.sliit.tgms.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SharedSearchApiIntegrationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM deliveries");
        jdbcTemplate.update("DELETE FROM production_task_material_usage");
        jdbcTemplate.update("DELETE FROM production_task_material_requirements");
        jdbcTemplate.update("DELETE FROM production_tasks");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'tgms74-%'");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void searchRequiresAuthenticationAndValidatesQueryAndPagination() throws Exception {
        mockMvc.perform(get("/api/search").queryParam("search", "ORD"))
                .andExpect(status().isUnauthorized());

        Cookie session = registerAndLogin("TGMS 74 Customer", "tgms74-customer@example.com");
        mockMvc.perform(get("/api/search").queryParam("search", "x").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.search").exists());

        mockMvc.perform(get("/api/search")
                        .queryParam("search", "ORD")
                        .queryParam("page", "-1")
                        .queryParam("size", "100")
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.page").exists())
                .andExpect(jsonPath("$.error.fields.size").exists());
    }

    @Test
    void customerSearchCannotRevealAnotherCustomersOrder() throws Exception {
        Cookie firstSession = registerAndLogin("First Customer", "tgms74-first@example.com");
        registerAndLogin("Second Customer", "tgms74-second@example.com");
        long firstId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'tgms74-first@example.com'", Long.class);
        long secondId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'tgms74-second@example.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO orders (customer_id, order_number, status) VALUES (?, 'TGMS74-FIRST-ORDER', 'CONFIRMED')",
                firstId);
        jdbcTemplate.update(
                "INSERT INTO orders (customer_id, order_number, status) VALUES (?, 'TGMS74-SECOND-SECRET', 'CONFIRMED')",
                secondId);

        mockMvc.perform(get("/api/search")
                        .queryParam("search", "TGMS74")
                        .cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.module == 'ORDER')].title").value("TGMS74-FIRST-ORDER"))
                .andExpect(jsonPath("$.results[?(@.title == 'TGMS74-SECOND-SECRET')]").isEmpty());
    }

    private Cookie registerAndLogin(String fullName, String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"fullName":"%s","email":"%s","password":"secure-pass-123"}
                                """).formatted(fullName, email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"email":"%s","password":"secure-pass-123"}
                                """).formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie(AuthCookieService.COOKIE_NAME);
        assertThat(session).isNotNull();
        return session;
    }
}

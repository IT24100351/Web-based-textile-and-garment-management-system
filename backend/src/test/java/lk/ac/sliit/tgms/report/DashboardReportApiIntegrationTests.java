package lk.ac.sliit.tgms.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
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
class DashboardReportApiIntegrationTests {
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
        jdbcTemplate.update("DELETE FROM inventory_materials");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'tgms75-%'");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void reportRequiresAuthenticationAndValidatesDateRange() throws Exception {
        mockMvc.perform(get("/api/reports/dashboard"))
                .andExpect(status().isUnauthorized());

        Cookie session = registerRoleAndLogin(
                "TGMS 75 Customer", "tgms75-validation@example.com", "CUSTOMER");
        mockMvc.perform(get("/api/reports/dashboard")
                        .queryParam("from", "2026-08-31")
                        .queryParam("to", "2026-08-01")
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.from").exists());

        mockMvc.perform(get("/api/reports/dashboard")
                        .queryParam("from", "not-a-date")
                        .queryParam("to", "2026-08-31")
                        .cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.from").exists());
    }

    @Test
    void administratorReportAggregatesActualStoredCoreRecords() throws Exception {
        Cookie adminSession = registerRoleAndLogin(
                "TGMS 75 Administrator", "tgms75-admin@example.com", "ADMINISTRATOR");
        registerRoleAndLogin("Report Customer", "tgms75-report-customer@example.com", "CUSTOMER");
        long customerId = userId("tgms75-report-customer@example.com");

        long orderId = insertOrder(customerId, "TGMS75-IN-RANGE", "CONFIRMED", LocalDateTime.of(2026, 8, 10, 10, 0));
        insertOrder(customerId, "TGMS75-OUTSIDE", "CANCELLED", LocalDateTime.of(2026, 7, 10, 10, 0));
        insertInventory("TGMS75-LOW", new BigDecimal("4.000"), new BigDecimal("5.000"), "ACTIVE");
        insertInventory("TGMS75-OK", new BigDecimal("20.000"), new BigDecimal("5.000"), "ACTIVE");
        insertInventory("TGMS75-INACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, "INACTIVE");
        insertProduction(orderId, "TGMS75-TASK", "IN_PROGRESS", LocalDateTime.of(2026, 8, 12, 9, 0));
        insertDelivery(orderId, "TGMS75-DEL", "DELIVERED", LocalDateTime.of(2026, 8, 20, 9, 0));

        mockMvc.perform(get("/api/reports/dashboard")
                        .queryParam("from", "2026-08-01")
                        .queryParam("to", "2026-08-31")
                        .cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.from").value("2026-08-01"))
                .andExpect(jsonPath("$.to").value("2026-08-31"))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.sections.length()").value(4))
                .andExpect(jsonPath("$.sections[0].key").value("orders"))
                .andExpect(jsonPath("$.sections[0].metrics[0].value").value(1))
                .andExpect(jsonPath("$.sections[0].metrics[2].value").value(1))
                .andExpect(jsonPath("$.sections[1].key").value("inventory"))
                .andExpect(jsonPath("$.sections[1].metrics[0].value").value(3))
                .andExpect(jsonPath("$.sections[1].metrics[2].value").value(1))
                .andExpect(jsonPath("$.sections[2].key").value("production"))
                .andExpect(jsonPath("$.sections[2].metrics[0].value").value(1))
                .andExpect(jsonPath("$.sections[2].metrics[2].value").value(1))
                .andExpect(jsonPath("$.sections[3].key").value("delivery"))
                .andExpect(jsonPath("$.sections[3].metrics[0].value").value(1))
                .andExpect(jsonPath("$.sections[3].metrics[3].value").value(1));
    }

    @Test
    void customerReportIsOwnershipScopedAndRolesReceiveOnlyPermittedSections() throws Exception {
        Cookie firstCustomerSession = registerRoleAndLogin(
                "First Customer", "tgms75-first@example.com", "CUSTOMER");
        registerRoleAndLogin("Second Customer", "tgms75-second@example.com", "CUSTOMER");
        long firstId = userId("tgms75-first@example.com");
        long secondId = userId("tgms75-second@example.com");
        insertOrder(firstId, "TGMS75-FIRST", "PENDING", LocalDateTime.of(2026, 8, 5, 10, 0));
        insertOrder(secondId, "TGMS75-SECOND", "COMPLETED", LocalDateTime.of(2026, 8, 6, 10, 0));

        mockMvc.perform(get("/api/reports/dashboard")
                        .queryParam("from", "2026-08-01")
                        .queryParam("to", "2026-08-31")
                        .cookie(firstCustomerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(1))
                .andExpect(jsonPath("$.sections[0].key").value("orders"))
                .andExpect(jsonPath("$.sections[0].metrics[0].value").value(1))
                .andExpect(jsonPath("$.sections[0].metrics[1].value").value(1))
                .andExpect(jsonPath("$.sections[0].metrics[5].value").value(0));

        Cookie inventorySession = registerRoleAndLogin(
                "Inventory Manager", "tgms75-inventory@example.com", "INVENTORY_MANAGER");
        mockMvc.perform(get("/api/reports/dashboard")
                        .queryParam("from", "2026-08-01")
                        .queryParam("to", "2026-08-31")
                        .cookie(inventorySession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(1))
                .andExpect(jsonPath("$.sections[0].key").value("inventory"));

        Cookie supplierSession = registerRoleAndLogin(
                "Supplier", "tgms75-supplier@example.com", "SUPPLIER");
        mockMvc.perform(get("/api/reports/dashboard")
                        .queryParam("from", "2026-08-01")
                        .queryParam("to", "2026-08-31")
                        .cookie(supplierSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.length()").value(0))
                .andExpect(jsonPath("$.notes[0]").value(org.hamcrest.Matchers.containsString("no Order, Inventory, Production or Delivery report scope")));
    }

    private Cookie registerRoleAndLogin(String fullName, String email, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"fullName":"%s","email":"%s","password":"secure-pass-123"}
                                """).formatted(fullName, email)))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET role = ? WHERE email = ?", role, email);
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

    private long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertOrder(long customerId, String number, String status, LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO orders (customer_id, order_number, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                customerId, number, status, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
        return jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_number = ?", Long.class, number);
    }

    private void insertInventory(String code, BigDecimal quantity, BigDecimal threshold, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO inventory_materials
                    (material_code, material_name, material_type, unit_of_measure,
                     current_quantity, low_stock_threshold, status)
                VALUES (?, ?, 'FABRIC', 'METRE', ?, ?, ?)
                """,
                code, code + " material", quantity, threshold, status);
    }

    private void insertProduction(long orderId, String number, String status, LocalDateTime createdAt) {
        Timestamp timestamp = Timestamp.valueOf(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO production_tasks
                    (task_number, order_id, status, started_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                number, orderId, status, timestamp, timestamp, timestamp);
    }

    private void insertDelivery(long orderId, String number, String status, LocalDateTime createdAt) {
        Timestamp timestamp = Timestamp.valueOf(createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO deliveries
                    (delivery_number, order_id, active_order_lock_id, scheduled_at,
                     delivery_address, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                number, orderId, orderId, timestamp, "TGMS 75 test address", status, timestamp, timestamp);
    }
}

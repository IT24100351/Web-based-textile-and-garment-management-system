package lk.ac.sliit.tgms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.AuthTokenService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * TGMS-66 final Delivery handoff regression: a real Product-backed Order arriving from Production
 * as READY_FOR_DELIVERY is selected, scheduled, dispatched, completed, synchronized to Order
 * Management, and observed through the registered customer's ownership-protected tracking API.
 */
@SpringBootTest
class DeliveryModuleHandoffIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        seedOperationalHandoff();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void readyOrderFlowsThroughDeliveryToCustomerTrackingAndOrderCompletion() throws Exception {
        Cookie sales = sessionFor(99791, UserRole.SALES_OFFICER);
        Cookie customer = sessionFor(99701, UserRole.CUSTOMER);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM production_tasks WHERE id = 99731", String.class))
                .isEqualTo("COMPLETED");

        mockMvc.perform(get("/api/deliveries/eligible-orders/99711").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(99711))
                .andExpect(jsonPath("$.items[0].productId").value(99742))
                .andExpect(jsonPath("$.readyForDelivery").value(true));

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId":99711,
                                  "scheduledAt":"2099-12-31T20:30:00Z",
                                  "deliveryAddress":"99 Final Handoff Road, Colombo",
                                  "deliveryNotes":"Final Delivery module handoff"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.orderId").value(99711))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));

        Long deliveryId = jdbcTemplate.queryForObject(
                "SELECT id FROM deliveries WHERE active_order_lock_id = 99711", Long.class);
        assertThat(deliveryId).isNotNull().isPositive();

        mockMvc.perform(get("/api/deliveries/mine/orders/99711/tracking").cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDelivery").value(true))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("OUT_FOR_DELIVERY"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 99711", String.class))
                .isEqualTo("READY_FOR_DELIVERY");

        mockMvc.perform(get("/api/deliveries/mine/orders/99711/tracking").cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("OUT_FOR_DELIVERY"));

        mockMvc.perform(patch("/api/deliveries/" + deliveryId + "/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 99711", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_status_history WHERE order_id = 99711 AND from_status = 'READY_FOR_DELIVERY' AND to_status = 'COMPLETED'",
                        Integer.class))
                .isEqualTo(1);

        mockMvc.perform(get("/api/deliveries/mine/orders/99711/tracking").cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"));
    }

    private void seedOperationalHandoff() {
        insertUser(99701, "customer.handoff@example.com", "Handoff Customer", UserRole.CUSTOMER);
        insertUser(99791, "sales.handoff@example.com", "Handoff Sales Officer", UserRole.SALES_OFFICER);
        insertUser(99792, "production.handoff@example.com", "Handoff Production Manager", UserRole.PRODUCTION_MANAGER);
        jdbcTemplate.update("INSERT INTO garment_categories (id, name, status) VALUES (99741, 'Handoff Garments', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_products (id, category_id, name, status) VALUES (99742, 99741, 'Handoff Shirt', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (99743, 99742, 'L', 'Black', 3200.00, 'AVAILABLE')");
        jdbcTemplate.update("INSERT INTO orders (id, customer_id, order_number, status) VALUES (99711, 99701, 'ORD-HANDOFF-99711', 'READY_FOR_DELIVERY')");
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, quantity,
                    selected_size, selected_color, unit_price_snapshot
                ) VALUES (99721, 99711, 99742, 99743, 1, 'L', 'Black', 3200.00)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO production_tasks (
                    id, task_number, order_id, status, started_at, completed_at,
                    quality_control_result, quality_checked_by_user_id, quality_checked_at
                ) VALUES (99731, 'PRD-HANDOFF-99731', 99711, 'COMPLETED',
                          '2099-12-30 08:00:00', '2099-12-30 12:00:00',
                          'PASSED', 99792, '2099-12-30 11:30:00')
                """);
    }

    private void insertUser(long id, String email, String name, UserRole role) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                VALUES (?, ?, 'not-used', ?, ?, TRUE)
                """,
                id, email, name, role.name());
    }

    private Cookie sessionFor(long userId, UserRole role) {
        UserAccount account = new UserAccount(
                userId,
                "session-" + userId + "@example.com",
                "not-used",
                "Session User",
                role,
                true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM deliveries WHERE order_id = 99711");
        jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id = 99711");
        jdbcTemplate.update("DELETE FROM production_task_material_usage WHERE production_task_id = 99731");
        jdbcTemplate.update("DELETE FROM production_task_material_requirements WHERE production_task_id = 99731");
        jdbcTemplate.update("DELETE FROM production_task_details WHERE production_task_id = 99731");
        jdbcTemplate.update("DELETE FROM production_tasks WHERE id = 99731");
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = 99711");
        jdbcTemplate.update("DELETE FROM orders WHERE id = 99711");
        jdbcTemplate.update("DELETE FROM garment_product_variants WHERE id = 99743");
        jdbcTemplate.update("DELETE FROM garment_products WHERE id = 99742");
        jdbcTemplate.update("DELETE FROM garment_categories WHERE id = 99741");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (99701, 99791, 99792)");
    }
}

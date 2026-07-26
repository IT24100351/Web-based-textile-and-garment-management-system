package lk.ac.sliit.tgms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
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

@SpringBootTest
class DeliveryRecordsApiIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        seedBaseData();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void salesOfficerCanListSearchFilterAndOpenDeliveryRecordsWithLiveOrderDetails() throws Exception {
        insertOrder(99611, 99601, "ORD-DELIVERY-RECORD-ASHA", "READY_FOR_DELIVERY", 99621);
        insertOrder(99612, 99602, "ORD-DELIVERY-RECORD-NIMAL", "READY_FOR_DELIVERY", 99622);
        insertDelivery(99631, "DLV-RECORD-ASHA", 99611, "25 Galle Road", "SCHEDULED");
        insertDelivery(99632, "DLV-RECORD-NIMAL", 99612, "18 Temple Road", "CANCELLED");
        Cookie sales = sessionFor(99691, UserRole.SALES_OFFICER);

        mockMvc.perform(get("/api/deliveries").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/deliveries")
                        .param("search", "RECORD-ASHA")
                        .param("status", "SCHEDULED")
                        .cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].delivery.id").value(99631))
                .andExpect(jsonPath("$[0].delivery.deliveryNumber").value("DLV-RECORD-ASHA"))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-DELIVERY-RECORD-ASHA"))
                .andExpect(jsonPath("$[0].customerName").value("Asha Records"))
                .andExpect(jsonPath("$[0].customerEmail").value("asha.records@example.com"))
                .andExpect(jsonPath("$[0].orderStatus").value("READY_FOR_DELIVERY"))
                .andExpect(jsonPath("$[0].items[0].productName").value("Records Shirt"));

        mockMvc.perform(get("/api/deliveries/99631").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.deliveryAddress").value("25 Galle Road"))
                .andExpect(jsonPath("$.orderTotal").value(5000.0))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void scheduledDeliveryCanBeSafelyCancelledAndReplacementCanBeScheduledWithoutDeletingHistory()
            throws Exception {
        insertOrder(99611, 99601, "ORD-CANCEL-REPLACE", "READY_FOR_DELIVERY", 99621);
        insertDelivery(99631, "DLV-INCORRECT-99631", 99611, "Wrong address", "SCHEDULED");
        Cookie sales = sessionFor(99691, UserRole.SALES_OFFICER);

        mockMvc.perform(patch("/api/deliveries/99631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("CANCELLED"))
                .andExpect(jsonPath("$.delivery.allowedStatusTransitions.length()").value(0));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM deliveries WHERE id = 99631", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT active_order_lock_id FROM deliveries WHERE id = 99631", Long.class))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 99611", String.class))
                .isEqualTo("READY_FOR_DELIVERY");

        mockMvc.perform(get("/api/deliveries/eligible-orders/99611").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForDelivery").value(true));

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId":99611,
                                  "scheduledAt":"2099-12-31T18:30:00Z",
                                  "deliveryAddress":"Correct replacement address",
                                  "deliveryNotes":"Replacement after cancelling incorrect schedule"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.orderId").value(99611))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM deliveries WHERE order_id = 99611", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM deliveries WHERE order_id = 99611 AND status = 'CANCELLED'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM deliveries WHERE active_order_lock_id = 99611",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void dispatchedDeliveryCannotBeCancelledAndFailuresDoNotCorruptOrderOrDelivery() throws Exception {
        insertOrder(99611, 99601, "ORD-DISPATCHED", "READY_FOR_DELIVERY", 99621);
        insertDelivery(99631, "DLV-DISPATCHED", 99611, "25 Galle Road", "OUT_FOR_DELIVERY");
        Cookie sales = sessionFor(99691, UserRole.SALES_OFFICER);

        mockMvc.perform(patch("/api/deliveries/99631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_STATUS_TRANSITION_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM deliveries WHERE id = 99631", String.class))
                .isEqualTo("OUT_FOR_DELIVERY");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 99611", String.class))
                .isEqualTo("READY_FOR_DELIVERY");

        mockMvc.perform(get("/api/deliveries/999999").cookie(sales))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_NOT_FOUND"));

        mockMvc.perform(get("/api/deliveries")
                        .param("status", "UNKNOWN")
                        .cookie(sales))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deliveryRecordsAreSalesOfficerOnly() throws Exception {
        insertOrder(99611, 99601, "ORD-RECORD-AUTH", "READY_FOR_DELIVERY", 99621);
        insertDelivery(99631, "DLV-RECORD-AUTH", 99611, "25 Galle Road", "SCHEDULED");

        mockMvc.perform(get("/api/deliveries")
                        .cookie(sessionFor(99692, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deliveries/99631"))
                .andExpect(status().isUnauthorized());
    }

    private void seedBaseData() {
        insertUser(99601, "asha.records@example.com", "Asha Records", UserRole.CUSTOMER);
        insertUser(99602, "nimal.records@example.com", "Nimal Records", UserRole.CUSTOMER);
        insertUser(99691, "sales.records@example.com", "Delivery Records Officer", UserRole.SALES_OFFICER);
        insertUser(99692, "customer.records@example.com", "Other Customer", UserRole.CUSTOMER);
        jdbcTemplate.update("INSERT INTO garment_categories (id, name, status) VALUES (99641, 'Records Garments', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_products (id, category_id, name, status) VALUES (99642, 99641, 'Records Shirt', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (99643, 99642, 'M', 'Blue', 2500.00, 'AVAILABLE')");
    }

    private void insertOrder(long id, long customerId, String orderNumber, String status, long itemId) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (?, ?, ?, ?)",
                id, customerId, orderNumber, status);
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, quantity,
                    selected_size, selected_color, unit_price_snapshot
                ) VALUES (?, ?, 99642, 99643, 2, 'M', 'Blue', 2500.00)
                """,
                itemId, id);
    }

    private void insertDelivery(
            long id, String number, long orderId, String address, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id,
                    scheduled_at, delivery_address, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                number,
                orderId,
                "CANCELLED".equals(status) ? null : orderId,
                Timestamp.from(Instant.parse("2099-12-31T10:30:00Z")),
                address,
                status);
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
        jdbcTemplate.update("DELETE FROM deliveries WHERE order_id IN (99611, 99612)");
        jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id IN (99611, 99612)");
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (99611, 99612)");
        jdbcTemplate.update("DELETE FROM orders WHERE id IN (99611, 99612)");
        jdbcTemplate.update("DELETE FROM garment_product_variants WHERE id = 99643");
        jdbcTemplate.update("DELETE FROM garment_products WHERE id = 99642");
        jdbcTemplate.update("DELETE FROM garment_categories WHERE id = 99641");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (99601, 99602, 99691, 99692)");
    }
}

package lk.ac.sliit.tgms.delivery;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class CustomerDeliveryTrackingApiIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        seedUsersAndOrders();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void customerReadsOnlyTheRealDeliveryLinkedToOwnOrder() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                98531,
                "DLV-CUSTOMER-98531",
                98511,
                98511,
                Timestamp.from(Instant.parse("2099-12-31T10:30:00Z")),
                "10 Main Street",
                "OUT_FOR_DELIVERY");

        mockMvc.perform(get("/api/deliveries/mine/orders/98511/tracking")
                        .cookie(sessionFor(98501, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(98511))
                .andExpect(jsonPath("$.hasDelivery").value(true))
                .andExpect(jsonPath("$.delivery.deliveryId").value(98531))
                .andExpect(jsonPath("$.delivery.deliveryNumber").value("DLV-CUSTOMER-98531"))
                .andExpect(jsonPath("$.delivery.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.delivery.scheduledAt").exists())
                .andExpect(jsonPath("$.delivery.lastUpdatedAt").exists())
                .andExpect(jsonPath("$.delivery.deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.delivery.deliveryNotes").doesNotExist());
    }

    @Test
    void customerTrackingPrefersCurrentReplacementOverCancelledHistory() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, status
                ) VALUES (98531, 'DLV-OLD-CANCELLED', 98511, NULL, ?, 'Old address', 'CANCELLED')
                """,
                Timestamp.from(Instant.parse("2099-12-31T08:00:00Z")));
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, status
                ) VALUES (98532, 'DLV-CURRENT-REPLACEMENT', 98511, 98511, ?, 'Correct address', 'SCHEDULED')
                """,
                Timestamp.from(Instant.parse("2099-12-31T10:30:00Z")));

        mockMvc.perform(get("/api/deliveries/mine/orders/98511/tracking")
                        .cookie(sessionFor(98501, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasDelivery").value(true))
                .andExpect(jsonPath("$.delivery.deliveryId").value(98532))
                .andExpect(jsonPath("$.delivery.deliveryNumber").value("DLV-CURRENT-REPLACEMENT"))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));
    }

    @Test
    void ownOrderWithoutDeliveryReturnsClearNormalNoDeliveryState() throws Exception {
        mockMvc.perform(get("/api/deliveries/mine/orders/98513/tracking")
                        .cookie(sessionFor(98501, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(98513))
                .andExpect(jsonPath("$.hasDelivery").value(false))
                .andExpect(jsonPath("$.delivery").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void anotherCustomersOrderAndUnknownOrderUseSameOwnershipSafeNotFoundResult() throws Exception {
        Cookie customer = sessionFor(98501, UserRole.CUSTOMER);

        mockMvc.perform(get("/api/deliveries/mine/orders/98512/tracking").cookie(customer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/deliveries/mine/orders/999999/tracking").cookie(customer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void customerDeliveryTrackingRejectsWrongRoleAndGuest() throws Exception {
        mockMvc.perform(get("/api/deliveries/mine/orders/98511/tracking")
                        .cookie(sessionFor(98509, UserRole.SALES_OFFICER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deliveries/mine/orders/98511/tracking"))
                .andExpect(status().isUnauthorized());
    }

    private void seedUsersAndOrders() {
        insertUser(98501, "customer-one.tracking@example.com", "Customer One", UserRole.CUSTOMER);
        insertUser(98502, "customer-two.tracking@example.com", "Customer Two", UserRole.CUSTOMER);
        insertUser(98509, "sales.tracking@example.com", "Tracking Sales Officer", UserRole.SALES_OFFICER);
        insertOrder(98511, 98501, "ORD-CUSTOMER-TRACK-98511", "READY_FOR_DELIVERY");
        insertOrder(98512, 98502, "ORD-CUSTOMER-TRACK-98512", "READY_FOR_DELIVERY");
        insertOrder(98513, 98501, "ORD-CUSTOMER-NO-DELIVERY-98513", "IN_PRODUCTION");
    }

    private void insertOrder(long id, long customerId, String orderNumber, String status) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (?, ?, ?, ?)",
                id, customerId, orderNumber, status);
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
        jdbcTemplate.update("DELETE FROM deliveries WHERE id = 98531 OR order_id IN (98511, 98512, 98513)");
        jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id IN (98511, 98512, 98513)");
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (98511, 98512, 98513)");
        jdbcTemplate.update("DELETE FROM orders WHERE id IN (98511, 98512, 98513)");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (98501, 98502, 98509)");
    }
}

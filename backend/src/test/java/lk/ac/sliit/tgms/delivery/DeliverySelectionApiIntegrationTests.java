package lk.ac.sliit.tgms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
class DeliverySelectionApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

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
    void salesOfficerSeesOnlyReadyOrdersWithoutExistingDeliveryAndCanSearchCustomerFields() throws Exception {
        insertOrder(9101, 9001, "ORD-DELIVERY-READY-ASHA", "READY_FOR_DELIVERY", 9201, 2);
        insertOrder(9102, 9001, "ORD-STILL-PRODUCING", "IN_PRODUCTION", 9202, 1);
        insertOrder(9103, 9002, "ORD-DELIVERY-ALREADY-CREATED", "READY_FOR_DELIVERY", 9203, 1);
        insertOrder(9104, 9002, "ORD-DELIVERY-READY-NIMAL", "READY_FOR_DELIVERY", 9204, 1);
        insertDelivery(9301, "DEL-EXISTING-9301", 9103);
        Cookie sales = sessionFor(9991, UserRole.SALES_OFFICER);

        mockMvc.perform(get("/api/deliveries/eligible-orders").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].readyForDelivery").value(true))
                .andExpect(jsonPath("$[1].readyForDelivery").value(true));

        mockMvc.perform(get("/api/deliveries/eligible-orders")
                        .param("search", "Asha")
                        .cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(9101))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-DELIVERY-READY-ASHA"))
                .andExpect(jsonPath("$[0].customerId").value(9001))
                .andExpect(jsonPath("$[0].customerName").value("Asha Perera"))
                .andExpect(jsonPath("$[0].customerEmail").value("asha.delivery@example.com"))
                .andExpect(jsonPath("$[0].currentStatus").value("READY_FOR_DELIVERY"))
                .andExpect(jsonPath("$[0].itemCount").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value(5000.0))
                .andExpect(jsonPath("$[0].items[0].orderItemId").value(9201))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$[0].items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$[0].items[0].selectedColor").value("Blue"));
    }

    @Test
    void selectedReadyOrderIsRevalidatedAndSelectionDoesNotCreateDeliveryRecord() throws Exception {
        insertOrder(9101, 9001, "ORD-DELIVERY-READY-ASHA", "READY_FOR_DELIVERY", 9201, 2);

        mockMvc.perform(get("/api/deliveries/eligible-orders/9101")
                        .cookie(sessionFor(9991, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(9101))
                .andExpect(jsonPath("$.readyForDelivery").value(true))
                .andExpect(jsonPath("$.customerName").value("Asha Perera"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class))
                .isZero();
    }

    @Test
    void missingInvalidNotReadyAndAlreadyAssignedOrdersAreRejectedClearly() throws Exception {
        insertOrder(9102, 9001, "ORD-STILL-PRODUCING", "IN_PRODUCTION", 9202, 1);
        insertOrder(9103, 9002, "ORD-DELIVERY-ALREADY-CREATED", "READY_FOR_DELIVERY", 9203, 1);
        insertDelivery(9301, "DEL-EXISTING-9301", 9103);
        Cookie sales = sessionFor(9991, UserRole.SALES_OFFICER);

        mockMvc.perform(get("/api/deliveries/eligible-orders/0").cookie(sales))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.orderId").value("Select a valid positive order ID."));

        mockMvc.perform(get("/api/deliveries/eligible-orders/999999").cookie(sales))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/deliveries/eligible-orders/9102").cookie(sales))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_READY_FOR_DELIVERY"))
                .andExpect(jsonPath("$.error.fields.orderId").value(
                        org.hamcrest.Matchers.containsString("readyForDelivery=true")));

        mockMvc.perform(get("/api/deliveries/eligible-orders/9103").cookie(sales))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.fields.orderId").exists());
    }

    @Test
    void cancelledDeliveryHistoryDoesNotBlockCorrectedReplacementSelection() throws Exception {
        insertOrder(9101, 9001, "ORD-DELIVERY-REPLACEMENT", "READY_FOR_DELIVERY", 9201, 2);
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id,
                    scheduled_at, delivery_address, status
                ) VALUES (9301, 'DEL-CANCELLED-9301', 9101, NULL,
                          CURRENT_TIMESTAMP(6), 'Old incorrect address', 'CANCELLED')
                """);

        mockMvc.perform(get("/api/deliveries/eligible-orders/9101")
                        .cookie(sessionFor(9991, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(9101))
                .andExpect(jsonPath("$.readyForDelivery").value(true));
    }

    @Test
    void deliveryOrderSelectionIsSalesOfficerOnly() throws Exception {
        insertOrder(9101, 9001, "ORD-DELIVERY-READY-ASHA", "READY_FOR_DELIVERY", 9201, 2);

        mockMvc.perform(get("/api/deliveries/eligible-orders")
                        .cookie(sessionFor(9992, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deliveries/eligible-orders"))
                .andExpect(status().isUnauthorized());
    }

    private void seedBaseData() {
        insertUser(9001, "asha.delivery@example.com", "Asha Perera", UserRole.CUSTOMER);
        insertUser(9002, "nimal.delivery@example.com", "Nimal Silva", UserRole.CUSTOMER);
        insertUser(9991, "sales.delivery@example.com", "Delivery Sales Officer", UserRole.SALES_OFFICER);
        insertUser(9992, "customer.session@example.com", "Other Customer", UserRole.CUSTOMER);
        jdbcTemplate.update(
                "INSERT INTO garment_categories (id, name, status) VALUES (9401, 'Delivery Garments', 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO garment_products (id, category_id, name, status) VALUES (9402, 9401, 'Delivery Shirt', 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (9403, 9402, 'M', 'Blue', 2500.00, 'AVAILABLE')");
    }

    private void insertOrder(
            long orderId,
            long customerId,
            String orderNumber,
            String status,
            long orderItemId,
            int quantity) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (?, ?, ?, ?)",
                orderId, customerId, orderNumber, status);
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, quantity,
                    selected_size, selected_color, unit_price_snapshot
                ) VALUES (?, ?, 9402, 9403, ?, 'M', 'Blue', 2500.00)
                """,
                orderItemId, orderId, quantity);
    }

    private void insertDelivery(long deliveryId, String deliveryNumber, long orderId) {
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, status
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), '10 Main Street, Colombo', 'SCHEDULED')
                """,
                deliveryId, deliveryNumber, orderId, orderId);
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
        jdbcTemplate.update("DELETE FROM deliveries WHERE id IN (9301)");
        jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id IN (9101, 9102, 9103, 9104)");
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (9101, 9102, 9103, 9104)");
        jdbcTemplate.update("DELETE FROM orders WHERE id IN (9101, 9102, 9103, 9104)");
        jdbcTemplate.update("DELETE FROM garment_product_variants WHERE id = 9403");
        jdbcTemplate.update("DELETE FROM garment_products WHERE id = 9402");
        jdbcTemplate.update("DELETE FROM garment_categories WHERE id = 9401");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (9001, 9002, 9991, 9992)");
    }
}

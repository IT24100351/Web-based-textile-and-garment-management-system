package lk.ac.sliit.tgms.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
class DeliveryScheduleApiIntegrationTests {

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
    void salesOfficerSchedulesReadyOrderAndSavedRecordMatchesRequest() throws Exception {
        insertOrder(9611, 9601, "ORD-SCHEDULE-READY", "READY_FOR_DELIVERY", 9621);
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 9611,
                                  "scheduledAt": "2099-12-31T10:30:00Z",
                                  "deliveryAddress": " 10 Main   Street, Colombo ",
                                  "deliveryNotes": " Call   before arrival "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Delivery scheduled successfully."))
                .andExpect(jsonPath("$.delivery.id").isNumber())
                .andExpect(jsonPath("$.delivery.deliveryNumber").value(org.hamcrest.Matchers.startsWith("DLV-")))
                .andExpect(jsonPath("$.delivery.orderId").value(9611))
                .andExpect(jsonPath("$.delivery.scheduledAt").value("2099-12-31T10:30:00Z"))
                .andExpect(jsonPath("$.delivery.deliveryAddress").value("10 Main Street, Colombo"))
                .andExpect(jsonPath("$.delivery.deliveryNotes").value("Call before arrival"))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_id FROM deliveries WHERE order_id = 9611", Long.class)).isEqualTo(9611L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM deliveries WHERE order_id = 9611", String.class)).isEqualTo("SCHEDULED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scheduled_at FROM deliveries WHERE order_id = 9611", Timestamp.class).toInstant())
                .isEqualTo(Instant.parse("2099-12-31T10:30:00Z"));
    }

    @Test
    void missingPastAndBlankScheduleDetailsAreRejectedWithoutSaving() throws Exception {
        insertOrder(9611, 9601, "ORD-SCHEDULE-READY", "READY_FOR_DELIVERY", 9621);
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 9611,
                                  "scheduledAt": "2020-01-01T10:30:00Z",
                                  "deliveryAddress": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.scheduledAt").value("Delivery date and time must be in the future."))
                .andExpect(jsonPath("$.error.fields.deliveryAddress").value("Enter the delivery address."));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class)).isZero();
    }

    @Test
    void saveRevalidatesMissingNotReadyAndDuplicateOrders() throws Exception {
        insertOrder(9612, 9601, "ORD-SCHEDULE-NOT-READY", "IN_PRODUCTION", 9622);
        insertOrder(9613, 9602, "ORD-SCHEDULE-DUPLICATE", "READY_FOR_DELIVERY", 9623);
        insertDelivery(9631, "DLV-EXISTING-9631", 9613);
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);
        String validSchedule = """
                {"scheduledAt":"2099-12-31T10:30:00Z","deliveryAddress":"10 Main Street"}
                """;

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales).contentType("application/json")
                        .content(validSchedule.replace("{", "{\"orderId\":999999,")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales).contentType("application/json")
                        .content(validSchedule.replace("{", "{\"orderId\":9612,")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_READY_FOR_DELIVERY"));

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales).contentType("application/json")
                        .content(validSchedule.replace("{", "{\"orderId\":9613,")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_ALREADY_EXISTS"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class)).isEqualTo(1);
    }


    @Test
    void activeDeliveryWithinSixtyMinutesConflictsAndAlternativeAtBoundaryCanBeSaved() throws Exception {
        insertOrder(9611, 9601, "ORD-SCHEDULE-READY", "READY_FOR_DELIVERY", 9621);
        insertOrder(9612, 9602, "ORD-CONFLICTING-DELIVERY", "READY_FOR_DELIVERY", 9622);
        insertDelivery(9632, "DLV-CONFLICT-9632", 9612, Instant.parse("2099-12-31T10:30:00Z"), "SCHEDULED");
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 9611,
                                  "scheduledAt": "2099-12-31T11:15:00Z",
                                  "deliveryAddress": "10 Main Street"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.error.fields.scheduledAt")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("DLV-CONFLICT-9632"),
                                org.hamcrest.Matchers.containsString("at least 60 minutes"))));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 9611,
                                  "scheduledAt": "2099-12-31T11:30:00Z",
                                  "deliveryAddress": "10 Main Street"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.orderId").value(9611))
                .andExpect(jsonPath("$.delivery.scheduledAt").value("2099-12-31T11:30:00Z"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class)).isEqualTo(2);
    }

    @Test
    void cancelledDeliveryDoesNotBlockItsFormerScheduleWindow() throws Exception {
        insertOrder(9611, 9601, "ORD-SCHEDULE-READY", "READY_FOR_DELIVERY", 9621);
        insertOrder(9612, 9602, "ORD-CANCELLED-DELIVERY", "READY_FOR_DELIVERY", 9622);
        insertDelivery(9632, "DLV-CANCELLED-9632", 9612, Instant.parse("2099-12-31T10:30:00Z"), "CANCELLED");
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("""
                                {
                                  "orderId": 9611,
                                  "scheduledAt": "2099-12-31T10:45:00Z",
                                  "deliveryAddress": "10 Main Street"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.orderId").value(9611));
    }

    @Test
    void deliveryLifecyclePersistsAndDeliveredSynchronizesOrderToCompleted() throws Exception {
        insertOrder(9611, 9601, "ORD-DELIVERY-PROGRESS", "READY_FOR_DELIVERY", 9621);
        insertDelivery(9631, "DLV-PROGRESS-9631", 9611);
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(patch("/api/deliveries/9631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("OUT_FOR_DELIVERY"))
                .andExpect(jsonPath("$.delivery.allowedStatusTransitions[0]").value("DELIVERED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM deliveries WHERE id = 9631", String.class))
                .isEqualTo("OUT_FOR_DELIVERY");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 9611", String.class))
                .isEqualTo("READY_FOR_DELIVERY");

        mockMvc.perform(patch("/api/deliveries/9631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"))
                .andExpect(jsonPath("$.delivery.allowedStatusTransitions.length()").value(0));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 9611", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_status_history WHERE order_id = 9611 AND from_status = 'READY_FOR_DELIVERY' AND to_status = 'COMPLETED'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT changed_by_user_id FROM order_status_history WHERE order_id = 9611 AND to_status = 'COMPLETED'",
                        Long.class))
                .isEqualTo(9691L);
    }

    @Test
    void invalidDeliveryStatusesAndTransitionsAreRejectedWithoutChangingOrder() throws Exception {
        insertOrder(9611, 9601, "ORD-DELIVERY-INVALID", "READY_FOR_DELIVERY", 9621);
        insertDelivery(9631, "DLV-INVALID-9631", 9611);
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(patch("/api/deliveries/9631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.status").exists());

        mockMvc.perform(patch("/api/deliveries/9631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_STATUS_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.fields.status").value(
                        "Allowed next status: OUT_FOR_DELIVERY or CANCELLED."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM deliveries WHERE id = 9631", String.class))
                .isEqualTo("SCHEDULED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 9611", String.class))
                .isEqualTo("READY_FOR_DELIVERY");
    }

    @Test
    void cancelledDeliveryIsTerminalAndCannotBeReactivatedByProgressApi() throws Exception {
        insertOrder(9611, 9601, "ORD-DELIVERY-CANCEL", "READY_FOR_DELIVERY", 9621);
        insertDelivery(9631, "DLV-CANCEL-9631", 9611, Instant.parse("2099-12-31T10:30:00Z"), "CANCELLED");
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(patch("/api/deliveries/9631/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_STATUS_TRANSITION_NOT_ALLOWED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 9611", String.class))
                .isEqualTo("READY_FOR_DELIVERY");
    }

    @Test
    void orderCannotBeManuallyCompletedBeforeDeliveryIsDelivered() throws Exception {
        insertOrder(9611, 9601, "ORD-MANUAL-COMPLETE-BLOCKED", "READY_FOR_DELIVERY", 9621);
        insertDelivery(9631, "DLV-MANUAL-BLOCK-9631", 9611);
        Cookie sales = sessionFor(9691, UserRole.SALES_OFFICER);

        mockMvc.perform(patch("/api/orders/9611/status")
                        .cookie(sales)
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_STATUS_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.fields.status")
                        .value("Order completion is synchronized by Delivery Management after the delivery is marked delivered."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = 9611", String.class))
                .isEqualTo("READY_FOR_DELIVERY");
    }

    @Test
    void deliveryStatusUpdateIsSalesOfficerOnlyAndUnknownDeliveryIsSafe() throws Exception {
        insertOrder(9611, 9601, "ORD-DELIVERY-AUTH", "READY_FOR_DELIVERY", 9621);
        insertDelivery(9631, "DLV-AUTH-9631", 9611);

        mockMvc.perform(patch("/api/deliveries/9631/status")
                        .cookie(sessionFor(9692, UserRole.CUSTOMER))
                        .contentType("application/json")
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/999999/status")
                        .cookie(sessionFor(9691, UserRole.SALES_OFFICER))
                        .contentType("application/json")
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_NOT_FOUND"));
    }

    @Test
    void schedulingIsSalesOfficerOnly() throws Exception {
        insertOrder(9611, 9601, "ORD-SCHEDULE-READY", "READY_FOR_DELIVERY", 9621);
        String request = """
                {"orderId":9611,"scheduledAt":"2099-12-31T10:30:00Z","deliveryAddress":"10 Main Street"}
                """;

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sessionFor(9692, UserRole.CUSTOMER))
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deliveries")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    private void seedBaseData() {
        insertUser(9601, "asha.schedule@example.com", "Asha Perera", UserRole.CUSTOMER);
        insertUser(9602, "nimal.schedule@example.com", "Nimal Silva", UserRole.CUSTOMER);
        insertUser(9691, "sales.schedule@example.com", "Delivery Sales Officer", UserRole.SALES_OFFICER);
        insertUser(9692, "other.schedule@example.com", "Other Customer", UserRole.CUSTOMER);
        jdbcTemplate.update("INSERT INTO garment_categories (id, name, status) VALUES (9641, 'Schedule Garments', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_products (id, category_id, name, status) VALUES (9642, 9641, 'Schedule Shirt', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (9643, 9642, 'M', 'Blue', 2500.00, 'AVAILABLE')");
    }

    private void insertOrder(long orderId, long customerId, String orderNumber, String status, long itemId) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (?, ?, ?, ?)",
                orderId, customerId, orderNumber, status);
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, quantity,
                    selected_size, selected_color, unit_price_snapshot
                ) VALUES (?, ?, 9642, 9643, 2, 'M', 'Blue', 2500.00)
                """,
                itemId, orderId);
    }

    private void insertDelivery(long deliveryId, String deliveryNumber, long orderId) {
        insertDelivery(deliveryId, deliveryNumber, orderId, Instant.parse("2099-12-31T10:30:00Z"), "SCHEDULED");
    }

    private void insertDelivery(
            long deliveryId,
            String deliveryNumber,
            long orderId,
            Instant scheduledAt,
            String status) {
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (
                    id, delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address, status
                ) VALUES (?, ?, ?, ?, ?, '10 Main Street', ?)
                """,
                deliveryId, deliveryNumber, orderId,
                "CANCELLED".equals(status) ? null : orderId,
                Timestamp.from(scheduledAt), status);
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
        jdbcTemplate.update("DELETE FROM deliveries WHERE order_id IN (9611, 9612, 9613)");
        jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id IN (9611, 9612, 9613)");
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (9611, 9612, 9613)");
        jdbcTemplate.update("DELETE FROM orders WHERE id IN (9611, 9612, 9613)");
        jdbcTemplate.update("DELETE FROM garment_product_variants WHERE id = 9643");
        jdbcTemplate.update("DELETE FROM garment_products WHERE id = 9642");
        jdbcTemplate.update("DELETE FROM garment_categories WHERE id = 9641");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (9601, 9602, 9691, 9692)");
    }
}

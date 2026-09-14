package lk.ac.sliit.tgms.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.AuthTokenService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class OrderApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 4101 AND 4104 OR id BETWEEN 4998 AND 4999");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 4101 AND 4104 OR id BETWEEN 4998 AND 4999");
    }

    @Test
    void salesOfficerCreatesOrderWithValidatedCustomerAndProductSnapshots() throws Exception {
        insertUser(4101, "customer4101@example.com", "Asha Perera", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();

        mockMvc.perform(post("/api/orders")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 4101,
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 2},
                                    {"productId": 4201, "variantId": 4302, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Customer order created successfully."))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.matchesPattern(
                        "ORD-[A-F0-9]{20}")))
                .andExpect(jsonPath("$.customerId").value(4101))
                .andExpect(jsonPath("$.customer.fullName").value("Asha Perera"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("Classic Oxford Shirt"))
                .andExpect(jsonPath("$.items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$.items[0].selectedColor").value("White"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value("2490.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("4980.00"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[1].selectedSize").value("L"))
                .andExpect(jsonPath("$.items[1].selectedColor").value("Navy"))
                .andExpect(jsonPath("$.items[1].unitPriceSnapshot").value("2690.00"))
                .andExpect(jsonPath("$.items[1].lineTotal").value("2690.00"))
                .andExpect(jsonPath("$.totalAmount").value("7670.00"));

        long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE customer_id = ?", Long.class, 4101);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                        Integer.class,
                        orderId))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PENDING");

        jdbcTemplate.update(
                "UPDATE garment_product_variants SET price = ?, size = ?, color = ? WHERE id = ?",
                new BigDecimal("3990.00"),
                "XL",
                "Black",
                4301);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT unit_price_snapshot FROM order_items WHERE order_id = ? AND variant_id = ?",
                        BigDecimal.class,
                        orderId,
                        4301))
                .isEqualByComparingTo("2490.00");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT selected_size FROM order_items WHERE order_id = ? AND variant_id = ?",
                        String.class,
                        orderId,
                        4301))
                .isEqualTo("M");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT selected_color FROM order_items WHERE order_id = ? AND variant_id = ?",
                        String.class,
                        orderId,
                        4301))
                .isEqualTo("White");
    }

    @Test
    void salesOfficerCustomerSelectorReturnsOnlyActiveCustomersAndSupportsSearch() throws Exception {
        insertUser(4101, "asha@example.com", "Asha Perera", UserRole.CUSTOMER, true);
        insertUser(4102, "amal@example.com", "Amal Silva", UserRole.CUSTOMER, false);
        insertUser(4103, "supplier@example.com", "Asha Supplier", UserRole.SUPPLIER, true);
        insertUser(4104, "nimal@example.com", "Nimal Fernando", UserRole.CUSTOMER, true);

        mockMvc.perform(get("/api/orders/customers")
                        .queryParam("search", " asha ")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(4101))
                .andExpect(jsonPath("$[0].fullName").value("Asha Perera"))
                .andExpect(jsonPath("$[0].email").value("asha@example.com"));
    }

    @Test
    void invalidOrInactiveCustomerIsRejectedWithoutCreatingOrder() throws Exception {
        insertUser(4102, "inactive@example.com", "Inactive Customer", UserRole.CUSTOMER, false);
        insertUser(4103, "supplier@example.com", "Supplier Account", UserRole.SUPPLIER, true);
        insertCategoryProductAndVariants();

        for (long customerId : new long[] {4102, 4103, 999999}) {
            mockMvc.perform(post("/api/orders")
                            .cookie(sessionFor(UserRole.SALES_OFFICER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validOrder(customerId, 4301)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_SELECTABLE"))
                    .andExpect(jsonPath("$.error.fields.customerId").value(
                            "Select an active registered customer account before placing the order."));
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class))
                .isZero();
    }

    @Test
    void invalidProductInLaterLineDoesNotPartiallyCreateOrder() throws Exception {
        insertUser(4101, "customer@example.com", "Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();

        mockMvc.perform(post("/api/orders")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 4101,
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 2},
                                    {"productId": 4201, "variantId": 999999, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELECTABLE"))
                .andExpect(jsonPath("$.error.fields['items[1].variantId']").value(
                        "This product/size/color selection is no longer available. Choose an available garment variant."));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class))
                .isZero();
    }

    @Test
    void createOrderValidatesItemsAndRequiresSalesOfficerRole() throws Exception {
        insertUser(4101, "customer@example.com", "Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();

        mockMvc.perform(post("/api/orders")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":4101,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.items").exists());

        mockMvc.perform(post("/api/orders")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 4101,
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 0}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['items[0].quantity']").value(
                        "Quantity must be a positive whole number."));

        mockMvc.perform(post("/api/orders")
                        .cookie(sessionFor(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrder(4101, 4301)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/orders/customers")
                        .cookie(sessionFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrder(4101, 4301)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeredCustomerPlacesOwnOrderUsingAuthenticatedIdentity() throws Exception {
        insertUser(4101, "own@example.com", "Own Customer", UserRole.CUSTOMER, true);
        insertUser(4102, "other@example.com", "Other Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 4102,
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 3}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(4101))
                .andExpect(jsonPath("$.customer.fullName").value("Own Customer"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$.items[0].selectedColor").value("White"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value("2490.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("7470.00"))
                .andExpect(jsonPath("$.totalAmount").value("7470.00"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE customer_id = ?", Integer.class, 4101))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE customer_id = ?", Integer.class, 4102))
                .isZero();
    }

    @Test
    void registeredCustomerOrderRejectsUnavailableProductAndInvalidQuantityAtomically()
            throws Exception {
        insertUser(4101, "own@example.com", "Own Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();
        insertVariant(4303, "S", "Grey", "2190.00", "UNAVAILABLE");

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 1},
                                    {"productId": 4201, "variantId": 4303, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELECTABLE"))
                .andExpect(jsonPath("$.error.fields['items[1].variantId']").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class))
                .isZero();

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 0}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['items[0].quantity']").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
    }

    @Test
    void salesOfficerAndCustomerPathsReturnTheSameSharedItemValidationFields() throws Exception {
        insertUser(4101, "shared@example.com", "Shared Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();

        String invalidItems = """
                [
                  {"productId": null, "variantId": null, "quantity": 0}
                ]
                """;

        mockMvc.perform(post("/api/orders")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":4101,\"items\":" + invalidItems + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['items[0].productId']").value("Select a product."))
                .andExpect(jsonPath("$.error.fields['items[0].variantId']").value(
                        "Select an available size/color variant."))
                .andExpect(jsonPath("$.error.fields['items[0].quantity']").value(
                        "Quantity must be a positive whole number."));

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":" + invalidItems + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['items[0].productId']").value("Select a product."))
                .andExpect(jsonPath("$.error.fields['items[0].variantId']").value(
                        "Select an available size/color variant."))
                .andExpect(jsonPath("$.error.fields['items[0].quantity']").value(
                        "Quantity must be a positive whole number."));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
    }

    @Test
    void guestAndOtherRolesCannotSubmitRegisteredCustomerOrder() throws Exception {
        insertUser(4101, "own@example.com", "Own Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();
        String body = """
                {
                  "items": [
                    {"productId": 4201, "variantId": 4301, "quantity": 1}
                  ]
                }
                """;

        mockMvc.perform(post("/api/orders/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
    }

    @Test
    void inactiveCustomerSessionCannotPlaceOrder() throws Exception {
        insertUser(4101, "inactive@example.com", "Inactive Customer", UserRole.CUSTOMER, false);
        insertCategoryProductAndVariants();

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productId": 4201, "variantId": 4301, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SESSION"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .isZero();
    }

    @Test
    void staffCanSearchOrdersAndInspectStoredItemsAndHistoricalTotals() throws Exception {
        insertUser(4101, "asha@example.com", "Asha Perera", UserRole.CUSTOMER, true);
        insertUser(4102, "nimal@example.com", "Nimal Fernando", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-SEARCH-ASHA", "CONFIRMED");
        insertOrderItem(4601, 4501, 4301, 2, "M", "White", "2490.00");
        insertOrderItem(4602, 4501, 4302, 1, "L", "Navy", "2690.00");
        insertOrder(4502, 4102, "ORD-OTHER-NIMAL", "PENDING");
        insertOrderItem(4603, 4502, 4301, 1, "M", "White", "2490.00");

        mockMvc.perform(get("/api/orders")
                        .queryParam("search", " asha ")
                        .queryParam("status", "confirmed")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(4501))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-SEARCH-ASHA"))
                .andExpect(jsonPath("$[0].customerName").value("Asha Perera"))
                .andExpect(jsonPath("$[0].customerEmail").value("asha@example.com"))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].itemCount").value(2))
                .andExpect(jsonPath("$[0].totalAmount").value("7670.00"));

        mockMvc.perform(get("/api/orders")
                        .queryParam("search", "ORD-OTHER-NIMAL")
                        .cookie(sessionFor(UserRole.ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(4502));

        mockMvc.perform(get("/api/orders")
                        .queryParam("search", "nimal@example.com")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerId").value(4102));

        jdbcTemplate.update(
                "UPDATE garment_product_variants SET price = ?, size = ?, color = ? WHERE id = ?",
                new BigDecimal("9999.00"), "XXL", "Changed", 4301);

        mockMvc.perform(get("/api/orders/4501")
                        .cookie(sessionFor(UserRole.ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4501))
                .andExpect(jsonPath("$.customerId").value(4101))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$.items[0].selectedColor").value("White"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value("2490.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("4980.00"))
                .andExpect(jsonPath("$.items[1].lineTotal").value("2690.00"))
                .andExpect(jsonPath("$.totalAmount").value("7670.00"));
    }

    @Test
    void customerHistoryAndDetailAreStrictlyScopedToAuthenticatedCustomer() throws Exception {
        insertUser(4101, "own@example.com", "Own Customer", UserRole.CUSTOMER, true);
        insertUser(4102, "other@example.com", "Other Customer", UserRole.CUSTOMER, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-OWN-ONE", "PENDING");
        insertOrderItem(4601, 4501, 4301, 1, "M", "White", "2490.00");
        insertOrder(4502, 4102, "ORD-OTHER-TWO", "COMPLETED");
        insertOrderItem(4602, 4502, 4302, 2, "L", "Navy", "2690.00");

        Cookie ownSession = sessionForUser(4101, UserRole.CUSTOMER);
        mockMvc.perform(get("/api/orders/mine").cookie(ownSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(4501))
                .andExpect(jsonPath("$[0].customerId").value(4101))
                .andExpect(jsonPath("$[0].totalAmount").value("2490.00"));

        mockMvc.perform(get("/api/orders/mine/4501").cookie(ownSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-OWN-ONE"))
                .andExpect(jsonPath("$.items[0].variantId").value(4301));

        mockMvc.perform(get("/api/orders/mine/4502").cookie(ownSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/orders").cookie(ownSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void authorizedStaffCanAdvanceStatusAndCustomerTrackingSeesLatestAuditedState() throws Exception {
        insertUser(4101, "tracking@example.com", "Tracking Customer", UserRole.CUSTOMER, true);
        insertUser(4999, "sales4999@example.com", "Status Sales", UserRole.SALES_OFFICER, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-STATUS-TRACK", "PENDING");
        insertOrderItem(4601, 4501, 4301, 1, "M", "White", "2490.00");
        jdbcTemplate.update(
                "UPDATE orders SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf("2026-08-20 00:00:00"),
                4501);

        mockMvc.perform(get("/api/orders/4501")
                        .cookie(sessionForUser(4999, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.allowedStatusTransitions[0]").value("CONFIRMED"))
                .andExpect(jsonPath("$.allowedStatusTransitions[1]").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistory.length()").value(0));

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionForUser(4999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order status updated successfully."))
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.order.allowedStatusTransitions.length()").value(1))
                .andExpect(jsonPath("$.order.allowedStatusTransitions[0]").value("CANCELLED"))
                .andExpect(jsonPath("$.order.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.order.statusHistory[0].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.order.statusHistory[0].toStatus").value("CONFIRMED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, 4501))
                .isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                        Integer.class,
                        4501))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT changed_by_user_id FROM order_status_history WHERE order_id = ?",
                        Long.class,
                        4501))
                .isEqualTo(4999L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT updated_at > ? FROM orders WHERE id = ?",
                        Boolean.class,
                        java.sql.Timestamp.valueOf("2026-08-20 00:00:00"),
                        4501))
                .isTrue();

        mockMvc.perform(get("/api/orders/mine/4501")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.allowedStatusTransitions.length()").value(0))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("CONFIRMED"));

        mockMvc.perform(get("/api/orders/mine/4501/tracking")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.orderHistory[0].toStatus").value("CONFIRMED"));
    }

    @Test
    void invalidStatusAndInvalidTransitionsAreRejectedWithoutHistoryOrPartialUpdate() throws Exception {
        insertUser(4101, "invalidstatus@example.com", "Status Customer", UserRole.CUSTOMER, true);
        insertUser(4999, "sales4999@example.com", "Status Sales", UserRole.SALES_OFFICER, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-STATUS-INVALID", "CONFIRMED");
        insertOrderItem(4601, 4501, 4301, 1, "M", "White", "2490.00");

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionForUser(4999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.status").exists());

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionForUser(4999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_STATUS_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.fields.status").value(
                        "Production starts through Production Management. The only manual Order action currently available is CANCELLED."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, 4501))
                .isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                        Integer.class,
                        4501))
                .isZero();
    }

    @Test
    void completedAndCancelledOrdersAreTerminalAndUnauthorizedRolesCannotUpdateStatus()
            throws Exception {
        insertUser(4101, "terminal@example.com", "Terminal Customer", UserRole.CUSTOMER, true);
        insertUser(4998, "admin4998@example.com", "Status Admin", UserRole.ADMINISTRATOR, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-STATUS-CANCEL", "PENDING");
        insertOrderItem(4601, 4501, 4301, 1, "M", "White", "2490.00");

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionForUser(4998, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.order.allowedStatusTransitions.length()").value(0));

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionForUser(4998, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.fields.status").value(
                        "This order is in a terminal status and cannot be changed."));

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/orders/4501/status")
                        .cookie(sessionFor(UserRole.PRODUCTION_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/orders/4501/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void salesOfficerBillingWorkflowValidatesAmountsMethodsAndForwardOnlyPayments()
            throws Exception {
        insertUser(4101, "billing-customer@example.com", "Billing Customer", UserRole.CUSTOMER, true);
        insertUser(4998, "billing-production@example.com", "Billing Production", UserRole.PRODUCTION_MANAGER, true);
        insertUser(4999, "billing-sales@example.com", "Billing Sales", UserRole.SALES_OFFICER, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-BILLING-FLOW", "PENDING");
        insertOrderItem(4601, 4501, 4301, 2, "M", "White", "2490.00");
        Cookie salesSession = sessionForUser(4999, UserRole.SALES_OFFICER);

        mockMvc.perform(post("/api/orders/4501/invoice").cookie(salesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.invoiceGenerated").value(true))
                .andExpect(jsonPath("$.billing.invoice.totalAmount").value("4980.00"))
                .andExpect(jsonPath("$.billing.payment.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.billing.payment.amountPaid").value("0.00"));

        // Generating again is idempotent and cannot duplicate billing rows.
        mockMvc.perform(post("/api/orders/4501/invoice").cookie(salesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.invoice.totalAmount").value("4980.00"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_invoices WHERE order_id = ?",
                        Integer.class,
                        4501))
                .isEqualTo(1);

        mockMvc.perform(patch("/api/orders/4501/payment")
                        .cookie(salesSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "PAID",
                                  "amountPaid": "0.00",
                                  "paymentMethod": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.amountPaid").value(
                        "Paid amount must exactly match the invoice total."))
                .andExpect(jsonPath("$.error.fields.paymentMethod").value(
                        "Select how the payment was recorded."));

        mockMvc.perform(patch("/api/orders/4501/payment")
                        .cookie(salesSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "PARTIALLY_PAID",
                                  "amountPaid": "2000.00",
                                  "paymentMethod": "BANK_TRANSFER",
                                  "paymentReference": "CEFT-TEST-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.payment.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.billing.payment.amountPaid").value("2000.00"))
                .andExpect(jsonPath("$.billing.payment.paymentMethod").value("BANK_TRANSFER"));

        mockMvc.perform(patch("/api/orders/4501/payment")
                        .cookie(salesSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "PARTIALLY_PAID",
                                  "amountPaid": "1000.00",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.amountPaid").value(
                        "Amount paid cannot be reduced because refund/reversal handling is not enabled."));

        mockMvc.perform(patch("/api/orders/4501/payment")
                        .cookie(salesSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "PAID",
                                  "amountPaid": "4980.00",
                                  "paymentMethod": "CASH",
                                  "note": "Paid at the sales counter."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.payment.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.billing.payment.amountPaid").value("4980.00"))
                .andExpect(jsonPath("$.billing.payment.paymentMethod").value("CASH"));

        mockMvc.perform(get("/api/orders/mine/4501/billing")
                        .cookie(sessionForUser(4101, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.totalAmount").value("4980.00"))
                .andExpect(jsonPath("$.payment.paymentStatus").value("PAID"));

        mockMvc.perform(patch("/api/orders/4501/payment")
                        .cookie(sessionForUser(4998, UserRole.PRODUCTION_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"PAID\",\"amountPaid\":\"4980.00\",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerTrackingIsOwnershipScopedAndReturnsSafeOrderProgress() throws Exception {
        insertUser(4101, "tracking-owner@example.com", "Tracking Owner", UserRole.CUSTOMER, true);
        insertUser(4102, "tracking-other@example.com", "Tracking Other", UserRole.CUSTOMER, true);
        insertUser(4998, "tracking-production@example.com", "Tracking Production", UserRole.PRODUCTION_MANAGER, true);
        insertUser(4999, "tracking-sales@example.com", "Tracking Sales", UserRole.SALES_OFFICER, true);
        insertCategoryProductAndVariants();
        insertOrder(4501, 4101, "ORD-TRACK-OWN", "IN_PRODUCTION");
        insertOrderItem(4601, 4501, 4301, 2, "M", "White", "2490.00");
        insertOrder(4502, 4102, "ORD-TRACK-OTHER", "CONFIRMED");
        insertOrderItem(4602, 4502, 4302, 1, "L", "Navy", "2690.00");
        jdbcTemplate.update(
                """
                INSERT INTO order_status_history
                    (id, order_id, from_status, to_status, changed_by_user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                4701, 4501, "PENDING", "CONFIRMED", 4999);
        jdbcTemplate.update(
                """
                INSERT INTO order_status_history
                    (id, order_id, from_status, to_status, changed_by_user_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                4702, 4501, "CONFIRMED", "IN_PRODUCTION", 4998);

        Cookie ownerSession = sessionForUser(4101, UserRole.CUSTOMER);
        mockMvc.perform(get("/api/orders/mine/4501/tracking").cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(4501))
                .andExpect(jsonPath("$.orderNumber").value("ORD-TRACK-OWN"))
                .andExpect(jsonPath("$.currentStatus").value("IN_PRODUCTION"))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value("4980.00"))
                .andExpect(jsonPath("$.orderHistory.length()").value(2))
                .andExpect(jsonPath("$.orderHistory[0].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.orderHistory[1].toStatus").value("IN_PRODUCTION"))
                .andExpect(jsonPath("$.orderHistory[0].changedByUserId").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.customerEmail").doesNotExist());

        // Not-owned and unknown identifiers deliberately produce the same response so the API
        // does not reveal whether another customer's private order exists.
        mockMvc.perform(get("/api/orders/mine/4502/tracking").cookie(ownerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(get("/api/orders/mine/999999/tracking").cookie(ownerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(get("/api/orders/mine/4501/tracking")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/mine/4501/tracking"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffOrderSearchRejectsInvalidStatusAndUnauthorizedRoles() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .queryParam("status", "UNKNOWN")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.status").exists());

        mockMvc.perform(get("/api/orders")
                        .cookie(sessionFor(UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/mine"))
                .andExpect(status().isUnauthorized());
    }

    private void insertOrder(long id, long customerId, String orderNumber, String status) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (?, ?, ?, ?)",
                id, customerId, orderNumber, status);
    }

    private void insertOrderItem(
            long id,
            long orderId,
            long variantId,
            int quantity,
            String size,
            String color,
            String price) {
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, quantity,
                    selected_size, selected_color, unit_price_snapshot
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                orderId,
                4201,
                variantId,
                quantity,
                size,
                color,
                new BigDecimal(price));
    }

    private void insertCategoryProductAndVariants() {
        jdbcTemplate.update(
                "INSERT INTO garment_categories (id, name, status) VALUES (?, ?, ?)",
                4401,
                "Formal Wear",
                "ACTIVE");
        jdbcTemplate.update(
                "INSERT INTO garment_products (id, category_id, name, status) VALUES (?, ?, ?, ?)",
                4201,
                4401,
                "Classic Oxford Shirt",
                "ACTIVE");
        insertVariant(4301, "M", "White", "2490.00", "AVAILABLE");
        insertVariant(4302, "L", "Navy", "2690.00", "AVAILABLE");
    }

    private void insertVariant(long id, String size, String color, String price, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO garment_product_variants
                    (id, product_id, size, color, price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                4201,
                size,
                color,
                new BigDecimal(price),
                status);
    }

    private void insertUser(
            long id, String email, String fullName, UserRole role, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                email,
                "not-used",
                fullName,
                role.name(),
                active);
    }

    private Cookie sessionFor(UserRole role) {
        return sessionForUser(4999L, role);
    }

    private Cookie sessionForUser(long userId, UserRole role) {
        UserAccount account = new UserAccount(
                userId, "order-test@example.com", "not-used", "Order Test User", role, true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private String validOrder(long customerId, long variantId) {
        return """
                {
                  "customerId": %d,
                  "items": [
                    {"productId": 4201, "variantId": %d, "quantity": 1}
                  ]
                }
                """.formatted(customerId, variantId);
    }
}

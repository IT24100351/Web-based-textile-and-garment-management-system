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
class OrderBillingApiIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanup();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void salesOfficerGeneratesStableInvoiceAndRecordsManualPayment() throws Exception {
        seedUsersAndOrder("PENDING");

        mockMvc.perform(post("/api/orders/6501/invoice").cookie(sessionFor(6999, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invoice generated successfully."))
                .andExpect(jsonPath("$.billing.invoiceGenerated").value(true))
                .andExpect(jsonPath("$.billing.invoice.invoiceNumber").value(
                        org.hamcrest.Matchers.matchesPattern("INV-[A-F0-9]{20}")))
                .andExpect(jsonPath("$.billing.invoice.totalAmount").value("7670.00"))
                .andExpect(jsonPath("$.billing.payment.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.billing.payment.amountPaid").value("0.00"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_id FROM order_invoices WHERE order_id = 6501", Long.class))
                .isEqualTo(6501L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_id FROM order_payment_records WHERE order_id = 6501", Long.class))
                .isEqualTo(6501L);

        jdbcTemplate.update("UPDATE garment_product_variants SET price = 9999.00 WHERE id = 6301");
        mockMvc.perform(get("/api/orders/6501/billing").cookie(sessionFor(6999, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.totalAmount").value("7670.00"));

        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sessionFor(6999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "PARTIALLY_PAID",
                                  "amountPaid": "3000.00",
                                  "paymentMethod": "BANK_TRANSFER",
                                  "paymentReference": "BANK-REF-49",
                                  "note": "Manual bank transfer recorded"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.payment.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.billing.payment.amountPaid").value("3000.00"))
                .andExpect(jsonPath("$.billing.payment.paymentMethod").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.billing.payment.paymentReference").value("BANK-REF-49"));

        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sessionFor(6998, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentStatus": "PAID",
                                  "amountPaid": "7670.00",
                                  "paymentMethod": "CASH",
                                  "paymentReference": "CASH-6501"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.payment.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.billing.payment.amountPaid").value("7670.00"));


        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sessionFor(6998, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"UNPAID\",\"amountPaid\":\"0.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.paymentStatus").value(
                        "Payment status cannot move backward because refund/reversal handling is not enabled."));
    }

    @Test
    void paymentValidationAndInvoiceRequirementAreActionable() throws Exception {
        seedUsersAndOrder("PENDING");

        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sessionFor(6999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"PAID\",\"amountPaid\":\"7670.00\",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVOICE_REQUIRED"));

        mockMvc.perform(post("/api/orders/6501/invoice").cookie(sessionFor(6999, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sessionFor(6999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"PAID\",\"amountPaid\":\"7000.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.amountPaid").exists())
                .andExpect(jsonPath("$.error.fields.paymentMethod").exists());

        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sessionFor(6999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"SETTLED\",\"amountPaid\":\"0.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.paymentStatus").exists());
    }

    @Test
    void cancellationPreservesHistoryAndBillingButPaidOrderCannotBeCancelled() throws Exception {
        seedUsersAndOrder("PENDING");
        Cookie sales = sessionFor(6999, UserRole.SALES_OFFICER);

        mockMvc.perform(post("/api/orders/6501/invoice").cookie(sales)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/6501/status")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE id = 6501", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id = 6501", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_invoices WHERE order_id = 6501", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_payment_records WHERE order_id = 6501", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_status_history WHERE order_id = 6501", Integer.class)).isEqualTo(1);

        cleanup();
        seedUsersAndOrder("PENDING");
        mockMvc.perform(post("/api/orders/6501/invoice").cookie(sales)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/6501/payment")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentStatus\":\"PARTIALLY_PAID\",\"amountPaid\":\"1000.00\",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/orders/6501/status")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_CANCELLATION_BLOCKED"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = 6501", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void productionHandoffUsesStableIdsSelectionsAndReadinessWithoutGrantingWriteAccess() throws Exception {
        seedUsersAndOrder("CONFIRMED");
        Cookie production = sessionFor(6997, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(get("/api/orders/6501/handoff").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(6501))
                .andExpect(jsonPath("$.customerId").value(6101))
                .andExpect(jsonPath("$.currentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.readyForProduction").value(true))
                .andExpect(jsonPath("$.readyForDelivery").value(false))
                .andExpect(jsonPath("$.items[0].productId").value(6201))
                .andExpect(jsonPath("$.items[0].variantId").value(6301))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$.items[0].selectedColor").value("White"));

        mockMvc.perform(patch("/api/orders/6501/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PRODUCTION\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/6501/handoff").cookie(sessionFor(6101, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("UPDATE orders SET status = 'READY_FOR_DELIVERY' WHERE id = 6501");
        mockMvc.perform(get("/api/orders/6501/handoff").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForProduction").value(false))
                .andExpect(jsonPath("$.readyForDelivery").value(true));
    }

    @Test
    void customerCanReadOnlyOwnBillingAndCancelledOrderCannotBeNewlyInvoiced() throws Exception {
        seedUsersAndOrder("PENDING");
        Cookie sales = sessionFor(6999, UserRole.SALES_OFFICER);
        mockMvc.perform(post("/api/orders/6501/invoice").cookie(sales)).andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/mine/6501/billing").cookie(sessionFor(6101, UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice.invoiceNumber").exists());

        insertUser(6102, "other@example.com", "Other Customer", UserRole.CUSTOMER, true);
        mockMvc.perform(get("/api/orders/mine/6501/billing").cookie(sessionFor(6102, UserRole.CUSTOMER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = 6501");
        jdbcTemplate.update("DELETE FROM order_payment_records WHERE order_id = 6501");
        jdbcTemplate.update("DELETE FROM order_invoices WHERE order_id = 6501");
        mockMvc.perform(post("/api/orders/6501/invoice").cookie(sales))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_CANCELLED"));
    }

    private void seedUsersAndOrder(String status) {
        insertUser(6101, "billing-customer@example.com", "Billing Customer", UserRole.CUSTOMER, true);
        insertUser(6999, "billing-sales@example.com", "Billing Sales", UserRole.SALES_OFFICER, true);
        insertUser(6998, "billing-admin@example.com", "Billing Admin", UserRole.ADMINISTRATOR, true);
        insertUser(6997, "billing-production@example.com", "Billing Production", UserRole.PRODUCTION_MANAGER, true);
        jdbcTemplate.update("INSERT INTO garment_categories (id, name, status) VALUES (6401, 'Billing Wear', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_products (id, category_id, name, status) VALUES (6201, 6401, 'Billing Shirt', 'ACTIVE')");
        jdbcTemplate.update("INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (6301, 6201, 'M', 'White', 2490.00, 'AVAILABLE')");
        jdbcTemplate.update("INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (6302, 6201, 'L', 'Navy', 2690.00, 'AVAILABLE')");
        jdbcTemplate.update("INSERT INTO orders (id, customer_id, order_number, status) VALUES (6501, 6101, 'ORD-BILLING-6501', ?)", status);
        jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, variant_id, quantity, selected_size, selected_color, unit_price_snapshot) VALUES (6601, 6501, 6201, 6301, 2, 'M', 'White', 2490.00)");
        jdbcTemplate.update("INSERT INTO order_items (id, order_id, product_id, variant_id, quantity, selected_size, selected_color, unit_price_snapshot) VALUES (6602, 6501, 6201, 6302, 1, 'L', 'Navy', 2690.00)");
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 6101 AND 6102 OR id BETWEEN 6997 AND 6999");
    }

    private void insertUser(long id, String email, String fullName, UserRole role, boolean active) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, role, is_active) VALUES (?, ?, 'not-used', ?, ?, ?)",
                id, email, fullName, role.name(), active);
    }

    private Cookie sessionFor(long userId, UserRole role) {
        UserAccount account = new UserAccount(
                userId, "session-" + userId + "@example.com", "not-used", "Session User", role, true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }
}

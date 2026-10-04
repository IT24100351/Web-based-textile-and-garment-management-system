package lk.ac.sliit.tgms.quotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class QuotationApiIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        clean();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void salesOfficerCreatesIssuedQuotationWithStableSnapshotsAndTotal() throws Exception {
        insertUser(5101, "asha@example.com", "Asha Perera", UserRole.CUSTOMER, true);
        insertUser(5999, "sales@example.com", "Sales Officer", UserRole.SALES_OFFICER, true);
        insertCatalog();

        mockMvc.perform(post("/api/quotations")
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 5101,
                                  "items": [
                                    {"productId": 5201, "variantId": 5301, "quantity": 2},
                                    {"productId": 5201, "variantId": 5302, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Customer quotation issued successfully."))
                .andExpect(jsonPath("$.quotationNumber").value(
                        org.hamcrest.Matchers.matchesPattern("QUO-[A-F0-9]{20}")))
                .andExpect(jsonPath("$.customer.id").value(5101))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productName").value("Classic Oxford Shirt"))
                .andExpect(jsonPath("$.items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$.items[0].selectedColor").value("White"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value("2490.00"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("4980.00"))
                .andExpect(jsonPath("$.totalAmount").value("7670.00"));

        long quotationId = jdbcTemplate.queryForObject(
                "SELECT id FROM quotations WHERE customer_id = 5101", Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT issued_by_user_id FROM quotations WHERE id = ?", Long.class, quotationId))
                .isEqualTo(5999L);

        jdbcTemplate.update(
                "UPDATE garment_products SET name = ? WHERE id = ?", "Renamed Shirt", 5201);
        jdbcTemplate.update(
                "UPDATE garment_product_variants SET price = ?, size = ?, color = ? WHERE id = ?",
                new BigDecimal("9999.00"), "XL", "Black", 5301);

        mockMvc.perform(get("/api/quotations/{id}", quotationId)
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Classic Oxford Shirt"))
                .andExpect(jsonPath("$.items[0].selectedSize").value("M"))
                .andExpect(jsonPath("$.items[0].selectedColor").value("White"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value("2490.00"))
                .andExpect(jsonPath("$.totalAmount").value("7670.00"));
    }

    @Test
    void salesOfficerCanListAndViewIssuedQuotations() throws Exception {
        insertUser(5101, "customer@example.com", "Customer One", UserRole.CUSTOMER, true);
        insertUser(5999, "sales@example.com", "Sales Officer", UserRole.SALES_OFFICER, true);
        insertCatalog();
        createQuotation();

        mockMvc.perform(get("/api/quotations")
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Customer One"))
                .andExpect(jsonPath("$[0].itemCount").value(1))
                .andExpect(jsonPath("$[0].totalAmount").value("2490.00"));
    }

    @Test
    void invalidCustomerOrUnavailableProductCannotCreateQuotation() throws Exception {
        insertUser(5101, "inactive@example.com", "Inactive", UserRole.CUSTOMER, false);
        insertUser(5999, "sales@example.com", "Sales Officer", UserRole.SALES_OFFICER, true);
        insertCatalog();

        mockMvc.perform(post("/api/quotations")
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuotation(5101, 5301)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_SELECTABLE"));

        jdbcTemplate.update("UPDATE garment_product_variants SET status = 'UNAVAILABLE' WHERE id = 5301");
        insertUser(5102, "active@example.com", "Active", UserRole.CUSTOMER, true);
        mockMvc.perform(post("/api/quotations")
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuotation(5102, 5301)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELECTABLE"))
                .andExpect(jsonPath("$.error.fields['items[0].variantId']").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quotations", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quotation_items", Integer.class)).isZero();
    }

    @Test
    void invalidLaterLineDoesNotPartiallyIssueQuotation() throws Exception {
        insertUser(5101, "customer@example.com", "Customer", UserRole.CUSTOMER, true);
        insertUser(5999, "sales@example.com", "Sales Officer", UserRole.SALES_OFFICER, true);
        insertCatalog();

        mockMvc.perform(post("/api/quotations")
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": 5101,
                                  "items": [
                                    {"productId": 5201, "variantId": 5301, "quantity": 1},
                                    {"productId": 5201, "variantId": 999999, "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_SELECTABLE"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quotations", Integer.class)).isZero();
    }

    @Test
    void quotationCreationAndViewsAreSalesOfficerOnly() throws Exception {
        insertUser(5101, "customer@example.com", "Customer", UserRole.CUSTOMER, true);
        insertUser(5999, "sales@example.com", "Sales Officer", UserRole.SALES_OFFICER, true);
        insertCatalog();

        mockMvc.perform(post("/api/quotations")
                        .cookie(sessionForUser(5101, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuotation(5101, 5301)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/quotations")
                        .cookie(sessionForUser(5101, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/quotations"))
                .andExpect(status().isUnauthorized());
    }

    private void createQuotation() throws Exception {
        mockMvc.perform(post("/api/quotations")
                        .cookie(sessionForUser(5999, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validQuotation(5101, 5301)))
                .andExpect(status().isCreated());
    }

    private void insertCatalog() {
        jdbcTemplate.update(
                "INSERT INTO garment_categories (id, name, status) VALUES (5401, 'Formal Wear', 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO garment_products (id, category_id, name, status) VALUES (5201, 5401, 'Classic Oxford Shirt', 'ACTIVE')");
        insertVariant(5301, "M", "White", "2490.00");
        insertVariant(5302, "L", "Navy", "2690.00");
    }

    private void insertVariant(long id, String size, String color, String price) {
        jdbcTemplate.update(
                """
                INSERT INTO garment_product_variants (id, product_id, size, color, price, status)
                VALUES (?, 5201, ?, ?, ?, 'AVAILABLE')
                """,
                id, size, color, new BigDecimal(price));
    }

    private void insertUser(long id, String email, String name, UserRole role, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                VALUES (?, ?, 'not-used', ?, ?, ?)
                """,
                id, email, name, role.name(), active);
    }

    private Cookie sessionForUser(long userId, UserRole role) {
        UserAccount account = new UserAccount(
                userId, "quote-test@example.com", "not-used", "Quote Test User", role, true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private String validQuotation(long customerId, long variantId) {
        return """
                {
                  "customerId": %d,
                  "items": [{"productId": 5201, "variantId": %d, "quantity": 1}]
                }
                """.formatted(customerId, variantId);
    }

    private void clean() {
        jdbcTemplate.update("DELETE FROM quotation_items");
        jdbcTemplate.update("DELETE FROM quotations");
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 5101 AND 5102 OR id = 5999");
    }
}

package lk.ac.sliit.tgms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SecurityBoundaryIntegrationTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuthTokenService authTokenService;

    private MockMvc mockMvc;
    private final Map<UserRole, Long> userIds = new EnumMap<>(UserRole.class);

    @BeforeEach
    void setUp() {
        cleanup();
        userIds.clear();
        for (UserRole role : UserRole.values()) {
            userIds.put(role, insertUser(role));
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void protectedMajorActionsRequireAuthenticationAndEnforceRoleGuards() throws Exception {
        List<SecuredAction> actions = List.of(
                new SecuredAction(
                        "administrator role management",
                        UserRole.ADMINISTRATOR,
                        UserRole.CUSTOMER,
                        () -> patch("/api/admin/users/999999/role")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"SUPPLIER\"}")),
                new SecuredAction(
                        "supplier-owned supply update",
                        UserRole.SUPPLIER,
                        UserRole.CUSTOMER,
                        () -> put("/api/material-supplies/999999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"quantity":1.000,"unitPrice":100.00,"deliveryLeadTimeDays":1,"deliveryNotes":"TGMS-80"}
                                        """)),
                new SecuredAction(
                        "inventory delete",
                        UserRole.INVENTORY_MANAGER,
                        UserRole.SALES_OFFICER,
                        () -> delete("/api/inventory-materials/999999")),
                new SecuredAction(
                        "garment product delete",
                        UserRole.SALES_OFFICER,
                        UserRole.ADMINISTRATOR,
                        () -> delete("/api/products/999999")),
                new SecuredAction(
                        "order status update",
                        UserRole.SALES_OFFICER,
                        UserRole.INVENTORY_MANAGER,
                        () -> patch("/api/orders/999999/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"CONFIRMED\"}")),
                new SecuredAction(
                        "quotation creation",
                        UserRole.SALES_OFFICER,
                        UserRole.CUSTOMER,
                        () -> post("/api/quotations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"customerId":999999,"items":[{"productId":1,"variantId":1,"quantity":1}]}
                                        """)),
                new SecuredAction(
                        "production status update",
                        UserRole.PRODUCTION_MANAGER,
                        UserRole.SALES_OFFICER,
                        () -> patch("/api/production/tasks/999999/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"IN_PROGRESS\"}")),
                new SecuredAction(
                        "delivery status update",
                        UserRole.SALES_OFFICER,
                        UserRole.CUSTOMER,
                        () -> patch("/api/deliveries/999999/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"OUT_FOR_DELIVERY\"}")));

        for (SecuredAction action : actions) {
            int allowedStatus = mockMvc.perform(action.request().get().cookie(sessionFor(action.allowedRole())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(allowedStatus)
                    .as("%s must pass the authorization layer for %s", action.allowedRole(), action.name())
                    .isNotIn(401, 403);

            mockMvc.perform(action.request().get().cookie(sessionFor(action.deniedRole())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            mockMvc.perform(action.request().get())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void authenticationByDefaultProtectsApiRoutesWhileIntentionalPublicCatalogRemainsPublic()
            throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/catalog/999999"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/a-future-controller-without-a-role-guard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedPathAndQueryValuesReturnSafeValidationErrorsInsteadOfServerErrors()
            throws Exception {
        mockMvc.perform(get("/api/orders/not-a-number")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.orderId").exists());

        mockMvc.perform(get("/api/search")
                        .queryParam("search", "shirt")
                        .queryParam("page", "not-a-number")
                        .cookie(sessionFor(UserRole.CUSTOMER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.page").exists());

        mockMvc.perform(get("/api/search")
                        .cookie(sessionFor(UserRole.CUSTOMER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.search").exists());

        mockMvc.perform(get("/api/inventory-materials/1/availability")
                        .queryParam("requiredQuantity", "not-a-decimal")
                        .cookie(sessionFor(UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.requiredQuantity").exists());
    }

    @Test
    void corsAllowsOnlyConfiguredFrontendOriginAndPreflightDoesNotRequireLogin() throws Exception {
        mockMvc.perform(options("/api/orders")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/orders")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private Cookie sessionFor(UserRole role) {
        long id = userIds.get(role);
        UserAccount account = new UserAccount(
                id,
                "tgms80-" + role.name().toLowerCase() + "@example.com",
                "$2a$10$not-used-by-security-tests",
                "TGMS 80 " + role.name(),
                role,
                true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private long insertUser(UserRole role) {
        String email = "tgms80-" + role.name().toLowerCase() + "@example.com";
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, full_name, role, is_active) VALUES (?, ?, ?, ?, TRUE)",
                email,
                "$2a$10$not-used-by-security-tests",
                "TGMS 80 " + role.name(),
                role.name());
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'tgms80-%'");
    }

    private record SecuredAction(
            String name,
            UserRole allowedRole,
            UserRole deniedRole,
            Supplier<MockHttpServletRequestBuilder> request) {}
}

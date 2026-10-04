package lk.ac.sliit.tgms.admin;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AdminUserManagementApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void administratorCanCreateListAndReadInternalAccountsWithoutExposingSecrets() throws Exception {
        insertUser(8701, "tgms70-admin@example.com", "TGMS 70 Admin", UserRole.ADMINISTRATOR, true);
        Cookie admin = sessionFor(8701, UserRole.ADMINISTRATOR);

        mockMvc.perform(post("/api/admin/users")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Inventory Team Member",
                                  "email": "TGMS70-INVENTORY@EXAMPLE.COM",
                                  "password": "StrongPass123!",
                                  "role": "INVENTORY_MANAGER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Internal user account created successfully."))
                .andExpect(jsonPath("$.user.fullName").value("Inventory Team Member"))
                .andExpect(jsonPath("$.user.email").value("tgms70-inventory@example.com"))
                .andExpect(jsonPath("$.user.role").value("INVENTORY_MANAGER"))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        Long createdId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                "tgms70-inventory@example.com");

        mockMvc.perform(get("/api/admin/users")
                        .cookie(admin)
                        .param("search", "inventory team")
                        .param("role", "INVENTORY_MANAGER")
                        .param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(createdId))
                .andExpect(jsonPath("$[0].email").value("tgms70-inventory@example.com"));

        mockMvc.perform(get("/api/admin/users/{userId}", createdId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.role").value("INVENTORY_MANAGER"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void publicRegistrationRoleBoundaryAndAdminAuthorizationAreEnforced() throws Exception {
        insertUser(8711, "tgms70-admin-2@example.com", "TGMS 70 Admin", UserRole.ADMINISTRATOR, true);
        insertUser(8712, "tgms70-customer@example.com", "TGMS 70 Customer", UserRole.CUSTOMER, true);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/admin/users").cookie(sessionFor(8712, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/admin/users")
                        .cookie(sessionFor(8711, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Not An Internal Account",
                                  "email": "tgms70-invalid-customer@example.com",
                                  "password": "StrongPass123!",
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.role")
                        .value("Customer accounts must use public registration."));

        mockMvc.perform(post("/api/admin/users")
                        .cookie(sessionFor(8711, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Duplicate User",
                                  "email": "tgms70-customer@example.com",
                                  "password": "StrongPass123!",
                                  "role": "SALES_OFFICER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void roleChangesImmediatelyAffectAuthorizationEvenForAnAlreadyIssuedSession() throws Exception {
        insertUser(8721, "tgms70-admin-3@example.com", "TGMS 70 Admin", UserRole.ADMINISTRATOR, true);
        insertUser(8722, "tgms70-sales@example.com", "TGMS 70 Sales", UserRole.SALES_OFFICER, true);
        Cookie admin = sessionFor(8721, UserRole.ADMINISTRATOR);
        Cookie existingSalesSession = sessionFor(8722, UserRole.SALES_OFFICER);

        mockMvc.perform(get("/api/role-access/sales-officer").cookie(existingSalesSession))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/8722/role")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PRODUCTION_MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("PRODUCTION_MANAGER"));

        mockMvc.perform(get("/api/role-access/sales-officer").cookie(existingSalesSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/role-access/production-manager").cookie(existingSalesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRODUCTION_MANAGER"));
    }

    @Test
    void deactivationInvalidatesExistingSessionsAndAdministratorCannotRemoveOwnAccess() throws Exception {
        insertUser(8731, "tgms70-admin-4@example.com", "TGMS 70 Admin", UserRole.ADMINISTRATOR, true);
        insertUser(8732, "tgms70-supplier@example.com", "TGMS 70 Supplier", UserRole.SUPPLIER, true);
        Cookie admin = sessionFor(8731, UserRole.ADMINISTRATOR);
        Cookie supplier = sessionFor(8732, UserRole.SUPPLIER);

        mockMvc.perform(patch("/api/admin/users/8732/status")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.active").value(false));

        mockMvc.perform(get("/api/role-access/supplier").cookie(supplier))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SESSION"));

        mockMvc.perform(patch("/api/admin/users/8731/role")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SALES_OFFICER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_SELF_ACCESS_REQUIRED"));

        mockMvc.perform(patch("/api/admin/users/8731/status")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_SELF_ACCESS_REQUIRED"));
    }

    @Test
    void adminQueryAndPathValidationReturnSafeClientErrors() throws Exception {
        insertUser(8741, "tgms70-admin-5@example.com", "TGMS 70 Admin", UserRole.ADMINISTRATOR, true);
        Cookie admin = sessionFor(8741, UserRole.ADMINISTRATOR);

        mockMvc.perform(get("/api/admin/users").cookie(admin).param("role", "root"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/admin/users").cookie(admin).param("active", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.active").value("Active must be true or false."));

        mockMvc.perform(get("/api/admin/users/0").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.userId")
                        .value("User ID must be a positive integer."));
    }

    private void insertUser(long id, String email, String fullName, UserRole role, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                fullName,
                role.name(),
                active);
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
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'tgms70-%'");
    }
}

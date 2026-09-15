package lk.ac.sliit.tgms.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class SupplierProfileApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanSupplierAccounts();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        cleanSupplierAccounts();
    }

    @Test
    void supplierCanCreateLoadAndUpdateOwnProfileWithoutChangingStableIds() throws Exception {
        UserAccount supplier = insertUser(6101, "supplier6101@example.com", UserRole.SUPPLIER);
        Cookie session = sessionFor(supplier, UserRole.SUPPLIER);

        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName": "  Lanka   Textile Supplies  ",
                                  "contactPhone": " +94 77 123 4567 ",
                                  "address": " 18   Industrial Estate, Colombo "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message")
                        .value("Supplier profile created successfully."))
                .andExpect(jsonPath("$.profile.id").isNumber())
                .andExpect(jsonPath("$.profile.userId").value(6101))
                .andExpect(jsonPath("$.profile.businessName")
                        .value("Lanka Textile Supplies"))
                .andExpect(jsonPath("$.profile.contactPhone").value("+94 77 123 4567"))
                .andExpect(jsonPath("$.profile.address")
                        .value("18 Industrial Estate, Colombo"));

        long supplierId = jdbcTemplate.queryForObject(
                "SELECT id FROM supplier_profiles WHERE user_id = ?", Long.class, 6101);
        mockMvc.perform(get("/api/supplier-profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplierId))
                .andExpect(jsonPath("$.userId").value(6101))
                .andExpect(jsonPath("$.businessName").value("Lanka Textile Supplies"));

        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName": "Lanka Textile & Thread Supplies",
                                  "contactPhone": "011 234 5678",
                                  "address": "42 Mill Road, Colombo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Supplier profile updated successfully."))
                .andExpect(jsonPath("$.profile.id").value(supplierId))
                .andExpect(jsonPath("$.profile.userId").value(6101))
                .andExpect(jsonPath("$.profile.businessName")
                        .value("Lanka Textile & Thread Supplies"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM supplier_profiles WHERE user_id = ?",
                        Integer.class,
                        6101))
                .isEqualTo(1);
    }

    @Test
    void supplierSessionCanOnlyReadAndUpdateItsOwnProfile() throws Exception {
        UserAccount first = insertUser(6201, "supplier6201@example.com", UserRole.SUPPLIER);
        UserAccount second = insertUser(6202, "supplier6202@example.com", UserRole.SUPPLIER);
        insertProfile(7201, first.id(), "First Supplier");
        insertProfile(7202, second.id(), "Second Supplier");

        Cookie firstSession = sessionFor(first, UserRole.SUPPLIER);
        mockMvc.perform(get("/api/supplier-profile").cookie(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7201))
                .andExpect(jsonPath("$.userId").value(6201))
                .andExpect(jsonPath("$.businessName").value("First Supplier"));

        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 7202,
                                  "userId": 6202,
                                  "businessName": "First Supplier Updated",
                                  "contactPhone": "011 111 1111",
                                  "address": "First Supplier Address"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(7201))
                .andExpect(jsonPath("$.profile.userId").value(6201));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT business_name FROM supplier_profiles WHERE id = ?",
                        String.class,
                        7202))
                .isEqualTo("Second Supplier");
    }

    @Test
    void supplierProfileRejectsMissingOrInvalidValuesClearly() throws Exception {
        UserAccount supplier = insertUser(6301, "supplier6301@example.com", UserRole.SUPPLIER);
        Cookie session = sessionFor(supplier, UserRole.SUPPLIER);

        mockMvc.perform(get("/api/supplier-profile").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUPPLIER_PROFILE_NOT_FOUND"));

        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName": "   ",
                                  "contactPhone": "",
                                  "address": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.businessName")
                        .value("Business name is required."))
                .andExpect(jsonPath("$.error.fields.contactPhone")
                        .value("Contact phone is required."))
                .andExpect(jsonPath("$.error.fields.address").value("Address is required."));
    }

    @Test
    void backendRequiresSupplierRoleAndAnActiveSupplierAccount() throws Exception {
        UserAccount customer = insertUser(6401, "customer6401@example.com", UserRole.CUSTOMER);
        UserAccount staleSupplier = insertUser(
                6402, "customer6402@example.com", UserRole.CUSTOMER);
        UserAccount inactiveSupplier = insertUser(
                6403, "supplier6403@example.com", UserRole.SUPPLIER);
        jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = ?", 6403);

        mockMvc.perform(get("/api/supplier-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/supplier-profile")
                        .cookie(sessionFor(customer, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(sessionFor(staleSupplier, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProfileRequest("Stale Role Supplier")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/supplier-profile")
                        .cookie(sessionFor(inactiveSupplier, UserRole.SUPPLIER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SESSION"));
    }

    private UserAccount insertUser(long id, String email, UserRole storedRole) {
        jdbcTemplate.update(
                """
                INSERT INTO users
                    (id, email, password_hash, full_name, role, is_active)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """,
                id,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                "Supplier Test Account",
                storedRole.name());
        return new UserAccount(
                id, email, "not-used", "Supplier Test Account", storedRole, true);
    }

    private void insertProfile(long id, long userId, String businessName) {
        jdbcTemplate.update(
                """
                INSERT INTO supplier_profiles
                    (id, user_id, business_name, contact_phone, address)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                userId,
                businessName,
                "011 000 0000",
                businessName + " Address");
    }

    private Cookie sessionFor(UserAccount storedAccount, UserRole tokenRole) {
        UserAccount tokenAccount = new UserAccount(
                storedAccount.id(),
                storedAccount.email(),
                storedAccount.passwordHash(),
                storedAccount.fullName(),
                tokenRole,
                storedAccount.active());
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(tokenAccount));
    }

    private String validProfileRequest(String businessName) {
        return """
                {
                  "businessName": "%s",
                  "contactPhone": "011 222 2222",
                  "address": "Valid Supplier Address"
                }
                """.formatted(businessName);
    }

    private void cleanSupplierAccounts() {
        jdbcTemplate.update("DELETE FROM supplier_profiles");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 6101 AND 6499");
    }
}

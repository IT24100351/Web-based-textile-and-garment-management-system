package lk.ac.sliit.tgms.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class MaterialSupplyApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanTestAccounts();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        cleanTestAccounts();
    }

    @Test
    void supplierCreatesNormalizedActiveSupplyAndReceivesSavedRecord() throws Exception {
        UserAccount account = insertUser(6501, "supplier6501@example.com", UserRole.SUPPLIER);
        long supplierId = insertProfile(7501, account.id(), "Central Fabric Supplier");

        mockMvc.perform(post("/api/material-supplies")
                        .cookie(sessionFor(account, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "  FAB-COT-001  ",
                                  "materialName": "  Cotton   twill fabric  ",
                                  "materialDescription": "  Durable  240 GSM cotton twill  ",
                                  "quantity": 1250.750,
                                  "unitOfMeasure": " metre ",
                                  "unitPrice": 845.50,
                                  "deliveryLeadTimeDays": 7,
                                  "deliveryNotes": " Deliver to the main receiving bay "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message")
                        .value("Material supply created successfully."))
                .andExpect(jsonPath("$.supply.id").isNumber())
                .andExpect(jsonPath("$.supply.supplierId").value(supplierId))
                .andExpect(jsonPath("$.supply.materialCode").value("FAB-COT-001"))
                .andExpect(jsonPath("$.supply.materialName").value("Cotton twill fabric"))
                .andExpect(jsonPath("$.supply.materialDescription")
                        .value("Durable 240 GSM cotton twill"))
                .andExpect(jsonPath("$.supply.quantity").value("1250.750"))
                .andExpect(jsonPath("$.supply.unitOfMeasure").value("metre"))
                .andExpect(jsonPath("$.supply.unitPrice").value("845.50"))
                .andExpect(jsonPath("$.supply.deliveryLeadTimeDays").value(7))
                .andExpect(jsonPath("$.supply.deliveryNotes")
                        .value("Deliver to the main receiving bay"))
                .andExpect(jsonPath("$.supply.status").value("ACTIVE"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_supplies WHERE supplier_id = ?",
                        Integer.class,
                        supplierId))
                .isEqualTo(1);
    }

    @Test
    void serverIgnoresForgedOwnershipFieldsAndUsesAuthenticatedSupplierProfile()
            throws Exception {
        UserAccount first = insertUser(6601, "supplier6601@example.com", UserRole.SUPPLIER);
        UserAccount second = insertUser(6602, "supplier6602@example.com", UserRole.SUPPLIER);
        long firstSupplierId = insertProfile(7601, first.id(), "First Material Supplier");
        long secondSupplierId = insertProfile(7602, second.id(), "Second Material Supplier");

        mockMvc.perform(post("/api/material-supplies")
                        .cookie(sessionFor(first, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest(
                                "\"id\": 9999, \"supplierId\": " + secondSupplierId + ",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supply.supplierId").value(firstSupplierId));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_supplies WHERE supplier_id = ?",
                        Integer.class,
                        firstSupplierId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_supplies WHERE supplier_id = ?",
                        Integer.class,
                        secondSupplierId))
                .isZero();
    }

    @Test
    void invalidAndDuplicateSupplyValuesAreRejectedClearly() throws Exception {
        UserAccount account = insertUser(6701, "supplier6701@example.com", UserRole.SUPPLIER);
        long supplierId = insertProfile(7701, account.id(), "Validation Supplier");
        Cookie session = sessionFor(account, UserRole.SUPPLIER);

        mockMvc.perform(post("/api/material-supplies")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "   ",
                                  "materialName": "",
                                  "quantity": -1,
                                  "unitOfMeasure": "   ",
                                  "unitPrice": 0,
                                  "deliveryLeadTimeDays": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.materialCode")
                        .value("Material code is required."))
                .andExpect(jsonPath("$.error.fields.materialName")
                        .value("Material name is required."))
                .andExpect(jsonPath("$.error.fields.quantity")
                        .value("Quantity must be greater than zero."))
                .andExpect(jsonPath("$.error.fields.unitOfMeasure")
                        .value("Unit of measure is required."))
                .andExpect(jsonPath("$.error.fields.unitPrice")
                        .value("Unit price must be greater than zero."))
                .andExpect(jsonPath("$.error.fields.deliveryLeadTimeDays")
                        .value("Delivery lead time must not be negative."));

        mockMvc.perform(post("/api/material-supplies")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/material-supplies")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("MATERIAL_SUPPLY_CODE_EXISTS"))
                .andExpect(jsonPath("$.error.fields.materialCode").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_supplies WHERE supplier_id = ?",
                        Integer.class,
                        supplierId))
                .isEqualTo(1);
    }

    @Test
    void backendRequiresOwnedProfileActiveSupplierAccountAndSupplierRole()
            throws Exception {
        UserAccount supplierWithoutProfile =
                insertUser(6801, "supplier6801@example.com", UserRole.SUPPLIER);
        UserAccount customer = insertUser(6802, "customer6802@example.com", UserRole.CUSTOMER);
        UserAccount staleSupplier = insertUser(6803, "customer6803@example.com", UserRole.CUSTOMER);
        UserAccount inactiveSupplier =
                insertUser(6804, "supplier6804@example.com", UserRole.SUPPLIER);
        insertProfile(7804, inactiveSupplier.id(), "Inactive Supplier");
        jdbcTemplate.update("UPDATE users SET is_active = FALSE WHERE id = ?", 6804);

        mockMvc.perform(post("/api/material-supplies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/material-supplies")
                        .cookie(sessionFor(customer, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/material-supplies")
                        .cookie(sessionFor(staleSupplier, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/material-supplies")
                        .cookie(sessionFor(inactiveSupplier, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_SESSION"));
        mockMvc.perform(post("/api/material-supplies")
                        .cookie(sessionFor(supplierWithoutProfile, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSupplyRequest("")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUPPLIER_PROFILE_NOT_FOUND"));
    }

    @Test
    void supplierListIsRestrictedToOwnProfileAndSupportsSearch() throws Exception {
        UserAccount first = insertUser(6811, "supplier6811@example.com", UserRole.SUPPLIER);
        UserAccount second = insertUser(6812, "supplier6812@example.com", UserRole.SUPPLIER);
        long firstSupplierId = insertProfile(7811, first.id(), "First Supply Company");
        long secondSupplierId = insertProfile(7812, second.id(), "Second Supply Company");
        insertSupply(firstSupplierId, "COTTON-OWN", "Organic cotton", "ACTIVE");
        insertSupply(firstSupplierId, "THREAD-OWN", "Polyester thread", "INACTIVE");
        insertSupply(secondSupplierId, "COTTON-OTHER", "Other cotton", "ACTIVE");

        mockMvc.perform(get("/api/material-supplies")
                        .cookie(sessionFor(first, UserRole.SUPPLIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplies.length()").value(2))
                .andExpect(jsonPath("$.supplies[*].supplierId")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is((int) firstSupplierId))))
                .andExpect(jsonPath("$.supplies[*].materialCode")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem("COTTON-OTHER"))));

        mockMvc.perform(get("/api/material-supplies")
                        .cookie(sessionFor(first, UserRole.SUPPLIER))
                        .param("search", " organic ")
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplies.length()").value(1))
                .andExpect(jsonPath("$.supplies[0].materialCode").value("COTTON-OWN"))
                .andExpect(jsonPath("$.supplies[0].supplierBusinessName")
                        .value("First Supply Company"));
    }

    @Test
    void inventoryManagerAndAdministratorCanReadAndSearchCompanySupplies()
            throws Exception {
        UserAccount supplier = insertUser(6821, "supplier6821@example.com", UserRole.SUPPLIER);
        UserAccount inventory = insertUser(
                6822, "inventory6822@example.com", UserRole.INVENTORY_MANAGER);
        UserAccount administrator = insertUser(
                6823, "administrator6823@example.com", UserRole.ADMINISTRATOR);
        long supplierId = insertProfile(7821, supplier.id(), "Company Fabric Partner");
        insertSupply(supplierId, "LINEN-COMPANY", "Natural linen", "ACTIVE");

        for (UserAccount reader : new UserAccount[] {inventory, administrator}) {
            mockMvc.perform(get("/api/material-supplies")
                            .cookie(sessionFor(reader, reader.role()))
                            .param("search", "linen")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supplies.length()").value(1))
                    .andExpect(jsonPath("$.supplies[0].supplierId").value(supplierId))
                    .andExpect(jsonPath("$.supplies[0].supplierBusinessName")
                            .value("Company Fabric Partner"))
                    .andExpect(jsonPath("$.supplies[0].materialName").value("Natural linen"));
        }
    }

    @Test
    void listRejectsUnauthorizedRolesAndInvalidFilters() throws Exception {
        UserAccount customer = insertUser(6831, "customer6831@example.com", UserRole.CUSTOMER);
        UserAccount supplier = insertUser(6832, "supplier6832@example.com", UserRole.SUPPLIER);
        insertProfile(7832, supplier.id(), "Filter Validation Supplier");

        mockMvc.perform(get("/api/material-supplies")
                        .cookie(sessionFor(customer, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/material-supplies")
                        .cookie(sessionFor(supplier, UserRole.SUPPLIER))
                        .param("status", "UNKNOWN")
                        .param("search", "x".repeat(161)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.status").exists())
                .andExpect(jsonPath("$.error.fields.search").exists());
    }

    @Test
    void supplierPreloadsAndUpdatesOwnMutableDetailsWithoutChangingStableFields()
            throws Exception {
        UserAccount supplier = insertUser(6841, "supplier6841@example.com", UserRole.SUPPLIER);
        long supplierId = insertProfile(7841, supplier.id(), "Editable Supply Company");
        insertSupply(supplierId, "EDIT-OWN", "Editable cotton", "ACTIVE");
        long supplyId = supplyId("EDIT-OWN");
        jdbcTemplate.update(
                "UPDATE material_supplies SET updated_at = TIMESTAMP '2026-01-01 00:00:00' WHERE id = ?",
                supplyId);
        Cookie session = sessionFor(supplier, UserRole.SUPPLIER);

        mockMvc.perform(get("/api/material-supplies/{supplyId}", supplyId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplyId))
                .andExpect(jsonPath("$.supplierId").value(supplierId))
                .andExpect(jsonPath("$.materialCode").value("EDIT-OWN"))
                .andExpect(jsonPath("$.quantity").value("25.000"));

        mockMvc.perform(put("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": 999999,
                                  "materialCode": "FORGED-CODE",
                                  "quantity": 88.250,
                                  "unitPrice": 725.50,
                                  "deliveryLeadTimeDays": 6,
                                  "deliveryNotes": " Updated delivery window "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Material supply updated successfully."))
                .andExpect(jsonPath("$.supply.id").value(supplyId))
                .andExpect(jsonPath("$.supply.supplierId").value(supplierId))
                .andExpect(jsonPath("$.supply.materialCode").value("EDIT-OWN"))
                .andExpect(jsonPath("$.supply.quantity").value("88.250"))
                .andExpect(jsonPath("$.supply.unitPrice").value("725.50"))
                .andExpect(jsonPath("$.supply.deliveryLeadTimeDays").value(6))
                .andExpect(jsonPath("$.supply.deliveryNotes").value("Updated delivery window"));

        assertThat(jdbcTemplate.queryForMap(
                        """
                        SELECT supplier_id, material_code, material_name, quantity,
                               unit_price, delivery_lead_time_days, delivery_notes, updated_at
                        FROM material_supplies WHERE id = ?
                        """,
                        supplyId))
                .containsEntry("SUPPLIER_ID", supplierId)
                .containsEntry("MATERIAL_CODE", "EDIT-OWN")
                .containsEntry("MATERIAL_NAME", "Editable cotton")
                .containsEntry("DELIVERY_LEAD_TIME_DAYS", 6)
                .containsEntry("DELIVERY_NOTES", "Updated delivery window");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT updated_at > TIMESTAMP '2026-01-01 00:00:00' FROM material_supplies WHERE id = ?",
                        Boolean.class,
                        supplyId))
                .isTrue();
    }

    @Test
    void supplierCannotPreloadOrUpdateAnotherSuppliersRecord() throws Exception {
        UserAccount owner = insertUser(6851, "supplier6851@example.com", UserRole.SUPPLIER);
        UserAccount other = insertUser(6852, "supplier6852@example.com", UserRole.SUPPLIER);
        long ownerId = insertProfile(7851, owner.id(), "Owning Supply Company");
        insertProfile(7852, other.id(), "Other Supply Company");
        insertSupply(ownerId, "OWNER-ONLY", "Owner material", "ACTIVE");
        long supplyId = supplyId("OWNER-ONLY");
        Cookie otherSession = sessionFor(other, UserRole.SUPPLIER);

        mockMvc.perform(get("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MATERIAL_SUPPLY_NOT_FOUND"));
        mockMvc.perform(put("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(otherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MATERIAL_SUPPLY_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT quantity FROM material_supplies WHERE id = ?",
                        java.math.BigDecimal.class,
                        supplyId))
                .isEqualByComparingTo("25.000");
    }

    @Test
    void invalidNumericUpdatesAreRejectedWithoutChangingStoredData() throws Exception {
        UserAccount supplier = insertUser(6861, "supplier6861@example.com", UserRole.SUPPLIER);
        long supplierId = insertProfile(7861, supplier.id(), "Numeric Validation Company");
        insertSupply(supplierId, "NUMERIC-VALIDATION", "Validation material", "ACTIVE");
        long supplyId = supplyId("NUMERIC-VALIDATION");

        mockMvc.perform(put("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(sessionFor(supplier, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": -1,
                                  "unitPrice": 0,
                                  "deliveryLeadTimeDays": -2,
                                  "deliveryNotes": "Invalid update"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.quantity").exists())
                .andExpect(jsonPath("$.error.fields.unitPrice").exists())
                .andExpect(jsonPath("$.error.fields.deliveryLeadTimeDays").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT quantity FROM material_supplies WHERE id = ?",
                        java.math.BigDecimal.class,
                        supplyId))
                .isEqualByComparingTo("25.000");
    }

    @Test
    void authorizedCompanyListReadsLatestSupplierUpdate() throws Exception {
        UserAccount supplier = insertUser(6871, "supplier6871@example.com", UserRole.SUPPLIER);
        UserAccount administrator = insertUser(
                6872, "administrator6872@example.com", UserRole.ADMINISTRATOR);
        long supplierId = insertProfile(7871, supplier.id(), "Latest Data Company");
        insertSupply(supplierId, "LATEST-DATA", "Latest material", "ACTIVE");
        long supplyId = supplyId("LATEST-DATA");

        mockMvc.perform(put("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(sessionFor(supplier, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/material-supplies")
                        .cookie(sessionFor(administrator, UserRole.ADMINISTRATOR))
                        .param("search", "LATEST-DATA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplies.length()").value(1))
                .andExpect(jsonPath("$.supplies[0].quantity").value("40.500"))
                .andExpect(jsonPath("$.supplies[0].unitPrice").value("600.25"))
                .andExpect(jsonPath("$.supplies[0].deliveryLeadTimeDays").value(8))
                .andExpect(jsonPath("$.supplies[0].deliveryNotes")
                        .value("Revised delivery plan"));

        mockMvc.perform(put("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(sessionFor(administrator, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void supplierArchivesOwnSupplyWithoutDeletingHistoricalRecord() throws Exception {
        UserAccount supplier = insertUser(6881, "supplier6881@example.com", UserRole.SUPPLIER);
        UserAccount inventory = insertUser(
                6882, "inventory6882@example.com", UserRole.INVENTORY_MANAGER);
        long supplierId = insertProfile(7881, supplier.id(), "Archive History Company");
        insertSupply(supplierId, "ARCHIVE-HISTORY", "Historical fabric", "ACTIVE");
        long supplyId = supplyId("ARCHIVE-HISTORY");
        jdbcTemplate.update(
                "UPDATE material_supplies SET updated_at = TIMESTAMP '2026-01-01 00:00:00' WHERE id = ?",
                supplyId);
        Cookie supplierSession = sessionFor(supplier, UserRole.SUPPLIER);

        mockMvc.perform(delete("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(supplierSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Material supply archived successfully."))
                .andExpect(jsonPath("$.supply.id").value(supplyId))
                .andExpect(jsonPath("$.supply.supplierId").value(supplierId))
                .andExpect(jsonPath("$.supply.status").value("DISCONTINUED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_supplies WHERE id = ?",
                        Integer.class,
                        supplyId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT updated_at > TIMESTAMP '2026-01-01 00:00:00' FROM material_supplies WHERE id = ?",
                        Boolean.class,
                        supplyId))
                .isTrue();

        mockMvc.perform(get("/api/material-supplies")
                        .cookie(sessionFor(inventory, UserRole.INVENTORY_MANAGER))
                        .param("search", "ARCHIVE-HISTORY")
                        .param("status", "DISCONTINUED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplies.length()").value(1))
                .andExpect(jsonPath("$.supplies[0].id").value(supplyId))
                .andExpect(jsonPath("$.supplies[0].status").value("DISCONTINUED"));

        mockMvc.perform(get("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(sessionFor(inventory, UserRole.INVENTORY_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplyId))
                .andExpect(jsonPath("$.supplierId").value(supplierId))
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));

        mockMvc.perform(delete("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(supplierSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supply.id").value(supplyId))
                .andExpect(jsonPath("$.supply.status").value("DISCONTINUED"));
    }

    @Test
    void archiveOwnershipAndRolePermissionsAreEnforced() throws Exception {
        UserAccount owner = insertUser(6883, "supplier6883@example.com", UserRole.SUPPLIER);
        UserAccount other = insertUser(6884, "supplier6884@example.com", UserRole.SUPPLIER);
        UserAccount administrator = insertUser(
                6885, "administrator6885@example.com", UserRole.ADMINISTRATOR);
        long ownerId = insertProfile(7883, owner.id(), "Archive Owner Company");
        insertProfile(7884, other.id(), "Archive Other Company");
        insertSupply(ownerId, "ARCHIVE-OWNER", "Owner archive fabric", "ACTIVE");
        long supplyId = supplyId("ARCHIVE-OWNER");

        mockMvc.perform(delete("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(sessionFor(other, UserRole.SUPPLIER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MATERIAL_SUPPLY_NOT_FOUND"));
        mockMvc.perform(delete("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(sessionFor(administrator, UserRole.ADMINISTRATOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM material_supplies WHERE id = ?",
                        String.class,
                        supplyId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void archivedSupplyCannotBeEditedButRemainsReadableByOwner() throws Exception {
        UserAccount supplier = insertUser(6886, "supplier6886@example.com", UserRole.SUPPLIER);
        long supplierId = insertProfile(7886, supplier.id(), "Archived Read Company");
        insertSupply(supplierId, "ARCHIVED-READ", "Archived readable fabric", "DISCONTINUED");
        long supplyId = supplyId("ARCHIVED-READ");
        Cookie session = sessionFor(supplier, UserRole.SUPPLIER);

        mockMvc.perform(get("/api/material-supplies/{supplyId}", supplyId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(supplyId))
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));
        mockMvc.perform(put("/api/material-supplies/{supplyId}", supplyId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUpdateRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATERIAL_SUPPLY_ARCHIVED"));
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
                "Material Supply Test Account",
                storedRole.name());
        return new UserAccount(
                id, email, "not-used", "Material Supply Test Account", storedRole, true);
    }

    private long insertProfile(long id, long userId, String businessName) {
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
        return id;
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

    private String validSupplyRequest(String extraFields) {
        return """
                {
                  %s
                  "materialCode": "THREAD-001",
                  "materialName": "Polyester thread",
                  "materialDescription": "High-strength garment thread",
                  "quantity": 25.000,
                  "unitOfMeasure": "cone",
                  "unitPrice": 475.00,
                  "deliveryLeadTimeDays": 3,
                  "deliveryNotes": "Standard delivery"
                }
                """.formatted(extraFields);
    }

    private void insertSupply(
            long supplierId, String materialCode, String materialName, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO material_supplies
                    (supplier_id, material_code, material_name, material_description,
                     quantity, unit_of_measure, unit_price, delivery_lead_time_days,
                     delivery_notes, status)
                VALUES (?, ?, ?, ?, 25.000, 'metre', 450.00, 4, ?, ?)
                """,
                supplierId,
                materialCode,
                materialName,
                materialName + " description",
                materialName + " delivery",
                status);
    }

    private long supplyId(String materialCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM material_supplies WHERE material_code = ?",
                Long.class,
                materialCode);
    }

    private String validUpdateRequest() {
        return """
                {
                  "quantity": 40.500,
                  "unitPrice": 600.25,
                  "deliveryLeadTimeDays": 8,
                  "deliveryNotes": "Revised delivery plan"
                }
                """;
    }

    private void cleanTestAccounts() {
        jdbcTemplate.update("DELETE FROM material_supplies WHERE supplier_id BETWEEN 7501 AND 7899");
        jdbcTemplate.update("DELETE FROM supplier_profiles WHERE id BETWEEN 7501 AND 7899");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 6501 AND 6899");
    }
}

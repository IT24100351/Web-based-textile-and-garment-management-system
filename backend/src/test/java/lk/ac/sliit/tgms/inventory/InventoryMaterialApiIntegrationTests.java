package lk.ac.sliit.tgms.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.AuthTokenService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class InventoryMaterialApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanInventoryMaterials();
        cleanSupplierFixtures();
        cleanTestAccounts();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        cleanInventoryMaterials();
        cleanSupplierFixtures();
        cleanTestAccounts();
    }

    @Test
    void inventoryManagerCreatesNormalizedMaterialAndSeesItInList() throws Exception {
        UserAccount inventoryManager = insertUser(
                6901, "inventory6901@example.com", UserRole.INVENTORY_MANAGER);
        UserAccount supplier = insertUser(
                6951, "inventory-ticket32-supplier@example.com", UserRole.SUPPLIER);
        long supplierId = insertSupplierProfile(supplier.id(), "Colombo Textile Supply");
        long supplyId = insertSupply(supplierId, "SUP-FAB-001", "Supplier cotton twill");

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceMaterialSupplyId": %d,
                                  "materialCode": "  INV-FAB-001  ",
                                  "materialName": "  Cotton   twill fabric  ",
                                  "materialDescription": "  Main store 240 GSM cotton twill  ",
                                  "materialType": "FABRIC",
                                  "unitOfMeasure": " metre ",
                                  "currentQuantity": 840.250,
                                  "lowStockThreshold": 100.000
                                }
                                """.formatted(supplyId)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        Matchers.matchesPattern("/api/inventory-materials/[1-9][0-9]*")))
                .andExpect(jsonPath("$.message")
                        .value("Inventory material created successfully."))
                .andExpect(jsonPath("$.material.id").isNumber())
                .andExpect(jsonPath("$.material.sourceMaterialSupplyId").value((int) supplyId))
                .andExpect(jsonPath("$.material.materialCode").value("INV-FAB-001"))
                .andExpect(jsonPath("$.material.materialName").value("Cotton twill fabric"))
                .andExpect(jsonPath("$.material.materialDescription")
                        .value("Main store 240 GSM cotton twill"))
                .andExpect(jsonPath("$.material.materialType").value("FABRIC"))
                .andExpect(jsonPath("$.material.unitOfMeasure").value("metre"))
                .andExpect(jsonPath("$.material.currentQuantity").value("840.250"))
                .andExpect(jsonPath("$.material.lowStockThreshold").value("100.000"))
                .andExpect(jsonPath("$.material.status").value("ACTIVE"))
                .andExpect(jsonPath("$.material.stockState").value("SUFFICIENT"));

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM inventory_materials
                        WHERE material_code = ? AND source_material_supply_id = ?
                        """,
                        Integer.class,
                        "INV-FAB-001",
                        supplyId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM supplier_profiles WHERE id = ?",
                        Integer.class,
                        supplierId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM material_supplies WHERE id = ?",
                        Integer.class,
                        supplyId))
                .isEqualTo(1);

        mockMvc.perform(get("/api/inventory-materials")
                        .cookie(sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials.length()").value(1))
                .andExpect(jsonPath("$.materials[0].sourceMaterialSupplyId").value((int) supplyId))
                .andExpect(jsonPath("$.materials[0].materialCode").value("INV-FAB-001"));
    }

    @Test
    void inventoryManagerSearchesAndFiltersInventoryUsingStoredFields() throws Exception {
        UserAccount inventoryManager = insertUser(
                6909, "inventory6909@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        insertInventoryMaterial(
                "INV-FAB-SEARCH",
                "Cotton poplin",
                "Lightweight cotton for shirts",
                "FABRIC",
                "metre",
                "42.375",
                "8.000",
                "ACTIVE");
        insertInventoryMaterial(
                "INV-RAW-SEARCH",
                "Polyester thread",
                "Thread cones for production",
                "RAW_MATERIAL",
                "cone",
                "90.000",
                "12.000",
                "ACTIVE");
        insertInventoryMaterial(
                "INV-FAB-INACTIVE",
                "Cotton canvas",
                "Inactive canvas stock",
                "FABRIC",
                "metre",
                "15.500",
                "5.000",
                "INACTIVE");

        mockMvc.perform(get("/api/inventory-materials")
                        .queryParam("search", "  cotton  ")
                        .queryParam("status", "active")
                        .queryParam("materialType", "fabric")
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials.length()").value(1))
                .andExpect(jsonPath("$.materials[0].materialCode").value("INV-FAB-SEARCH"))
                .andExpect(jsonPath("$.materials[0].materialName").value("Cotton poplin"))
                .andExpect(jsonPath("$.materials[0].materialType").value("FABRIC"))
                .andExpect(jsonPath("$.materials[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.materials[0].currentQuantity").value("42.375"))
                .andExpect(jsonPath("$.materials[0].stockState").value("SUFFICIENT"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE material_code = ?",
                        java.math.BigDecimal.class,
                        "INV-FAB-SEARCH"))
                .isEqualByComparingTo("42.375");
    }

    @Test
    void lowStockEndpointIncludesBelowAndExactThresholdButExcludesAbove() throws Exception {
        UserAccount inventoryManager = insertUser(
                6911, "inventory6911@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        insertInventoryMaterial(
                "INV-LOW-BELOW", "Below threshold fabric", "Below threshold", "FABRIC",
                "metre", "4.999", "5.000", "ACTIVE");
        insertInventoryMaterial(
                "INV-LOW-EXACT", "Exact threshold fabric", "Exactly threshold", "FABRIC",
                "metre", "5.000", "5.000", "ACTIVE");
        insertInventoryMaterial(
                "INV-LOW-ABOVE", "Above threshold fabric", "Above threshold", "FABRIC",
                "metre", "5.001", "5.000", "ACTIVE");

        mockMvc.perform(get("/api/inventory-materials/low-stock").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.materials.length()").value(2))
                .andExpect(jsonPath("$.materials[*].materialCode", Matchers.containsInAnyOrder(
                        "INV-LOW-BELOW", "INV-LOW-EXACT")))
                .andExpect(jsonPath("$.materials[*].stockState", Matchers.everyItem(
                        Matchers.is("LOW_STOCK"))))
                .andExpect(jsonPath("$.materials[?(@.materialCode == 'INV-LOW-BELOW')].currentQuantity")
                        .value(Matchers.contains("4.999")))
                .andExpect(jsonPath("$.materials[?(@.materialCode == 'INV-LOW-EXACT')].currentQuantity")
                        .value(Matchers.contains("5.000")));

        mockMvc.perform(get("/api/inventory-materials").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials[?(@.materialCode == 'INV-LOW-BELOW')].stockState")
                        .value(Matchers.contains("LOW_STOCK")))
                .andExpect(jsonPath("$.materials[?(@.materialCode == 'INV-LOW-EXACT')].stockState")
                        .value(Matchers.contains("LOW_STOCK")))
                .andExpect(jsonPath("$.materials[?(@.materialCode == 'INV-LOW-ABOVE')].stockState")
                        .value(Matchers.contains("SUFFICIENT")));
    }

    @Test
    void stockConsumptionCanMoveMaterialExactlyOntoLowStockThreshold() throws Exception {
        UserAccount inventoryManager = insertUser(
                6912, "inventory6912@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);
        insertInventoryMaterial(
                "INV-LOW-CONSUME", "Consumption threshold fabric", "Transition test", "FABRIC",
                "metre", "6.000", "5.000", "ACTIVE");
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-LOW-CONSUME");

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1.000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.material.currentQuantity").value("5.000"))
                .andExpect(jsonPath("$.material.lowStockThreshold").value("5.000"))
                .andExpect(jsonPath("$.material.stockState").value("LOW_STOCK"));

        mockMvc.perform(get("/api/inventory-materials/low-stock").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.materials[0].id").value(materialId));
    }

    @Test
    void invalidInventoryListFiltersAreRejectedClearly() throws Exception {
        UserAccount inventoryManager = insertUser(
                6910, "inventory6910@example.com", UserRole.INVENTORY_MANAGER);

        mockMvc.perform(get("/api/inventory-materials")
                        .queryParam("status", "UNKNOWN")
                        .queryParam("materialType", "THREAD")
                        .cookie(sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.status")
                        .value("Status must be ACTIVE, INACTIVE, or DISCONTINUED."))
                .andExpect(jsonPath("$.error.fields.materialType")
                        .value("Material type must be FABRIC or RAW_MATERIAL."));
    }

    @Test
    void invalidAndDuplicateInventoryMaterialValuesAreRejectedClearly() throws Exception {
        UserAccount inventoryManager = insertUser(
                6902, "inventory6902@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "   ",
                                  "materialName": "",
                                  "materialType": null,
                                  "unitOfMeasure": "   ",
                                  "currentQuantity": -1,
                                  "lowStockThreshold": -0.001
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.materialCode")
                        .value("Material code is required."))
                .andExpect(jsonPath("$.error.fields.materialName")
                        .value("Material name is required."))
                .andExpect(jsonPath("$.error.fields.materialType")
                        .value("Material type is required."))
                .andExpect(jsonPath("$.error.fields.unitOfMeasure")
                        .value("Unit of measure is required."))
                .andExpect(jsonPath("$.error.fields.currentQuantity")
                        .value("Opening quantity must be greater than zero."))
                .andExpect(jsonPath("$.error.fields.lowStockThreshold")
                        .value("Low-stock threshold cannot be negative."));

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("INVENTORY_MATERIAL_CODE_EXISTS"))
                .andExpect(jsonPath("$.error.fields.materialCode").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM inventory_materials WHERE material_code = ?",
                        Integer.class,
                        "INV-RAW-001"))
                .isEqualTo(1);
    }


    @Test
    void brokenSupplierSupplyReferenceIsRejectedWithoutCreatingInventoryData() throws Exception {
        UserAccount inventoryManager = insertUser(
                6904, "inventory6904@example.com", UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceMaterialSupplyId": 9223372036854775807,
                                  "materialCode": "INV-BROKEN-001",
                                  "materialName": "Broken source test",
                                  "materialType": "FABRIC",
                                  "unitOfMeasure": "metre",
                                  "currentQuantity": 10.000,
                                  "lowStockThreshold": 1.000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.sourceMaterialSupplyId")
                        .value("Source material supply was not found."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM inventory_materials WHERE material_code = ?",
                        Integer.class,
                        "INV-BROKEN-001"))
                .isZero();
    }

    @Test
    void validMaterialUsageReducesStockAndReturnsUpdatedMaterial() throws Exception {
        UserAccount inventoryManager = insertUser(
                6905, "inventory6905@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-RAW-001");

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 7.250
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Inventory stock consumed successfully."))
                .andExpect(jsonPath("$.material.id").value((int) materialId))
                .andExpect(jsonPath("$.material.currentQuantity").value("17.750"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        java.math.BigDecimal.class,
                        materialId))
                .isEqualByComparingTo("17.750");
    }

    @Test
    void insufficientStockIsRejectedWithoutPartiallyChangingQuantity() throws Exception {
        UserAccount inventoryManager = insertUser(
                6906, "inventory6906@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-RAW-001");

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 30.000
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.error.message")
                        .value("Insufficient stock. Requested 30.000 but only 25.000 is available."))
                .andExpect(jsonPath("$.error.fields.quantity").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        java.math.BigDecimal.class,
                        materialId))
                .isEqualByComparingTo("25.000");
    }

    @Test
    void nonPositiveUsageIsRejectedWithoutChangingStock() throws Exception {
        UserAccount inventoryManager = insertUser(
                6907, "inventory6907@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-RAW-001");

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.quantity")
                        .value("Usage quantity must be greater than zero."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        java.math.BigDecimal.class,
                        materialId))
                .isEqualByComparingTo("25.000");
    }

    @Test
    void consumeStockRejectsUnknownMaterialWithoutCreatingOrChangingData() throws Exception {
        UserAccount inventoryManager = insertUser(
                6908, "inventory6908@example.com", UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", 9223372036854775807L)
                        .cookie(sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 1.000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.materialId")
                        .value("Inventory material was not found."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM inventory_materials", Integer.class))
                .isZero();
    }

    @Test
    void inventoryMaterialCreateAndListRequireInventoryManagerRole() throws Exception {
        UserAccount customer = insertUser(6903, "customer6903@example.com", UserRole.CUSTOMER);

        mockMvc.perform(post("/api/inventory-materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(sessionFor(customer, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/inventory-materials")
                        .cookie(sessionFor(customer, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/inventory-materials/low-stock"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/inventory-materials/low-stock")
                        .cookie(sessionFor(customer, UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1.000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", 1)
                        .cookie(sessionFor(customer, UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1.000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/inventory-materials/{materialId}/availability", 1)
                        .param("requiredQuantity", "1.000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/inventory-materials/{materialId}/availability", 1)
                        .cookie(sessionFor(customer, UserRole.CUSTOMER))
                        .param("requiredQuantity", "1.000"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void inventoryManagerUpdatesMetadataWithoutChangingStockOrSupplierProvenance() throws Exception {
        UserAccount inventoryManager = insertUser(
                6910, "inventory69010@example.com", UserRole.INVENTORY_MANAGER);
        UserAccount supplier = insertUser(
                6952, "inventory-ticket32-supplier-update@example.com", UserRole.SUPPLIER);
        long supplierId = insertSupplierProfile(supplier.id(), "Update Source Supplier");
        long supplyId = insertSupply(supplierId, "SUP-UPD-001", "Update source fabric");
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceMaterialSupplyId": %d,
                                  "materialCode": "INV-EDIT-001",
                                  "materialName": "Original fabric",
                                  "materialType": "FABRIC",
                                  "unitOfMeasure": "metre",
                                  "currentQuantity": 25.000,
                                  "lowStockThreshold": 5.000
                                }
                                """.formatted(supplyId)))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-EDIT-001");

        mockMvc.perform(put("/api/inventory-materials/{materialId}", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": " INV-EDIT-002 ",
                                  "materialName": " Updated   fabric ",
                                  "materialDescription": " Updated metadata only ",
                                  "materialType": "RAW_MATERIAL",
                                  "unitOfMeasure": " roll ",
                                  "lowStockThreshold": 8.500,
                                  "status": "INACTIVE",
                                  "currentQuantity": 999.000,
                                  "sourceMaterialSupplyId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Inventory material updated successfully."))
                .andExpect(jsonPath("$.material.materialCode").value("INV-EDIT-002"))
                .andExpect(jsonPath("$.material.materialName").value("Updated fabric"))
                .andExpect(jsonPath("$.material.currentQuantity").value("25.000"))
                .andExpect(jsonPath("$.material.lowStockThreshold").value("8.500"))
                .andExpect(jsonPath("$.material.status").value("INACTIVE"))
                .andExpect(jsonPath("$.material.sourceMaterialSupplyId").value((int) supplyId));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        java.math.BigDecimal.class,
                        materialId))
                .isEqualByComparingTo("25.000");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT source_material_supply_id FROM inventory_materials WHERE id = ?",
                        Long.class,
                        materialId))
                .isEqualTo(supplyId);
    }

    @Test
    void archivePreservesInventoryHistoryAndBlocksFurtherEditOrConsumption() throws Exception {
        UserAccount inventoryManager = insertUser(
                6911, "inventory69011@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);
        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-RAW-001");

        mockMvc.perform(delete("/api/inventory-materials/{materialId}", materialId)
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Inventory material archived successfully."))
                .andExpect(jsonPath("$.material.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.material.currentQuantity").value("25.000"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM inventory_materials WHERE id = ?",
                        Integer.class,
                        materialId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        java.math.BigDecimal.class,
                        materialId))
                .isEqualByComparingTo("25.000");

        mockMvc.perform(put("/api/inventory-materials/{materialId}", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "INV-RAW-001",
                                  "materialName": "Should not change",
                                  "materialType": "RAW_MATERIAL",
                                  "unitOfMeasure": "roll",
                                  "lowStockThreshold": 5.000,
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_MATERIAL_ARCHIVED"));

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1.000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_MATERIAL_NOT_ACTIVE"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        java.math.BigDecimal.class,
                        materialId))
                .isEqualByComparingTo("25.000");
    }

    @Test
    void archivedLowStockMaterialIsRemovedFromReplenishmentMonitoring() throws Exception {
        UserAccount inventoryManager = insertUser(
                6912, "inventory69012@example.com", UserRole.INVENTORY_MANAGER);
        Cookie session = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);
        insertInventoryMaterial(
                "INV-ARCHIVE-LOW", "Archived low stock", null, "FABRIC", "metre",
                "4.000", "5.000", "ACTIVE");
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-ARCHIVE-LOW");

        mockMvc.perform(get("/api/inventory-materials/low-stock").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(delete("/api/inventory-materials/{materialId}", materialId).cookie(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/inventory-materials/low-stock").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void productionManagerCanCheckExactMaterialAvailabilityButCannotEditInventory() throws Exception {
        UserAccount inventoryManager = insertUser(
                6913, "inventory69013@example.com", UserRole.INVENTORY_MANAGER);
        UserAccount productionManager = insertUser(
                6914, "inventory69014@example.com", UserRole.PRODUCTION_MANAGER);
        Cookie inventorySession = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);
        Cookie productionSession = sessionFor(productionManager, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(inventorySession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-RAW-001");

        mockMvc.perform(get("/api/inventory-materials/{materialId}/availability", materialId)
                        .cookie(productionSession)
                        .param("requiredQuantity", "20.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialId").value((int) materialId))
                .andExpect(jsonPath("$.currentQuantity").value("25.000"))
                .andExpect(jsonPath("$.requiredQuantity").value("20.000"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.availabilityState").value("AVAILABLE"));

        mockMvc.perform(get("/api/inventory-materials/{materialId}/availability", materialId)
                        .cookie(productionSession)
                        .param("requiredQuantity", "30.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuantity").value("25.000"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.availabilityState").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(put("/api/inventory-materials/{materialId}", materialId)
                        .cookie(productionSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "INV-RAW-001",
                                  "materialName": "Unauthorized edit",
                                  "materialType": "RAW_MATERIAL",
                                  "unitOfMeasure": "roll",
                                  "lowStockThreshold": 5.000,
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void availabilityReportsNotActiveAfterArchiveAndRejectsInvalidRequiredQuantity() throws Exception {
        UserAccount inventoryManager = insertUser(
                6915, "inventory69015@example.com", UserRole.INVENTORY_MANAGER);
        UserAccount productionManager = insertUser(
                6916, "inventory69016@example.com", UserRole.PRODUCTION_MANAGER);
        Cookie inventorySession = sessionFor(inventoryManager, UserRole.INVENTORY_MANAGER);
        Cookie productionSession = sessionFor(productionManager, UserRole.PRODUCTION_MANAGER);
        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(inventorySession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMaterialRequest()))
                .andExpect(status().isCreated());
        long materialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "INV-RAW-001");

        mockMvc.perform(delete("/api/inventory-materials/{materialId}", materialId)
                        .cookie(inventorySession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/inventory-materials/{materialId}/availability", materialId)
                        .cookie(productionSession)
                        .param("requiredQuantity", "1.000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentQuantity").value("25.000"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.availabilityState").value("NOT_ACTIVE"));

        mockMvc.perform(get("/api/inventory-materials/{materialId}/availability", materialId)
                        .cookie(productionSession)
                        .param("requiredQuantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.requiredQuantity")
                        .value("Required quantity must be greater than zero."));
    }

    private void insertInventoryMaterial(
            String materialCode,
            String materialName,
            String materialDescription,
            String materialType,
            String unitOfMeasure,
            String currentQuantity,
            String lowStockThreshold,
            String status) {
        jdbcTemplate.update(
                """
                INSERT INTO inventory_materials
                    (material_code, material_name, material_description, material_type,
                     unit_of_measure, current_quantity, low_stock_threshold, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                materialCode,
                materialName,
                materialDescription,
                materialType,
                unitOfMeasure,
                new java.math.BigDecimal(currentQuantity),
                new java.math.BigDecimal(lowStockThreshold),
                status);
    }

    private String validMaterialRequest() {
        return """
                {
                  "materialCode": "INV-RAW-001",
                  "materialName": "Polyester lining",
                  "materialDescription": "Roll stock for production orders",
                  "materialType": "RAW_MATERIAL",
                  "unitOfMeasure": "roll",
                  "currentQuantity": 25.000,
                  "lowStockThreshold": 5.000
                }
                """;
    }

    private UserAccount insertUser(long id, String email, UserRole role) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                "Inventory API User " + id,
                role.name());
        return new UserAccount(
                id,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                "Inventory API User " + id,
                role,
                true);
    }


    private long insertSupplierProfile(long userId, String businessName) {
        jdbcTemplate.update(
                """
                INSERT INTO supplier_profiles (user_id, business_name, contact_phone, address)
                VALUES (?, ?, ?, ?)
                """,
                userId,
                businessName,
                "011 234 5678",
                "Colombo");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM supplier_profiles WHERE user_id = ?", Long.class, userId);
    }

    private long insertSupply(long supplierId, String materialCode, String materialName) {
        jdbcTemplate.update(
                """
                INSERT INTO material_supplies
                    (supplier_id, material_code, material_name, quantity, unit_of_measure,
                     unit_price, delivery_lead_time_days, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """,
                supplierId,
                materialCode,
                materialName,
                new java.math.BigDecimal("500.000"),
                "metre",
                new java.math.BigDecimal("750.00"),
                5);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM material_supplies WHERE supplier_id = ? AND material_code = ?",
                Long.class,
                supplierId,
                materialCode);
    }

    private Cookie sessionFor(UserAccount account, UserRole tokenRole) {
        String token = authTokenService.issue(
                new UserAccount(
                        account.id(),
                        account.email(),
                        account.passwordHash(),
                        account.fullName(),
                        tokenRole,
                        account.active()));
        Cookie cookie = new Cookie(AuthCookieService.COOKIE_NAME, token);
        cookie.setPath("/");
        return cookie;
    }

    private void cleanInventoryMaterials() {
        jdbcTemplate.update("DELETE FROM inventory_materials");
    }


    private void cleanSupplierFixtures() {
        jdbcTemplate.update(
                """
                DELETE FROM material_supplies
                WHERE supplier_id IN (
                    SELECT sp.id
                    FROM supplier_profiles sp
                    JOIN users u ON u.id = sp.user_id
                    WHERE u.email LIKE 'inventory-ticket32-%@example.com'
                )
                """);
        jdbcTemplate.update(
                """
                DELETE FROM supplier_profiles
                WHERE user_id IN (
                    SELECT id FROM users
                    WHERE email LIKE 'inventory-ticket32-%@example.com'
                )
                """);
    }

    private void cleanTestAccounts() {
        jdbcTemplate.update(
                "DELETE FROM users WHERE email LIKE '%690%@example.com' "
                        + "OR email LIKE 'inventory-ticket32-%@example.com'");
    }
}

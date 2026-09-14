package lk.ac.sliit.tgms.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProductionTaskApiIntegrationTests {

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
    void productionManagerCanQueryOnlyProductionReadyOrdersThroughOrderHandoff() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        insertOrder(7502, "ORD-NOT-READY", "PENDING", 7602);
        insertOrder(7503, "ORD-DELIVERY-READY", "READY_FOR_DELIVERY", 7603);

        mockMvc.perform(get("/api/production/tasks/eligible-orders")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(7501))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-PRODUCTION-READY"))
                .andExpect(jsonPath("$[0].customerId").value(7101))
                .andExpect(jsonPath("$[0].currentStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].readyForProduction").value(true))
                .andExpect(jsonPath("$[0].items[0].orderItemId").value(7601))
                .andExpect(jsonPath("$[0].items[0].productId").value(7201))
                .andExpect(jsonPath("$[0].items[0].variantId").value(7301))
                .andExpect(jsonPath("$[0].items[0].quantity").value(3))
                .andExpect(jsonPath("$[0].items[0].selectedSize").value("L"))
                .andExpect(jsonPath("$[0].items[0].selectedColor").value("Navy"));
    }

    @Test
    void eligibleOrderCreatesPendingTaskLinkedToCorrectOrder() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);

        mockMvc.perform(post("/api/production/tasks")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":7501}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Production task created successfully."))
                .andExpect(jsonPath("$.task.id").isNumber())
                .andExpect(jsonPath("$.task.taskNumber").value(
                        org.hamcrest.Matchers.matchesPattern("PRD-[A-F0-9]{20}")))
                .andExpect(jsonPath("$.task.orderId").value(7501))
                .andExpect(jsonPath("$.task.status").value("PENDING"))
                .andExpect(jsonPath("$.task.startedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.task.completedAt").value(org.hamcrest.Matchers.nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT order_id FROM production_tasks", Long.class)).isEqualTo(7501L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks", String.class)).isEqualTo("PENDING");
    }

    @Test
    void missingInvalidAndNoLongerEligibleOrdersAreRejectedWithoutTaskInsert() throws Exception {
        insertOrder(7501, "ORD-NOT-READY", "PENDING", 7601);
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(post("/api/production/tasks")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.orderId").value("Select a valid positive order ID."));

        mockMvc.perform(post("/api/production/tasks")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(post("/api/production/tasks")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":7501}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_READY_FOR_PRODUCTION"))
                .andExpect(jsonPath("$.error.fields.orderId").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_tasks", Integer.class)).isZero();
    }

    @Test
    void productionManagerCanSaveAndEditRequiredTaskDetails() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7701, 'PRD-DETAIL-7701', 7501)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(get("/api/production/tasks/7701").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.id").value(7701))
                .andExpect(jsonPath("$.order.orderNumber").value("ORD-PRODUCTION-READY"))
                .andExpect(jsonPath("$.order.items[0].quantity").value(3))
                .andExpect(jsonPath("$.workDetails").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/production/tasks/7701/details")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDetails\":\"Cut and stitch three navy shirts to the approved order selections.\",\"workAssignment\":\"Sewing Line A\",\"workNotes\":\"Prioritize collar inspection.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Production task details saved successfully."))
                .andExpect(jsonPath("$.task.workDetails.workAssignment").value("Sewing Line A"))
                .andExpect(jsonPath("$.task.workDetails.workNotes").value("Prioritize collar inspection."));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT work_details FROM production_task_details WHERE production_task_id = 7701",
                String.class)).contains("Cut and stitch");

        mockMvc.perform(put("/api/production/tasks/7701/details")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDetails\":\"Complete cutting, stitching and finishing for the linked order.\",\"workAssignment\":\"Finishing Team 2\",\"workNotes\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.workDetails.workAssignment").value("Finishing Team 2"))
                .andExpect(jsonPath("$.task.workDetails.workNotes").value(org.hamcrest.Matchers.nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_details WHERE production_task_id = 7701",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void missingRequiredTaskDetailsAreRejectedWithoutPartialSave() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7701, 'PRD-DETAIL-7701', 7501)");

        mockMvc.perform(put("/api/production/tasks/7701/details")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDetails\":\"  \",\"workAssignment\":\"\",\"workNotes\":\"optional note\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.workDetails").exists())
                .andExpect(jsonPath("$.error.fields.workAssignment").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_details", Integer.class)).isZero();

        mockMvc.perform(get("/api/production/tasks/999999")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_TASK_NOT_FOUND"));
    }

    @Test
    void productionManagerAndAdministratorCanManageDetailsButOtherRolesCannot() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7701, 'PRD-DETAIL-7701', 7501)");
        String body = "{\"workDetails\":\"Manufacture the linked order items.\",\"workAssignment\":\"Line B\",\"workNotes\":null}";

        mockMvc.perform(put("/api/production/tasks/7701/details")
                        .cookie(sessionFor(7997, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/production/tasks/7701/details")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/production/tasks/7701")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void productionTaskEndpointsRequireProductionManager() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);

        mockMvc.perform(get("/api/production/tasks/eligible-orders")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/production/tasks")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":7501}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/production/tasks/eligible-orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productionManagerCanSelectActiveInventoryMaterialsThroughInventoryContract() throws Exception {
        mockMvc.perform(get("/api/production/tasks/material-options")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inventoryMaterialId").value(7801))
                .andExpect(jsonPath("$[0].materialCode").value("FAB-PROD-001"))
                .andExpect(jsonPath("$[0].materialName").value("Cotton Twill"))
                .andExpect(jsonPath("$[0].materialType").value("FABRIC"))
                .andExpect(jsonPath("$[0].unitOfMeasure").value("metre"))
                .andExpect(jsonPath("$[0].currentQuantity").value(120.000))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void productionManagerCanSaveCompleteMaterialRequirementsAndSeeAvailability() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7701, 'PRD-MATERIAL-7701', 7501)");

        mockMvc.perform(put("/api/production/tasks/7701/materials")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materials": [
                                    {"inventoryMaterialId": 7801, "requiredQuantity": 25.500},
                                    {"inventoryMaterialId": 7802, "requiredQuantity": 12.000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Production material requirements saved successfully."))
                .andExpect(jsonPath("$.task.materialRequirements.length()").value(2))
                .andExpect(jsonPath("$.task.materialRequirements[0].inventoryMaterialId").value(7801))
                .andExpect(jsonPath("$.task.materialRequirements[0].requiredQuantity").value(25.500))
                .andExpect(jsonPath("$.task.materialRequirements[0].availabilityState").value("AVAILABLE"))
                .andExpect(jsonPath("$.task.materialRequirements[1].inventoryMaterialId").value(7802))
                .andExpect(jsonPath("$.task.materialRequirements[1].requiredQuantity").value(12.000))
                .andExpect(jsonPath("$.task.materialRequirements[1].availabilityState").value("INSUFFICIENT_STOCK"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_material_requirements WHERE production_task_id = 7701",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT required_quantity FROM production_task_material_requirements WHERE production_task_id = 7701 AND inventory_material_id = 7801",
                java.math.BigDecimal.class)).isEqualByComparingTo("25.500");

        mockMvc.perform(get("/api/production/tasks/7701")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialRequirements.length()").value(2))
                .andExpect(jsonPath("$.materialRequirements[0].materialName").value("Cotton Twill"));
    }

    @Test
    void materialRequirementsRejectDuplicatesInvalidQuantitiesAndInactiveOrMissingMaterialsAtomically()
            throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7701, 'PRD-MATERIAL-VALIDATION', 7501)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(put("/api/production/tasks/7701/materials")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materials":[
                                  {"inventoryMaterialId":7801,"requiredQuantity":0},
                                  {"inventoryMaterialId":7801,"requiredQuantity":5}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['error']['fields']['materials[0].requiredQuantity']").exists())
                .andExpect(jsonPath("$['error']['fields']['materials[1].inventoryMaterialId']").value(
                        "This inventory material is already assigned to the production task."));

        mockMvc.perform(put("/api/production/tasks/7701/materials")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materials":[
                                  {"inventoryMaterialId":7803,"requiredQuantity":5},
                                  {"inventoryMaterialId":999999,"requiredQuantity":1}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['error']['fields']['materials[0].inventoryMaterialId']").value(
                        "Select an active inventory material for new production requirements."))
                .andExpect(jsonPath("$['error']['fields']['materials[1].inventoryMaterialId']").value(
                        "Inventory material was not found."));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_material_requirements", Integer.class)).isZero();
    }

    @Test
    void materialAvailabilityUsesInventorySourceOfTruthAtSufficientAndInsufficientBoundary()
            throws Exception {
        insertOrder(7501, "ORD-MATERIAL-BOUNDARY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7710, 'PRD-BOUNDARY-7710', 7501)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7710, 7802, 5.000)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(get("/api/production/tasks/7710/material-availability").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMaterialRequirements").value(true))
                .andExpect(jsonPath("$.allMaterialsAvailable").value(true))
                .andExpect(jsonPath("$.canStart").value(true))
                .andExpect(jsonPath("$.materials[0].requiredQuantity").value(5.000))
                .andExpect(jsonPath("$.materials[0].currentQuantity").value(5.000))
                .andExpect(jsonPath("$.materials[0].availabilityState").value("AVAILABLE"));

        jdbcTemplate.update(
                "UPDATE production_task_material_requirements SET required_quantity = 5.001 WHERE production_task_id = 7710 AND inventory_material_id = 7802");

        mockMvc.perform(get("/api/production/tasks/7710/material-availability").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allMaterialsAvailable").value(false))
                .andExpect(jsonPath("$.canStart").value(false))
                .andExpect(jsonPath("$.materials[0].requiredQuantity").value(5.001))
                .andExpect(jsonPath("$.materials[0].currentQuantity").value(5.000))
                .andExpect(jsonPath("$.materials[0].availabilityState").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void productionStartIsBlockedByShortageAndSucceedsWhenQuantityExactlyMatchesStock()
            throws Exception {
        insertOrder(7501, "ORD-START-GATE", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7711, 'PRD-START-7711', 7501)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7711, 7802, 5.001)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(post("/api/production/tasks/7711/start").cookie(production))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_MATERIAL_SHORTAGE"))
                .andExpect(jsonPath("$.error.fields.materials").value(
                        "Resolve every material shortage before starting production."))
                .andExpect(jsonPath("$['error']['fields']['materials[0]']").value(
                        "RAW-PROD-002 requires 5.001 cone but Inventory currently has 5.000 cone."));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks WHERE id = 7711", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT started_at FROM production_tasks WHERE id = 7711", java.sql.Timestamp.class))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7802", java.math.BigDecimal.class))
                .isEqualByComparingTo("5.000");

        jdbcTemplate.update(
                "UPDATE production_task_material_requirements SET required_quantity = 5.000 WHERE production_task_id = 7711 AND inventory_material_id = 7802");

        mockMvc.perform(post("/api/production/tasks/7711/start").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Production started successfully."))
                .andExpect(jsonPath("$.task.task.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.task.task.startedAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks WHERE id = 7711", String.class))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = 7501", String.class))
                .isEqualTo("IN_PRODUCTION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_status_history WHERE order_id = 7501 AND from_status = 'CONFIRMED' AND to_status = 'IN_PRODUCTION'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7802", java.math.BigDecimal.class))
                .isEqualByComparingTo("5.000");

        mockMvc.perform(post("/api/production/tasks/7711/start").cookie(production))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_TASK_START_NOT_ALLOWED"));
    }

    @Test
    void productionStartRequiresSavedMaterialsAndManageableRole() throws Exception {
        insertOrder(7501, "ORD-START-REQUIRED", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7712, 'PRD-START-7712', 7501)");

        mockMvc.perform(post("/api/production/tasks/7712/start")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_TASK_START_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.fields.materials").value(
                        "Assign at least one required inventory material before starting production."));

        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7712, 7801, 10.000)");

        mockMvc.perform(get("/api/production/tasks/7712/material-availability")
                        .cookie(sessionFor(7997, UserRole.ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canStart").value(true));

        mockMvc.perform(post("/api/production/tasks/7712/start")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void productionMaterialUsageDeductsApprovedRequirementsExactlyOnceAndStoresHistory() throws Exception {
        insertOrder(7501, "ORD-USAGE-READY", "IN_PRODUCTION", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7720, 'PRD-USAGE-7720', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7720, 7801, 20.000)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7720, 7802, 5.000)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(post("/api/production/tasks/7720/material-usage").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Production material usage recorded successfully."))
                .andExpect(jsonPath("$.usage.usageRecorded").value(true))
                .andExpect(jsonPath("$.usage.materials.length()").value(2))
                .andExpect(jsonPath("$.usage.materials[0].quantityUsed").value(20.000))
                .andExpect(jsonPath("$.usage.materials[0].remainingQuantity").value(100.000))
                .andExpect(jsonPath("$.usage.materials[1].quantityUsed").value(5.000))
                .andExpect(jsonPath("$.usage.materials[1].remainingQuantity").value(0.000));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7801", BigDecimal.class))
                .isEqualByComparingTo("100.000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7802", BigDecimal.class))
                .isEqualByComparingTo("0.000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_material_usage WHERE production_task_id = 7720", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_material_usage WHERE production_task_id = 7720 AND recorded_by_user_id = 7999", Integer.class))
                .isEqualTo(2);

        mockMvc.perform(post("/api/production/tasks/7720/material-usage").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.usageRecorded").value(true))
                .andExpect(jsonPath("$.usage.materials.length()").value(2));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7801", BigDecimal.class))
                .isEqualByComparingTo("100.000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7802", BigDecimal.class))
                .isEqualByComparingTo("0.000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_material_usage WHERE production_task_id = 7720", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void productionMaterialUsageFailureDoesNotPartiallyDeductOrRecordHistory() throws Exception {
        insertOrder(7501, "ORD-USAGE-SHORT", "IN_PRODUCTION", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7721, 'PRD-USAGE-7721', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7721, 7801, 20.000)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7721, 7802, 5.001)");

        mockMvc.perform(post("/api/production/tasks/7721/material-usage")
                        .cookie(sessionFor(7999, UserRole.PRODUCTION_MANAGER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_MATERIAL_SHORTAGE"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7801", BigDecimal.class))
                .isEqualByComparingTo("120.000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = 7802", BigDecimal.class))
                .isEqualByComparingTo("5.000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM production_task_material_usage WHERE production_task_id = 7721", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks WHERE id = 7721", String.class))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void materialUsageRequiresInProgressTaskAndLocksRequirementsAfterStart() throws Exception {
        insertOrder(7501, "ORD-USAGE-LOCK", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7722, 'PRD-USAGE-7722', 7501)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7722, 7801, 10.000)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(post("/api/production/tasks/7722/material-usage").cookie(production))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_MATERIAL_USAGE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/production/tasks/7722/start").cookie(production))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/production/tasks/7722/materials")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materials\":[{\"inventoryMaterialId\":7801,\"requiredQuantity\":5}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_MATERIAL_REQUIREMENTS_LOCKED"));
    }

    @Test
    void productionCompletionRequiresRecordedUsageAndSynchronizesOrderWhenLastTaskCompletes() throws Exception {
        insertOrder(7501, "ORD-PROGRESS-COMPLETE", "IN_PRODUCTION", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7730, 'PRD-PROGRESS-7730', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7730, 7801, 20.000)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(patch("/api/production/tasks/7730/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_STATUS_TRANSITION_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.fields.materialUsage").exists());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks WHERE id = 7730", String.class))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = 7501", String.class))
                .isEqualTo("IN_PRODUCTION");

        mockMvc.perform(post("/api/production/tasks/7730/material-usage").cookie(production))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/production/tasks/7730/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.fields.qualityControl").exists());

        mockMvc.perform(patch("/api/production/tasks/7730/quality-control")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PASSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.qualityControlResult").value("PASSED"));

        mockMvc.perform(patch("/api/production/tasks/7730/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Production status updated successfully."))
                .andExpect(jsonPath("$.task.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.task.task.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.task.order.currentStatus").value("READY_FOR_DELIVERY"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks WHERE id = 7730", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = 7501", String.class))
                .isEqualTo("READY_FOR_DELIVERY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_status_history WHERE order_id = 7501 AND from_status = 'IN_PRODUCTION' AND to_status = 'READY_FOR_DELIVERY'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void orderStaysInProductionUntilEveryTaskForTheOrderIsCompleted() throws Exception {
        insertOrder(7501, "ORD-MULTI-PRODUCTION", "IN_PRODUCTION", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7731, 'PRD-MULTI-7731', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7732, 'PRD-MULTI-7732', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        for (long taskId : new long[] {7731L, 7732L}) {
            jdbcTemplate.update(
                    "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (?, 7801, 1.000)",
                    taskId);
            jdbcTemplate.update(
                    "INSERT INTO production_task_material_usage (production_task_id, inventory_material_id, quantity_used, recorded_by_user_id) VALUES (?, 7801, 1.000, 7999)",
                    taskId);
            jdbcTemplate.update(
                    "UPDATE production_tasks SET quality_control_result = 'PASSED', quality_checked_by_user_id = 7999, quality_checked_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                    taskId);
        }
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(patch("/api/production/tasks/7731/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.order.currentStatus").value("IN_PRODUCTION"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = 7501", String.class))
                .isEqualTo("IN_PRODUCTION");

        mockMvc.perform(patch("/api/production/tasks/7732/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.order.currentStatus").value("READY_FOR_DELIVERY"));
    }

    @Test
    void administratorCanCompleteProductionAndIsRecordedOnOrderProgressHistory() throws Exception {
        insertOrder(7501, "ORD-PROGRESS-ADMIN", "IN_PRODUCTION", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7734, 'PRD-PROGRESS-7734', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7734, 7801, 1.000)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_usage (production_task_id, inventory_material_id, quantity_used, recorded_by_user_id) VALUES (7734, 7801, 1.000, 7999)");
        jdbcTemplate.update(
                "UPDATE production_tasks SET quality_control_result = 'PASSED', quality_checked_by_user_id = 7997, quality_checked_at = CURRENT_TIMESTAMP(6) WHERE id = 7734");

        mockMvc.perform(patch("/api/production/tasks/7734/status")
                        .cookie(sessionFor(7997, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.status").value("COMPLETED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT changed_by_user_id FROM order_status_history WHERE order_id = 7501 AND to_status = 'READY_FOR_DELIVERY'", Long.class))
                .isEqualTo(7997L);
    }

    @Test
    void invalidProductionStatusAndInvalidTransitionsAreRejectedAndAuthorizedRolesAreEnforced() throws Exception {
        insertOrder(7501, "ORD-PROGRESS-VALIDATION", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7733, 'PRD-PROGRESS-7733', 7501)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(patch("/api/production/tasks/7733/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.status").value(
                        "Status must be PENDING, IN_PROGRESS, or COMPLETED."));

        mockMvc.perform(patch("/api/production/tasks/7733/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_STATUS_TRANSITION_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/production/tasks/7733/status")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void productionRecordsSeparateActiveAndCompletedAndQualityControlIsProductionScoped() throws Exception {
        insertOrder(7501, "ORD-RECORD-ACTIVE", "IN_PRODUCTION", 7601);
        insertOrder(7502, "ORD-RECORD-COMPLETED", "READY_FOR_DELIVERY", 7602);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at) VALUES (7740, 'PRD-RECORD-ACTIVE', 7501, 'IN_PROGRESS', CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status, started_at, completed_at, quality_control_result, quality_checked_by_user_id, quality_checked_at) VALUES (7741, 'PRD-RECORD-COMPLETED', 7502, 'COMPLETED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'PASSED', 7999, CURRENT_TIMESTAMP(6))");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (7740, 7801, 1.000)");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_usage (production_task_id, inventory_material_id, quantity_used, recorded_by_user_id) VALUES (7740, 7801, 1.000, 7999)");
        Cookie production = sessionFor(7999, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(get("/api/production/tasks").param("view", "ACTIVE").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].task.taskNumber").value("PRD-RECORD-ACTIVE"))
                .andExpect(jsonPath("$[0].task.qualityControlResult").value("PENDING"))
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-RECORD-ACTIVE"));

        mockMvc.perform(get("/api/production/tasks").param("view", "COMPLETED").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].task.taskNumber").value("PRD-RECORD-COMPLETED"))
                .andExpect(jsonPath("$[0].task.qualityControlResult").value("PASSED"))
                .andExpect(jsonPath("$[0].readyForDelivery").value(true));

        mockMvc.perform(patch("/api/production/tasks/7740/quality-control")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.qualityControlResult").value("FAILED"));

        mockMvc.perform(patch("/api/production/tasks/7740/status")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.fields.qualityControl").exists());

        mockMvc.perform(patch("/api/production/tasks/7740/quality-control")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PASSED\"}"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT quality_checked_by_user_id FROM production_tasks WHERE id = 7740", Long.class))
                .isEqualTo(7999L);
    }

    @Test
    void materialRequirementManagementAllowsAdministratorButRejectsSalesOfficer() throws Exception {
        insertOrder(7501, "ORD-PRODUCTION-READY", "CONFIRMED", 7601);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id) VALUES (7701, 'PRD-MATERIAL-AUTH', 7501)");
        String payload = "{\"materials\":[{\"inventoryMaterialId\":7801,\"requiredQuantity\":10}]}";

        mockMvc.perform(put("/api/production/tasks/7701/materials")
                        .cookie(sessionFor(7997, UserRole.ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/production/tasks/7701/materials")
                        .cookie(sessionFor(7998, UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    private void seedBaseData() {
        insertUser(7101, "production-customer@example.com", UserRole.CUSTOMER);
        insertUser(7999, "production-manager@example.com", UserRole.PRODUCTION_MANAGER);
        insertUser(7998, "production-sales@example.com", UserRole.SALES_OFFICER);
        insertUser(7997, "production-admin@example.com", UserRole.ADMINISTRATOR);
        jdbcTemplate.update(
                "INSERT INTO garment_categories (id, name, status) VALUES (7401, 'Production Garments', 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO garment_products (id, category_id, name, status) VALUES (7201, 7401, 'Production Shirt', 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO garment_product_variants (id, product_id, size, color, price, status) VALUES (7301, 7201, 'L', 'Navy', 3100.00, 'AVAILABLE')");
        jdbcTemplate.update(
                "INSERT INTO inventory_materials (id, material_code, material_name, material_type, unit_of_measure, current_quantity, low_stock_threshold, status) VALUES (7801, 'FAB-PROD-001', 'Cotton Twill', 'FABRIC', 'metre', 120.000, 20.000, 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO inventory_materials (id, material_code, material_name, material_type, unit_of_measure, current_quantity, low_stock_threshold, status) VALUES (7802, 'RAW-PROD-002', 'Polyester Thread', 'RAW_MATERIAL', 'cone', 5.000, 2.000, 'ACTIVE')");
        jdbcTemplate.update(
                "INSERT INTO inventory_materials (id, material_code, material_name, material_type, unit_of_measure, current_quantity, low_stock_threshold, status) VALUES (7803, 'FAB-PROD-003', 'Archived Fabric', 'FABRIC', 'metre', 50.000, 10.000, 'DISCONTINUED')");
    }

    private void insertOrder(long orderId, String orderNumber, String status, long orderItemId) {
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (?, 7101, ?, ?)",
                orderId, orderNumber, status);
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    id, order_id, product_id, variant_id, quantity,
                    selected_size, selected_color, unit_price_snapshot
                ) VALUES (?, ?, 7201, 7301, 3, 'L', 'Navy', 3100.00)
                """,
                orderItemId, orderId);
    }

    private void insertUser(long id, String email, UserRole role) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, role, is_active) VALUES (?, ?, 'not-used', ?, ?, TRUE)",
                id, email, role == UserRole.CUSTOMER ? "Production Customer" : "Production Staff", role.name());
    }

    private Cookie sessionFor(long userId, UserRole role) {
        UserAccount account = new UserAccount(
                userId, "session-" + userId + "@example.com", "not-used", "Session User", role, true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM production_task_material_usage");
        jdbcTemplate.update("DELETE FROM production_task_material_requirements");
        jdbcTemplate.update("DELETE FROM production_task_details");
        jdbcTemplate.update("DELETE FROM production_tasks");
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        jdbcTemplate.update("DELETE FROM inventory_materials WHERE id IN (7801, 7802, 7803)");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (7101, 7997, 7998, 7999)");
    }
}

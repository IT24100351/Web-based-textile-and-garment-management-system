package lk.ac.sliit.tgms.integration;

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
import org.hamcrest.Matchers;
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

/**
 * TGMS-81 full-system integration coverage.
 *
 * <p>The fixtures create only authenticated actors. Product, Supplier, Inventory, Order,
 * Production and Delivery records are then created or transitioned through the same HTTP APIs a
 * real user reaches. Assertions verify the stable foreign-key handoffs and the final customer
 * tracking state, so the test does not manufacture impossible downstream states directly in the
 * database.
 */
@SpringBootTest
class CrossModuleEndToEndIntegrationTests {

    private static final long CUSTOMER_ID = 98101L;
    private static final long SUPPLIER_USER_ID = 98102L;
    private static final long INVENTORY_MANAGER_ID = 98103L;
    private static final long PRODUCTION_MANAGER_ID = 98104L;
    private static final long SALES_OFFICER_ID = 98105L;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanCrossModuleData();
        insertUser(CUSTOMER_ID, "tgms81-customer@example.com", "TGMS 81 Customer", UserRole.CUSTOMER);
        insertUser(SUPPLIER_USER_ID, "tgms81-supplier@example.com", "TGMS 81 Supplier", UserRole.SUPPLIER);
        insertUser(INVENTORY_MANAGER_ID, "tgms81-inventory@example.com", "TGMS 81 Inventory", UserRole.INVENTORY_MANAGER);
        insertUser(PRODUCTION_MANAGER_ID, "tgms81-production@example.com", "TGMS 81 Production", UserRole.PRODUCTION_MANAGER);
        insertUser(SALES_OFFICER_ID, "tgms81-sales@example.com", "TGMS 81 Sales", UserRole.SALES_OFFICER);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void tearDown() {
        cleanCrossModuleData();
    }

    @Test
    void completeGarmentWorkflowUsesStableModuleHandoffsAndFinishesWithCustomerTracking()
            throws Exception {
        Cookie customer = sessionFor(CUSTOMER_ID, UserRole.CUSTOMER);
        Cookie supplier = sessionFor(SUPPLIER_USER_ID, UserRole.SUPPLIER);
        Cookie inventory = sessionFor(INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        Cookie production = sessionFor(PRODUCTION_MANAGER_ID, UserRole.PRODUCTION_MANAGER);
        Cookie sales = sessionFor(SALES_OFFICER_ID, UserRole.SALES_OFFICER);

        // Product -> Order: create the sellable master record through Product Management.
        mockMvc.perform(post("/api/products")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "TGMS81 Integration Shirt",
                                  "category": "TGMS81 Integration Garments",
                                  "size": "M",
                                  "color": "Ocean Blue",
                                  "price": 3250.00,
                                  "availability": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.name").value("TGMS81 Integration Shirt"))
                .andExpect(jsonPath("$.product.variants[0].status").value("AVAILABLE"));

        long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_products WHERE name = ?",
                Long.class,
                "TGMS81 Integration Shirt");
        long variantId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_product_variants WHERE product_id = ?",
                Long.class,
                productId);

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productId": %d, "variantId": %d, "quantity": 2}
                                  ]
                                }
                                """.formatted(productId, variantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].variantId").value(variantId))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value("3250.00"));

        long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE customer_id = ?", Long.class, CUSTOMER_ID);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT product_id FROM order_items WHERE order_id = ?",
                        Long.class,
                        orderId))
                .isEqualTo(productId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT variant_id FROM order_items WHERE order_id = ?",
                        Long.class,
                        orderId))
                .isEqualTo(variantId);

        // Supplier -> Inventory: create a real supplier profile/supply, then reference its stable ID.
        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName": "TGMS81 Textile Supplier",
                                  "contactPhone": "0112345678",
                                  "address": "81 Textile Road, Colombo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profile.userId").value(SUPPLIER_USER_ID));

        mockMvc.perform(post("/api/material-supplies")
                        .cookie(supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "TGMS81-FAB-001",
                                  "materialName": "TGMS81 Cotton Fabric",
                                  "materialDescription": "Integration-test production fabric",
                                  "quantity": 120.000,
                                  "unitOfMeasure": "metre",
                                  "unitPrice": 800.00,
                                  "deliveryLeadTimeDays": 2,
                                  "deliveryNotes": "Cross-module test supply"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supply.materialCode").value("TGMS81-FAB-001"))
                .andExpect(jsonPath("$.supply.status").value("ACTIVE"));

        long supplyId = jdbcTemplate.queryForObject(
                "SELECT id FROM material_supplies WHERE material_code = ?",
                Long.class,
                "TGMS81-FAB-001");

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(inventory)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceMaterialSupplyId": %d,
                                  "materialCode": "TGMS81-INV-FAB-001",
                                  "materialName": "TGMS81 Cotton Fabric Stock",
                                  "materialDescription": "Inventory sourced from TGMS81 supplier supply",
                                  "materialType": "FABRIC",
                                  "unitOfMeasure": "metre",
                                  "currentQuantity": 100.000,
                                  "lowStockThreshold": 20.000
                                }
                                """.formatted(supplyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.material.sourceMaterialSupplyId").value(supplyId))
                .andExpect(jsonPath("$.material.currentQuantity").value("100.000"));

        long inventoryMaterialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                "TGMS81-INV-FAB-001");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT source_material_supply_id FROM inventory_materials WHERE id = ?",
                        Long.class,
                        inventoryMaterialId))
                .isEqualTo(supplyId);

        // Order -> Production: only a confirmed order becomes Production-eligible.
        mockMvc.perform(patch("/api/orders/{orderId}/status", orderId)
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/production/tasks/eligible-orders").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == %d)].currentStatus".formatted(orderId))
                        .value(Matchers.contains("CONFIRMED")));

        mockMvc.perform(get("/api/production/tasks/material-options").cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.inventoryMaterialId == %d)].materialCode".formatted(inventoryMaterialId))
                        .value(Matchers.contains("TGMS81-INV-FAB-001")));

        mockMvc.perform(post("/api/production/tasks")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.orderId").value(orderId))
                .andExpect(jsonPath("$.task.status").value("PENDING"));

        long productionTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM production_tasks WHERE order_id = ?", Long.class, orderId);

        mockMvc.perform(put("/api/production/tasks/{taskId}/materials", productionTaskId)
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materials":[{"inventoryMaterialId":%d,"requiredQuantity":12.000}]}
                                """.formatted(inventoryMaterialId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.materialRequirements[0].inventoryMaterialId")
                        .value(inventoryMaterialId));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT inventory_material_id FROM production_task_material_requirements WHERE production_task_id = ?",
                        Long.class,
                        productionTaskId))
                .isEqualTo(inventoryMaterialId);

        mockMvc.perform(get("/api/production/tasks/{taskId}/material-availability", productionTaskId)
                        .cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allMaterialsAvailable").value(true))
                .andExpect(jsonPath("$.materials[0].availabilityState").value("AVAILABLE"));

        mockMvc.perform(post("/api/production/tasks/{taskId}/start", productionTaskId)
                        .cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.task.order.currentStatus").value("IN_PRODUCTION"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("IN_PRODUCTION");

        // Inventory -> Production usage: Production calls Inventory to deduct the approved requirement.
        mockMvc.perform(post("/api/production/tasks/{taskId}/material-usage", productionTaskId)
                        .cookie(production))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usage.usageRecorded").value(true));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        BigDecimal.class,
                        inventoryMaterialId))
                .isEqualByComparingTo("88.000");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT inventory_material_id FROM production_task_material_usage WHERE production_task_id = ?",
                        Long.class,
                        productionTaskId))
                .isEqualTo(inventoryMaterialId);

        mockMvc.perform(patch("/api/production/tasks/{taskId}/quality-control", productionTaskId)
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"PASSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.qualityControlResult").value("PASSED"));

        // Production completion -> Delivery readiness through the Order service contract.
        mockMvc.perform(patch("/api/production/tasks/{taskId}/status", productionTaskId)
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.task.order.currentStatus").value("READY_FOR_DELIVERY"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("READY_FOR_DELIVERY");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_status_history WHERE order_id = ? AND from_status = 'IN_PRODUCTION' AND to_status = 'READY_FOR_DELIVERY'",
                        Integer.class, orderId))
                .isEqualTo(1);

        mockMvc.perform(get("/api/deliveries/eligible-orders").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(orderId))
                .andExpect(jsonPath("$[0].readyForDelivery").value(true))
                .andExpect(jsonPath("$[0].items[0].productId").value(productId))
                .andExpect(jsonPath("$[0].items[0].variantId").value(variantId))
                .andExpect(jsonPath("$[0].items[0].productName")
                        .value("TGMS81 Integration Shirt"));

        mockMvc.perform(get("/api/deliveries/eligible-orders/{orderId}", orderId).cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.readyForDelivery").value(true))
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].variantId").value(variantId));

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "scheduledAt": "2099-01-02T10:00:00Z",
                                  "deliveryAddress": "81 Customer Lane, Colombo",
                                  "deliveryNotes": "TGMS81 end-to-end delivery"
                                }
                                """.formatted(orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.orderId").value(orderId))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));

        long deliveryId = jdbcTemplate.queryForObject(
                "SELECT id FROM deliveries WHERE order_id = ? AND status = 'SCHEDULED'",
                Long.class,
                orderId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT order_id FROM deliveries WHERE id = ?", Long.class, deliveryId))
                .isEqualTo(orderId);
        mockMvc.perform(get("/api/deliveries/eligible-orders").cookie(sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.orderId == %d)]".formatted(orderId)).isEmpty());

        // Delivery -> Customer tracking: only the owning Customer reads the delivery progress.
        mockMvc.perform(get("/api/deliveries/mine/orders/{orderId}/tracking", orderId)
                        .cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.hasDelivery").value(true))
                .andExpect(jsonPath("$.delivery.deliveryId").value(deliveryId))
                .andExpect(jsonPath("$.delivery.status").value("SCHEDULED"));

        mockMvc.perform(patch("/api/deliveries/{deliveryId}/status", deliveryId)
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OUT_FOR_DELIVERY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("OUT_FOR_DELIVERY"));

        mockMvc.perform(get("/api/deliveries/mine/orders/{orderId}/tracking", orderId)
                        .cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("OUT_FOR_DELIVERY"));

        mockMvc.perform(patch("/api/deliveries/{deliveryId}/status", deliveryId)
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("COMPLETED");

        mockMvc.perform(get("/api/orders/mine/{orderId}/tracking", orderId).cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("COMPLETED"));

        mockMvc.perform(get("/api/deliveries/mine/orders/{orderId}/tracking", orderId)
                        .cookie(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivery.status").value("DELIVERED"));

        // Audit trail proves the cross-module services, rather than direct SQL, advanced the Order.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM order_status_history WHERE order_id = ?",
                        Integer.class,
                        orderId))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT to_status FROM order_status_history WHERE order_id = ? ORDER BY id",
                        String.class,
                        orderId))
                .containsExactly("CONFIRMED", "IN_PRODUCTION", "READY_FOR_DELIVERY", "COMPLETED");
    }

    @Test
    void productionShortageBlocksStartWithoutChangingOrderOrInventory() throws Exception {
        ScenarioIds scenario = createConfirmedOrderAndInventory("SHORT", new BigDecimal("5.000"));
        Cookie production = sessionFor(PRODUCTION_MANAGER_ID, UserRole.PRODUCTION_MANAGER);

        mockMvc.perform(post("/api/production/tasks")
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":%d}".formatted(scenario.orderId())))
                .andExpect(status().isOk());
        long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM production_tasks WHERE order_id = ?", Long.class, scenario.orderId());

        mockMvc.perform(put("/api/production/tasks/{taskId}/materials", taskId)
                        .cookie(production)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materials":[{"inventoryMaterialId":%d,"requiredQuantity":10.000}]}
                                """.formatted(scenario.inventoryMaterialId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/production/tasks/{taskId}/start", taskId).cookie(production))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCTION_MATERIAL_SHORTAGE"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM production_tasks WHERE id = ?", String.class, taskId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM orders WHERE id = ?", String.class, scenario.orderId()))
                .isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                        BigDecimal.class,
                        scenario.inventoryMaterialId()))
                .isEqualByComparingTo("5.000");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM production_task_material_usage WHERE production_task_id = ?",
                        Integer.class,
                        taskId))
                .isZero();
    }

    @Test
    void deliveryCannotBypassProductionReadinessAndCustomerCannotTrackAnotherOrder()
            throws Exception {
        ScenarioIds scenario = createConfirmedOrderAndInventory("BOUNDARY", new BigDecimal("50.000"));
        Cookie sales = sessionFor(SALES_OFFICER_ID, UserRole.SALES_OFFICER);
        Cookie customer = sessionFor(CUSTOMER_ID, UserRole.CUSTOMER);

        mockMvc.perform(post("/api/deliveries")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "scheduledAt": "2099-01-03T10:00:00Z",
                                  "deliveryAddress": "Premature Delivery Address",
                                  "deliveryNotes": "Must be rejected before production completes"
                                }
                                """.formatted(scenario.orderId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_READY_FOR_DELIVERY"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM deliveries WHERE order_id = ?",
                        Integer.class,
                        scenario.orderId()))
                .isZero();

        insertUser(98106L, "tgms81-other-customer@example.com", "TGMS 81 Other Customer", UserRole.CUSTOMER);
        Cookie otherCustomer = sessionFor(98106L, UserRole.CUSTOMER);
        mockMvc.perform(post("/api/orders/mine")
                        .cookie(otherCustomer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"variantId":%d,"quantity":1}]}
                                """.formatted(scenario.productId(), scenario.variantId())))
                .andExpect(status().isCreated());
        long otherOrderId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM orders WHERE customer_id = ?", Long.class, 98106L);

        mockMvc.perform(get("/api/deliveries/mine/orders/{orderId}/tracking", otherOrderId)
                        .cookie(customer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
    }

    private ScenarioIds createConfirmedOrderAndInventory(String suffix, BigDecimal openingQuantity)
            throws Exception {
        Cookie customer = sessionFor(CUSTOMER_ID, UserRole.CUSTOMER);
        Cookie supplier = sessionFor(SUPPLIER_USER_ID, UserRole.SUPPLIER);
        Cookie inventory = sessionFor(INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        Cookie sales = sessionFor(SALES_OFFICER_ID, UserRole.SALES_OFFICER);

        String productName = "TGMS81 " + suffix + " Shirt";
        String materialCode = "TGMS81-" + suffix + "-SUPPLY";
        String inventoryCode = "TGMS81-" + suffix + "-INV";

        mockMvc.perform(post("/api/products")
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "category": "TGMS81 Boundary Garments",
                                  "size": "M",
                                  "color": "Grey",
                                  "price": 2500.00,
                                  "availability": "AVAILABLE"
                                }
                                """.formatted(productName)))
                .andExpect(status().isCreated());
        long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_products WHERE name = ?", Long.class, productName);
        long variantId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_product_variants WHERE product_id = ?", Long.class, productId);

        mockMvc.perform(post("/api/orders/mine")
                        .cookie(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"variantId":%d,"quantity":1}]}
                                """.formatted(productId, variantId)))
                .andExpect(status().isCreated());
        long orderId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM orders WHERE customer_id = ?", Long.class, CUSTOMER_ID);

        ensureSupplierProfile(supplier);
        mockMvc.perform(post("/api/material-supplies")
                        .cookie(supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "%s",
                                  "materialName": "TGMS81 %s Fabric",
                                  "materialDescription": "Boundary fixture",
                                  "quantity": 100.000,
                                  "unitOfMeasure": "metre",
                                  "unitPrice": 500.00,
                                  "deliveryLeadTimeDays": 1,
                                  "deliveryNotes": "Boundary fixture"
                                }
                                """.formatted(materialCode, suffix)))
                .andExpect(status().isCreated());
        long supplyId = jdbcTemplate.queryForObject(
                "SELECT id FROM material_supplies WHERE material_code = ?", Long.class, materialCode);

        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(inventory)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceMaterialSupplyId": %d,
                                  "materialCode": "%s",
                                  "materialName": "TGMS81 %s Inventory",
                                  "materialDescription": "Boundary fixture inventory",
                                  "materialType": "FABRIC",
                                  "unitOfMeasure": "metre",
                                  "currentQuantity": %s,
                                  "lowStockThreshold": 2.000
                                }
                                """.formatted(supplyId, inventoryCode, suffix, openingQuantity.toPlainString())))
                .andExpect(status().isCreated());
        long inventoryMaterialId = jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?", Long.class, inventoryCode);

        mockMvc.perform(patch("/api/orders/{orderId}/status", orderId)
                        .cookie(sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk());

        return new ScenarioIds(orderId, inventoryMaterialId, productId, variantId);
    }

    private void ensureSupplierProfile(Cookie supplier) throws Exception {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier_profiles WHERE user_id = ?",
                Integer.class,
                SUPPLIER_USER_ID);
        if (existing != null && existing > 0) {
            return;
        }
        mockMvc.perform(put("/api/supplier-profile")
                        .cookie(supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName": "TGMS81 Boundary Supplier",
                                  "contactPhone": "0119998888",
                                  "address": "81 Boundary Road, Colombo"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private void insertUser(long id, String email, String fullName, UserRole role) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, full_name, role, is_active)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """,
                id,
                email,
                "not-used",
                fullName,
                role.name());
    }

    private Cookie sessionFor(long userId, UserRole role) {
        UserAccount account = new UserAccount(
                userId,
                "tgms81-session-" + userId + "@example.com",
                "not-used",
                "TGMS 81 Session",
                role,
                true);
        Cookie cookie = new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
        cookie.setPath("/");
        return cookie;
    }

    private void cleanCrossModuleData() {
        // Child-to-parent order follows the production schema's RESTRICT foreign keys.
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM deliveries");
        jdbcTemplate.update("DELETE FROM production_task_material_usage");
        jdbcTemplate.update("DELETE FROM production_task_material_requirements");
        jdbcTemplate.update("DELETE FROM production_task_details");
        jdbcTemplate.update("DELETE FROM production_tasks");
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM quotation_items");
        jdbcTemplate.update("DELETE FROM quotations");
        jdbcTemplate.update("DELETE FROM inventory_materials");
        jdbcTemplate.update("DELETE FROM material_supplies");
        jdbcTemplate.update("DELETE FROM supplier_profiles");
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 98101 AND 98106");
    }

    private record ScenarioIds(long orderId, long inventoryMaterialId, long productId, long variantId) {}
}

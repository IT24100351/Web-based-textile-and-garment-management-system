package lk.ac.sliit.tgms.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.delivery.DeliveryService;
import lk.ac.sliit.tgms.inventory.InventoryMaterialService;
import lk.ac.sliit.tgms.order.OrderService;
import lk.ac.sliit.tgms.production.ProductionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "tgms.notifications.enabled=true")
class NotificationOperationalEventIntegrationTests {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private InventoryMaterialService inventoryMaterialService;
    @Autowired private OrderService orderService;
    @Autowired private ProductionTaskService productionTaskService;
    @Autowired private DeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM production_task_material_usage");
        jdbcTemplate.update("DELETE FROM production_task_material_requirements");
        jdbcTemplate.update("DELETE FROM production_task_details");
        jdbcTemplate.update("DELETE FROM deliveries");
        jdbcTemplate.update("DELETE FROM production_tasks");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_payment_records");
        jdbcTemplate.update("DELETE FROM order_invoices");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM inventory_materials");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'tgms73-event-%'");
    }

    @Test
    void lowStockTransitionNotifiesOnlyRelevantOperationalRoles() throws Exception {
        long inventoryManagerId = insertUser(73101, "tgms73-event-inventory@example.com", UserRole.INVENTORY_MANAGER);
        long administratorId = insertUser(73102, "tgms73-event-admin@example.com", UserRole.ADMINISTRATOR);
        long customerId = insertUser(73103, "tgms73-event-customer@example.com", UserRole.CUSTOMER);
        long materialId = insertInventoryMaterial();

        inventoryMaterialService.consumeStock(materialId, new BigDecimal("2.000"));

        awaitCount("SELECT COUNT(*) FROM notifications WHERE kind = 'LOW_STOCK'", 2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE kind = 'LOW_STOCK' AND recipient_user_id IN (?, ?)",
                Integer.class, inventoryManagerId, administratorId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ?", Integer.class, customerId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = ?", BigDecimal.class, materialId))
                .isEqualByComparingTo("4.000");
    }

    @Test
    void orderStatusNotificationUsesPersistedOrderCustomerAndActualTransition() throws Exception {
        long customerId = insertUser(73111, "tgms73-event-order-customer@example.com", UserRole.CUSTOMER);
        long salesId = insertUser(73112, "tgms73-event-sales@example.com", UserRole.SALES_OFFICER);
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (73120, ?, 'ORD-TGMS73', 'PENDING')",
                customerId);

        orderService.updateStatus(salesId, 73120, "CONFIRMED");

        awaitCount(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = 73111 AND kind = 'ORDER_STATUS'",
                1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = 73120", String.class)).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_module FROM notifications WHERE recipient_user_id = ?",
                String.class, customerId)).isEqualTo("ORDER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_record_id FROM notifications WHERE recipient_user_id = ?",
                Long.class, customerId)).isEqualTo(73120L);
    }


    @Test
    void productionStartNotificationComesFromRealProductionLifecycle() throws Exception {
        long customerId = insertUser(73121, "tgms73-event-production-customer@example.com", UserRole.CUSTOMER);
        long productionManagerId = insertUser(73122, "tgms73-event-production-manager@example.com", UserRole.PRODUCTION_MANAGER);
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (73123, ?, 'ORD-PROD-73', 'CONFIRMED')",
                customerId);
        jdbcTemplate.update(
                """
                INSERT INTO inventory_materials
                    (id, material_code, material_name, material_description, material_type,
                     unit_of_measure, current_quantity, low_stock_threshold, status)
                VALUES (73124, 'INV-PROD-73', 'Production fabric', 'Production notification test',
                        'FABRIC', 'metre', 10.000, 2.000, 'ACTIVE')
                """);
        jdbcTemplate.update(
                "INSERT INTO production_tasks (id, task_number, order_id, status) VALUES (73125, 'PT-TGMS73', 73123, 'PENDING')");
        jdbcTemplate.update(
                "INSERT INTO production_task_material_requirements (production_task_id, inventory_material_id, required_quantity) VALUES (73125, 73124, 1.000)");

        productionTaskService.startTask(73125, productionManagerId);

        awaitCount(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = 73121 AND kind = 'PRODUCTION_STATUS'",
                1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM production_tasks WHERE id = 73125", String.class)).isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = 73123", String.class)).isEqualTo("IN_PRODUCTION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_module FROM notifications WHERE recipient_user_id = ?",
                String.class, customerId)).isEqualTo("PRODUCTION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_record_id FROM notifications WHERE recipient_user_id = ?",
                Long.class, customerId)).isEqualTo(73125L);
    }

    @Test
    void deliveryStatusNotificationComesFromRealDeliveryLifecycle() throws Exception {
        long customerId = insertUser(73131, "tgms73-event-delivery-customer@example.com", UserRole.CUSTOMER);
        long salesId = insertUser(73132, "tgms73-event-delivery-sales@example.com", UserRole.SALES_OFFICER);
        jdbcTemplate.update(
                "INSERT INTO orders (id, customer_id, order_number, status) VALUES (73133, ?, 'ORD-DEL-73', 'READY_FOR_DELIVERY')",
                customerId);
        jdbcTemplate.update(
                """
                INSERT INTO deliveries
                    (id, delivery_number, order_id, scheduled_at, delivery_address, status, active_order_lock_id)
                VALUES (73134, 'DEL-TGMS73', 73133, CURRENT_TIMESTAMP, 'Colombo', 'SCHEDULED', 73133)
                """);

        deliveryService.updateStatus(73134, "OUT_FOR_DELIVERY", salesId);

        awaitCount(
                "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = 73131 AND kind = 'DELIVERY_STATUS'",
                1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM deliveries WHERE id = 73134", String.class)).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_module FROM notifications WHERE recipient_user_id = ?",
                String.class, customerId)).isEqualTo("DELIVERY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_record_id FROM notifications WHERE recipient_user_id = ?",
                Long.class, customerId)).isEqualTo(73134L);
    }

    private long insertUser(long id, String email, UserRole role) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, role) VALUES (?, ?, ?, ?, ?)",
                id,
                email,
                "$2a$10$01234567890123456789012345678901234567890123456789012",
                "TGMS 73 Event User " + id,
                role.name());
        return id;
    }

    private long insertInventoryMaterial() {
        jdbcTemplate.update(
                """
                INSERT INTO inventory_materials
                    (id, material_code, material_name, material_description, material_type,
                     unit_of_measure, current_quantity, low_stock_threshold, status)
                VALUES (73130, 'INV-TGMS73', 'Cotton test fabric', 'Notification transition test',
                        'FABRIC', 'metre', 6.000, 5.000, 'ACTIVE')
                """);
        return 73130;
    }

    private void awaitCount(String sql, int expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(3));
        while (Instant.now().isBefore(deadline)) {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            if (count != null && count == expected) {
                return;
            }
            Thread.sleep(25);
        }
        assertThat(jdbcTemplate.queryForObject(sql, Integer.class)).isEqualTo(expected);
    }
}

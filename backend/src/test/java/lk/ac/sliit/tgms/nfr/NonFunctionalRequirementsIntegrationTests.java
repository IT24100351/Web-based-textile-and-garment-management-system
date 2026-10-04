package lk.ac.sliit.tgms.nfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/**
 * TGMS-82 practical non-functional verification.
 *
 * <p>Latency observations deliberately have no arbitrary pass/fail SLA. They verify successful real
 * application requests and record the observed development/test timings under {@code target/}.
 * Reliability assertions are strict: concurrent stock consumption must not oversell, and a failed
 * request followed by a valid retry must preserve accurate persisted data.
 */
@SpringBootTest
class NonFunctionalRequirementsIntegrationTests {

    private static final long ADMIN_ID = 98201L;
    private static final long INVENTORY_MANAGER_ID = 98202L;
    private static final long SALES_OFFICER_ID = 98203L;
    private static final long CUSTOMER_ID = 98204L;
    private static final long SECOND_INVENTORY_MANAGER_ID = 98205L;
    private static final int WARM_UP_REQUESTS = 2;
    private static final int MEASURED_REQUESTS = 12;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        cleanScenarioData();
        insertUser(ADMIN_ID, "tgms82-admin@example.com", "TGMS 82 Administrator", UserRole.ADMINISTRATOR);
        insertUser(
                INVENTORY_MANAGER_ID,
                "tgms82-inventory@example.com",
                "TGMS 82 Inventory Manager",
                UserRole.INVENTORY_MANAGER);
        insertUser(
                SALES_OFFICER_ID,
                "tgms82-sales@example.com",
                "TGMS 82 Sales Officer",
                UserRole.SALES_OFFICER);
        insertUser(CUSTOMER_ID, "tgms82-customer@example.com", "TGMS 82 Customer", UserRole.CUSTOMER);
        insertUser(
                SECOND_INVENTORY_MANAGER_ID,
                "tgms82-inventory-2@example.com",
                "TGMS 82 Inventory Manager Two",
                UserRole.INVENTORY_MANAGER);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        createReachablePerformanceFixtures();
    }

    @AfterEach
    void tearDown() {
        cleanScenarioData();
    }

    @Test
    void recordsObservedLatencyForKeyReadOperationsWithoutInventingAnSla() throws Exception {
        Cookie administrator = sessionFor(ADMIN_ID, UserRole.ADMINISTRATOR);
        Cookie inventoryManager = sessionFor(INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        Cookie customer = sessionFor(CUSTOMER_ID, UserRole.CUSTOMER);

        List<LatencyObservation> observations = List.of(
                measure("public-product-catalog", () -> responseStatus(get("/api/products"))),
                measure(
                        "customer-order-history",
                        () -> responseStatus(get("/api/orders/mine").cookie(customer))),
                measure(
                        "inventory-list",
                        () -> responseStatus(get("/api/inventory-materials").cookie(inventoryManager))),
                measure(
                        "administrator-dashboard-report",
                        () -> responseStatus(get("/api/reports/dashboard").cookie(administrator))),
                measure(
                        "role-aware-shared-search",
                        () -> responseStatus(get("/api/search")
                                .queryParam("search", "TGMS82")
                                .queryParam("page", "0")
                                .queryParam("size", "10")
                                .cookie(inventoryManager))));

        assertThat(observations).allSatisfy(observation -> {
            assertThat(observation.samples()).hasSize(MEASURED_REQUESTS);
            assertThat(observation.minMs()).isGreaterThanOrEqualTo(0.0);
            assertThat(observation.p50Ms()).isGreaterThanOrEqualTo(observation.minMs());
            assertThat(observation.p95Ms()).isGreaterThanOrEqualTo(observation.p50Ms());
            assertThat(observation.maxMs()).isGreaterThanOrEqualTo(observation.p95Ms());
        });

        ObservationReport report = new ObservationReport(
                Instant.now(),
                System.getProperty("java.version"),
                "Spring MockMvc + test datasource (development/test observation only)",
                WARM_UP_REQUESTS,
                MEASURED_REQUESTS,
                observations);
        Path reportPath = Path.of("target", "tgms82-latency-observations.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, toJson(report), StandardCharsets.UTF_8);

        observations.forEach(observation -> System.out.printf(
                Locale.ROOT,
                "TGMS82_LATENCY operation=%s samples=%d minMs=%.3f p50Ms=%.3f p95Ms=%.3f maxMs=%.3f%n",
                observation.operation(),
                observation.samples().size(),
                observation.minMs(),
                observation.p50Ms(),
                observation.p95Ms(),
                observation.maxMs()));
    }

    @Test
    void concurrentInventoryConsumptionNeverOversellsAvailableStock() throws Exception {
        Cookie inventoryManager = sessionFor(INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        Cookie secondInventoryManager =
                sessionFor(SECOND_INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        long materialId = createInventoryMaterial(
                inventoryManager,
                "TGMS82-CONCURRENT",
                "Concurrent stock verification",
                "10.000",
                "2.000");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            List<Cookie> concurrentUsers = List.of(inventoryManager, secondInventoryManager);
            for (Cookie concurrentUser : concurrentUsers) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return responseStatus(post("/api/inventory-materials/{materialId}/consume", materialId)
                            .cookie(concurrentUser)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"quantity\":\"7.000\"}"));
                }));
            }

            ready.await();
            long startedNanos = System.nanoTime();
            start.countDown();
            List<Integer> statuses = List.of(futures.get(0).get(), futures.get(1).get());
            double elapsedMs = nanosToMillis(System.nanoTime() - startedNanos);

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            BigDecimal remaining = currentQuantity(materialId);
            assertThat(remaining).isEqualByComparingTo("3.000");
            assertThat(remaining.signum()).isGreaterThanOrEqualTo(0);

            writeTextObservation(
                    "target/tgms82-concurrency-observation.txt",
                    "statuses=" + statuses + System.lineSeparator()
                            + "remainingQuantity=" + remaining.toPlainString() + System.lineSeparator()
                            + "elapsedMs=" + String.format(Locale.ROOT, "%.3f", elapsedMs)
                            + System.lineSeparator()
                            + "invariant=one-success-one-conflict-no-negative-stock"
                            + System.lineSeparator());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedStockRequestCanBeRetriedWithoutCorruptingQuantity() throws Exception {
        Cookie inventoryManager = sessionFor(INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        long materialId = createInventoryMaterial(
                inventoryManager,
                "TGMS82-RETRY",
                "Failure and retry verification",
                "10.000",
                "2.000");

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(inventoryManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":\"20.000\"}"))
                .andExpect(status().isConflict());
        assertThat(currentQuantity(materialId)).isEqualByComparingTo("10.000");

        mockMvc.perform(post("/api/inventory-materials/{materialId}/consume", materialId)
                        .cookie(inventoryManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":\"3.000\"}"))
                .andExpect(status().isOk());
        BigDecimal remaining = currentQuantity(materialId);
        assertThat(remaining).isEqualByComparingTo("7.000");

        writeTextObservation(
                "target/tgms82-retry-observation.txt",
                "failedRequestStatus=409" + System.lineSeparator()
                        + "quantityAfterFailure=10.000" + System.lineSeparator()
                        + "retryStatus=200" + System.lineSeparator()
                        + "quantityAfterRetry=" + remaining.toPlainString() + System.lineSeparator()
                        + "invariant=failed-request-does-not-mutate-valid-retry-applies-once"
                        + System.lineSeparator());
    }

    private void createReachablePerformanceFixtures() throws Exception {
        Cookie salesOfficer = sessionFor(SALES_OFFICER_ID, UserRole.SALES_OFFICER);
        Cookie inventoryManager = sessionFor(INVENTORY_MANAGER_ID, UserRole.INVENTORY_MANAGER);
        Cookie customer = sessionFor(CUSTOMER_ID, UserRole.CUSTOMER);

        mockMvc.perform(post("/api/products")
                        .cookie(salesOfficer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "TGMS82 Timing Shirt",
                                  "category": "TGMS82 Timing Garments",
                                  "size": "M",
                                  "color": "Slate",
                                  "price": 2750.00,
                                  "availability": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isCreated());

        long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_products WHERE name = ?",
                Long.class,
                "TGMS82 Timing Shirt");
        long variantId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_product_variants WHERE product_id = ?",
                Long.class,
                productId);
        mockMvc.perform(post("/api/orders/mine")
                        .cookie(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":%d,"variantId":%d,"quantity":1}]}
                                """.formatted(productId, variantId)))
                .andExpect(status().isCreated());

        createInventoryMaterial(
                inventoryManager,
                "TGMS82-TIMING",
                "TGMS82 timing fabric",
                "50.000",
                "8.000");
    }

    private long createInventoryMaterial(
            Cookie inventoryManager,
            String code,
            String name,
            String currentQuantity,
            String threshold) throws Exception {
        mockMvc.perform(post("/api/inventory-materials")
                        .cookie(inventoryManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "materialCode": "%s",
                                  "materialName": "%s",
                                  "materialDescription": "TGMS-82 measurable test fixture",
                                  "materialType": "FABRIC",
                                  "unitOfMeasure": "metre",
                                  "currentQuantity": %s,
                                  "lowStockThreshold": %s
                                }
                                """.formatted(code, name, currentQuantity, threshold)))
                .andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_materials WHERE material_code = ?",
                Long.class,
                code);
    }

    private LatencyObservation measure(String operation, ThrowingStatusRequest request) throws Exception {
        for (int index = 0; index < WARM_UP_REQUESTS; index++) {
            assertThat(request.execute()).isBetween(200, 299);
        }

        List<Double> samples = new ArrayList<>();
        for (int index = 0; index < MEASURED_REQUESTS; index++) {
            long startedNanos = System.nanoTime();
            int responseStatus = request.execute();
            double elapsedMs = nanosToMillis(System.nanoTime() - startedNanos);
            assertThat(responseStatus).isBetween(200, 299);
            samples.add(elapsedMs);
        }

        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return new LatencyObservation(
                operation,
                List.copyOf(samples),
                sorted.get(0),
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                sorted.get(sorted.size() - 1));
    }

    private int responseStatus(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private double percentile(List<Double> sortedSamples, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sortedSamples.size()) - 1);
        return sortedSamples.get(index);
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private BigDecimal currentQuantity(long materialId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_quantity FROM inventory_materials WHERE id = ?",
                BigDecimal.class,
                materialId);
    }


    private String toJson(ObservationReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"recordedAt\": \"").append(report.recordedAt()).append("\",\n")
                .append("  \"javaVersion\": \"").append(report.javaVersion()).append("\",\n")
                .append("  \"environment\": \"").append(report.environment()).append("\",\n")
                .append("  \"warmUpRequests\": ").append(report.warmUpRequests()).append(",\n")
                .append("  \"measuredRequestsPerOperation\": ")
                .append(report.measuredRequestsPerOperation()).append(",\n")
                .append("  \"observations\": [\n");
        for (int observationIndex = 0; observationIndex < report.observations().size(); observationIndex++) {
            LatencyObservation observation = report.observations().get(observationIndex);
            json.append("    {\n")
                    .append("      \"operation\": \"").append(observation.operation()).append("\",\n")
                    .append("      \"samplesMs\": [");
            for (int sampleIndex = 0; sampleIndex < observation.samples().size(); sampleIndex++) {
                if (sampleIndex > 0) {
                    json.append(", ");
                }
                json.append(String.format(Locale.ROOT, "%.3f", observation.samples().get(sampleIndex)));
            }
            json.append("],\n")
                    .append("      \"minMs\": ").append(String.format(Locale.ROOT, "%.3f", observation.minMs())).append(",\n")
                    .append("      \"p50Ms\": ").append(String.format(Locale.ROOT, "%.3f", observation.p50Ms())).append(",\n")
                    .append("      \"p95Ms\": ").append(String.format(Locale.ROOT, "%.3f", observation.p95Ms())).append(",\n")
                    .append("      \"maxMs\": ").append(String.format(Locale.ROOT, "%.3f", observation.maxMs())).append("\n")
                    .append("    }");
            if (observationIndex + 1 < report.observations().size()) {
                json.append(',');
            }
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private void writeTextObservation(String filename, String content) throws Exception {
        Path path = Path.of(filename);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
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
                "tgms82-session-" + userId + "@example.com",
                "not-used",
                "TGMS 82 Session",
                role,
                true);
        Cookie cookie = new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
        cookie.setPath("/");
        return cookie;
    }

    private void cleanScenarioData() {
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
        jdbcTemplate.update("DELETE FROM users WHERE id BETWEEN 98201 AND 98205");
    }

    @FunctionalInterface
    private interface ThrowingStatusRequest {
        int execute() throws Exception;
    }

    private record LatencyObservation(
            String operation,
            List<Double> samples,
            double minMs,
            double p50Ms,
            double p95Ms,
            double maxMs) {}

    private record ObservationReport(
            Instant recordedAt,
            String javaVersion,
            String environment,
            int warmUpRequests,
            int measuredRequestsPerOperation,
            List<LatencyObservation> observations) {}
}

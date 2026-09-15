package lk.ac.sliit.tgms.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.AuthTokenService;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.hamcrest.Matchers;
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
class ProductApiIntegrationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM garment_product_variants");
        jdbcTemplate.update("DELETE FROM garment_products");
        jdbcTemplate.update("DELETE FROM garment_categories");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void salesOfficerCanCreateAndRetrieveProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Classic   Crew Neck  ",
                                  "category": "  T-Shirts  ",
                                  "imageUrl": " /products/demo/colombo-classic-polo.jpg ",
                                  "size": " M ",
                                  "color": " Navy Blue ",
                                  "price": "2499.90",
                                  "availability": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION, Matchers.matchesPattern("/api/products/[1-9][0-9]*")))
                .andExpect(jsonPath("$.message").value("Garment product created successfully."))
                .andExpect(jsonPath("$.product.name").value("Classic Crew Neck"))
                .andExpect(jsonPath("$.product.category.name").value("T-Shirts"))
                .andExpect(jsonPath("$.product.imageUrl")
                        .value("/products/demo/colombo-classic-polo.jpg"))
                .andExpect(jsonPath("$.product.status").value("ACTIVE"))
                .andExpect(jsonPath("$.product.variants[0].size").value("M"))
                .andExpect(jsonPath("$.product.variants[0].color").value("Navy Blue"))
                .andExpect(jsonPath("$.product.variants[0].price").value("2499.90"))
                .andExpect(jsonPath("$.product.variants[0].status").value("AVAILABLE"));

        long productId = jdbcTemplate.queryForObject(
                "SELECT id FROM garment_products WHERE name = ?",
                Long.class,
                "Classic Crew Neck");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_categories WHERE name = ?",
                        Integer.class,
                        "T-Shirts"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT price FROM garment_product_variants WHERE product_id = ?",
                        BigDecimal.class,
                        productId))
                .isEqualByComparingTo("2499.90");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT image_url FROM garment_products WHERE id = ?",
                        String.class,
                        productId))
                .isEqualTo("/products/demo/colombo-classic-polo.jpg");

        mockMvc.perform(get("/api/products/{productId}", productId)
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId))
                .andExpect(jsonPath("$.categoryId").isNumber())
                .andExpect(jsonPath("$.name").value("Classic Crew Neck"))
                .andExpect(jsonPath("$.imageUrl")
                        .value("/products/demo/colombo-classic-polo.jpg"))
                .andExpect(jsonPath("$.variants[0].price").value("2499.90"));
    }

    @Test
    void salesOfficerCanCreateMultipleIndependentlyPricedVariantsAtomically() throws Exception {
        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Oxford Shirt",
                                  "category": "Formal Wear",
                                  "description": "Easy-care cotton shirt",
                                  "imageUrl": "/api/product-images/123e4567-e89b-12d3-a456-426614174000.jpg",
                                  "variants": [
                                    {
                                      "size": "M",
                                      "color": "White",
                                      "price": "3990.00",
                                      "availability": "AVAILABLE"
                                    },
                                    {
                                      "size": "XL",
                                      "color": "Navy",
                                      "price": "4490.00",
                                      "availability": "UNAVAILABLE"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.description").value("Easy-care cotton shirt"))
                .andExpect(jsonPath("$.product.variants.length()").value(2))
                .andExpect(jsonPath("$.product.variants[0].size").value("M"))
                .andExpect(jsonPath("$.product.variants[0].price").value("3990.00"))
                .andExpect(jsonPath("$.product.variants[1].size").value("XL"))
                .andExpect(jsonPath("$.product.variants[1].color").value("Navy"))
                .andExpect(jsonPath("$.product.variants[1].price").value("4490.00"))
                .andExpect(jsonPath("$.product.variants[1].status").value("UNAVAILABLE"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_product_variants", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void duplicateSizeAndColourCombinationRollsBackTheWholeProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicate Shirt",
                                  "category": "Formal Wear",
                                  "variants": [
                                    {"size":"M","color":"Navy","price":"3990.00","availability":"AVAILABLE"},
                                    {"size":"m","color":"navy","price":"4290.00","availability":"AVAILABLE"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields['variants[1].color']").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_products", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_categories", Integer.class))
                .isZero();
    }

    @Test
    void existingActiveCategoryIsReused() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO garment_categories (name, status) VALUES (?, ?)",
                "Formal Wear",
                CategoryStatus.ACTIVE.name());

        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProduct("formal wear")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.category.name").value("Formal Wear"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_categories", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void invalidAndIncompleteValuesAreRejectedBeforePersistence() throws Exception {
        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "category": "",
                                  "size": "",
                                  "color": "",
                                  "price": "0.00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.category").exists())
                .andExpect(jsonPath("$.error.fields.size").exists())
                .andExpect(jsonPath("$.error.fields.color").exists())
                .andExpect(jsonPath("$.error.fields.price").exists())
                .andExpect(jsonPath("$.error.fields.availability").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_products", Integer.class))
                .isZero();
    }

    @Test
    void inactiveCategoryCannotReceiveNewProduct() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO garment_categories (name, status) VALUES (?, ?)",
                "Archived Range",
                CategoryStatus.INACTIVE.name());

        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProduct("Archived Range")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_INACTIVE"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_products", Integer.class))
                .isZero();
    }

    @Test
    void productActionsRequireSalesOfficerRole() throws Exception {
        mockMvc.perform(post("/api/products")
                        .cookie(sessionFor(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProduct("Casual Wear")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProduct("Casual Wear")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/products/1").cookie(sessionFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/api/products/1")
                        .cookie(sessionFor(UserRole.CUSTOMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductUpdate(1, "Casual Wear")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductUpdate(1, "Casual Wear")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(delete("/api/products/1")
                        .cookie(sessionFor(UserRole.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void publicCatalogShowsOnlyActiveProductsAndAvailableVariants() throws Exception {
        insertCategory(1101, "Everyday Wear", CategoryStatus.ACTIVE);
        insertCategory(1102, "Archived Wear", CategoryStatus.INACTIVE);

        insertProduct(2101, 1101, "Classic Tee", ProductStatus.ACTIVE);
        insertProduct(2102, 1101, "Hidden Tee", ProductStatus.INACTIVE);
        insertProduct(2103, 1101, "Old Tee", ProductStatus.DISCONTINUED);
        insertProduct(2104, 1102, "Archived Category Tee", ProductStatus.ACTIVE);
        insertProduct(2105, 1101, "Out of Stock Tee", ProductStatus.ACTIVE);

        insertVariant(3101, 2101, "M", "Navy", "2499.90", VariantStatus.AVAILABLE);
        insertVariant(3102, 2101, "L", "Navy", "2599.90", VariantStatus.AVAILABLE);
        insertVariant(3103, 2101, "XL", "Navy", "2699.90", VariantStatus.UNAVAILABLE);
        insertVariant(3104, 2102, "M", "Black", "1999.00", VariantStatus.AVAILABLE);
        insertVariant(3105, 2103, "M", "Grey", "1899.00", VariantStatus.AVAILABLE);
        insertVariant(3106, 2104, "M", "White", "1799.00", VariantStatus.AVAILABLE);
        insertVariant(3107, 2105, "M", "Green", "1699.00", VariantStatus.DISCONTINUED);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2101))
                .andExpect(jsonPath("$[0].name").value("Classic Tee"))
                .andExpect(jsonPath("$[0].category.name").value("Everyday Wear"))
                .andExpect(jsonPath("$[0].variants.length()").value(2))
                .andExpect(jsonPath("$[0].variants[0].id").value(3101))
                .andExpect(jsonPath("$[0].variants[0].price").value("2499.90"))
                .andExpect(jsonPath("$[0].variants[1].id").value(3102));
    }

    @Test
    void registeredCustomerAndInternalRolesCanReadPublicCatalog() throws Exception {
        mockMvc.perform(get("/api/products").cookie(sessionFor(UserRole.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        for (UserRole role : new UserRole[] {
            UserRole.ADMINISTRATOR,
            UserRole.SUPPLIER,
            UserRole.INVENTORY_MANAGER,
            UserRole.PRODUCTION_MANAGER,
            UserRole.SALES_OFFICER
        }) {
            mockMvc.perform(get("/api/products").cookie(sessionFor(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Test
    void catalogSearchCombinedFiltersAndClearRequestArePredictable() throws Exception {
        insertCategory(1201, "Casual Wear", CategoryStatus.ACTIVE);
        insertCategory(1202, "Formal Wear", CategoryStatus.ACTIVE);
        insertProduct(2201, 1201, "Classic Tee", ProductStatus.ACTIVE);
        insertProduct(2202, 1202, "Classic Shirt", ProductStatus.ACTIVE);
        insertProduct(2203, 1202, "Oxford Shirt", ProductStatus.ACTIVE);
        insertVariant(3201, 2201, "M", "Navy", "2499.90", VariantStatus.AVAILABLE);
        insertVariant(3202, 2201, "L", "White", "2599.90", VariantStatus.AVAILABLE);
        insertVariant(3203, 2201, "XL", "Red", "2699.90", VariantStatus.UNAVAILABLE);
        insertVariant(3204, 2202, "M", "Navy", "3499.90", VariantStatus.AVAILABLE);
        insertVariant(3205, 2203, "L", "White", "3990.00", VariantStatus.AVAILABLE);

        mockMvc.perform(get("/api/products").param("search", "cLaSsIc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name")
                        .value(Matchers.containsInAnyOrder("Classic Tee", "Classic Shirt")));

        mockMvc.perform(get("/api/products")
                        .param("search", " classic ")
                        .param("category", " casual wear ")
                        .param("size", "m")
                        .param("color", "NAVY")
                        .param("availability", "available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2201))
                .andExpect(jsonPath("$[0].variants.length()").value(1))
                .andExpect(jsonPath("$[0].variants[0].id").value(3201));

        mockMvc.perform(get("/api/products").param("availability", "UNAVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void catalogFiltersValidateControlledAndMaximumLengthValues() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("availability", "BACKORDERED")
                        .param("search", "x".repeat(161)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.availability").exists())
                .andExpect(jsonPath("$.error.fields.search").exists());
    }

    @Test
    void guestCanLoadPublicProductDetailByStableId() throws Exception {
        insertCategory(1301, "Knitwear", CategoryStatus.ACTIVE);
        insertProduct(2301, 1301, "Ribbed Cardigan", ProductStatus.ACTIVE);
        insertVariant(3301, 2301, "M", "Forest Green", "5490.00", VariantStatus.AVAILABLE);
        insertVariant(3302, 2301, "L", "Forest Green", "5590.00", VariantStatus.UNAVAILABLE);
        jdbcTemplate.update(
                "UPDATE garment_categories SET description = ? WHERE id = ?",
                "Layering garments",
                1301);
        jdbcTemplate.update(
                "UPDATE garment_products SET description = ? WHERE id = ?",
                "Soft ribbed cardigan",
                2301);

        mockMvc.perform(get("/api/products/catalog/2301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2301))
                .andExpect(jsonPath("$.name").value("Ribbed Cardigan"))
                .andExpect(jsonPath("$.description").value("Soft ribbed cardigan"))
                .andExpect(jsonPath("$.category.id").value(1301))
                .andExpect(jsonPath("$.category.name").value("Knitwear"))
                .andExpect(jsonPath("$.category.description").value("Layering garments"))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].id").value(3301))
                .andExpect(jsonPath("$.variants[0].size").value("M"))
                .andExpect(jsonPath("$.variants[0].color").value("Forest Green"))
                .andExpect(jsonPath("$.variants[0].price").value("5490.00"))
                .andExpect(jsonPath("$.variants[0].status").value("AVAILABLE"));
    }

    @Test
    void publicProductDetailHandlesInvalidMissingAndNonPublicIdsSafely() throws Exception {
        insertCategory(1302, "Archived", CategoryStatus.ACTIVE);
        insertProduct(2302, 1302, "Inactive Jacket", ProductStatus.INACTIVE);
        insertProduct(2303, 1302, "Unavailable Jacket", ProductStatus.ACTIVE);
        insertVariant(3303, 2302, "M", "Black", "6490.00", VariantStatus.AVAILABLE);
        insertVariant(3304, 2303, "M", "Black", "6490.00", VariantStatus.UNAVAILABLE);

        mockMvc.perform(get("/api/products/catalog/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/products/catalog/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        for (long hiddenOrMissingId : new long[] {2302, 2303, 999999}) {
            mockMvc.perform(get("/api/products/catalog/{productId}", hiddenOrMissingId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        }
    }

    @Test
    void retrievalValidatesIdAndReturnsSafeNotFoundError() throws Exception {
        Cookie session = sessionFor(UserRole.SALES_OFFICER);

        mockMvc.perform(get("/api/products/0").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.productId").exists());

        mockMvc.perform(get("/api/products/not-a-number").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.productId").exists());

        mockMvc.perform(get("/api/products/999999").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void salesOfficerCanUpdateSelectedVariantWithoutChangingStableIds() throws Exception {
        insertCategory(1401, "Casual Wear", CategoryStatus.ACTIVE);
        insertCategory(1402, "Formal Wear", CategoryStatus.ACTIVE);
        insertProduct(2401, 1401, "Original Shirt", ProductStatus.ACTIVE);
        insertVariant(3401, 2401, "M", "Blue", "2990.00", VariantStatus.AVAILABLE);
        insertVariant(3402, 2401, "L", "Black", "3190.00", VariantStatus.UNAVAILABLE);

        mockMvc.perform(put("/api/products/2401")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductUpdate(3401, " formal wear ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Garment product updated successfully."))
                .andExpect(jsonPath("$.product.id").value(2401))
                .andExpect(jsonPath("$.product.category.id").value(1402))
                .andExpect(jsonPath("$.product.name").value("Updated Oxford Shirt"))
                .andExpect(jsonPath("$.product.variants[0].id").value(3401))
                .andExpect(jsonPath("$.product.variants[0].productId").value(2401))
                .andExpect(jsonPath("$.product.variants[0].size").value("XL"))
                .andExpect(jsonPath("$.product.variants[0].color").value("Ivory"))
                .andExpect(jsonPath("$.product.variants[0].price").value("4290.50"))
                .andExpect(jsonPath("$.product.variants[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.product.variants[1].id").value(3402))
                .andExpect(jsonPath("$.product.variants[1].size").value("L"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT id FROM garment_products WHERE name = ?",
                        Long.class,
                        "Updated Oxford Shirt"))
                .isEqualTo(2401L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT product_id FROM garment_product_variants WHERE id = ?",
                        Long.class,
                        3401))
                .isEqualTo(2401L);

        mockMvc.perform(get("/api/products/2401")
                        .cookie(sessionFor(UserRole.SALES_OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2401))
                .andExpect(jsonPath("$.variants[0].id").value(3401))
                .andExpect(jsonPath("$.variants[0].price").value("4290.50"));
    }

    @Test
    void invalidUpdateIsRejectedWithoutChangingStoredProduct() throws Exception {
        insertCategory(1403, "Outerwear", CategoryStatus.ACTIVE);
        insertProduct(2402, 1403, "Canvas Jacket", ProductStatus.ACTIVE);
        insertVariant(3403, 2402, "M", "Khaki", "6990.00", VariantStatus.AVAILABLE);

        mockMvc.perform(put("/api/products/2402")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variantId": 3403,
                                  "name": " ",
                                  "category": "Outerwear",
                                  "size": "M",
                                  "color": "Khaki",
                                  "price": "0.00",
                                  "availability": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.price").exists());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM garment_products WHERE id = ?", String.class, 2402))
                .isEqualTo("Canvas Jacket");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT price FROM garment_product_variants WHERE id = ?",
                        BigDecimal.class,
                        3403))
                .isEqualByComparingTo("6990.00");
    }

    @Test
    void updateRejectsVariantOwnedByAnotherProduct() throws Exception {
        insertCategory(1404, "Sportswear", CategoryStatus.ACTIVE);
        insertProduct(2403, 1404, "Training Tee", ProductStatus.ACTIVE);
        insertProduct(2404, 1404, "Running Tee", ProductStatus.ACTIVE);
        insertVariant(3404, 2403, "M", "Red", "2490.00", VariantStatus.AVAILABLE);
        insertVariant(3405, 2404, "M", "Blue", "2590.00", VariantStatus.AVAILABLE);

        mockMvc.perform(put("/api/products/2403")
                        .cookie(sessionFor(UserRole.SALES_OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validProductUpdate(3405, "Sportswear")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.variantId")
                        .value("The selected variant does not belong to this product."));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM garment_products WHERE id = ?", String.class, 2403))
                .isEqualTo("Training Tee");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT product_id FROM garment_product_variants WHERE id = ?",
                        Long.class,
                        3405))
                .isEqualTo(2404L);
    }

    @Test
    void discontinuationPreservesHistoryAndBlocksNewOrderSelection() throws Exception {
        insertCategory(1501, "Workwear", CategoryStatus.ACTIVE);
        insertProduct(2501, 1501, "Utility Shirt", ProductStatus.ACTIVE);
        insertVariant(3501, 2501, "M", "Stone", "4590.00", VariantStatus.AVAILABLE);
        insertVariant(3502, 2501, "L", "Stone", "4690.00", VariantStatus.UNAVAILABLE);

        OrderProductSelection selection = productService.requireOrderSelectableVariant(2501, 3501);
        assertThat(selection.productId()).isEqualTo(2501);
        assertThat(selection.variantId()).isEqualTo(3501);
        assertThat(selection.productName()).isEqualTo("Utility Shirt");
        assertThat(selection.categoryId()).isEqualTo(1501);
        assertThat(selection.categoryName()).isEqualTo("Workwear");
        assertThat(selection.size()).isEqualTo("M");
        assertThat(selection.color()).isEqualTo("Stone");
        assertThat(selection.currentPrice()).isEqualByComparingTo("4590.00");
        assertThat(selection.availability()).isEqualTo(VariantStatus.AVAILABLE);

        Cookie salesOfficer = sessionFor(UserRole.SALES_OFFICER);
        mockMvc.perform(delete("/api/products/2501").cookie(salesOfficer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Garment product discontinued successfully."))
                .andExpect(jsonPath("$.product.id").value(2501))
                .andExpect(jsonPath("$.product.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.product.variants.length()").value(2))
                .andExpect(jsonPath("$.product.variants[*].status")
                        .value(Matchers.everyItem(Matchers.is("DISCONTINUED"))));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_products WHERE id = ?",
                        Integer.class,
                        2501))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM garment_product_variants WHERE product_id = ?",
                        Integer.class,
                        2501))
                .isEqualTo(2);
        assertThatThrownBy(() -> productService.requireOrderSelectableVariant(2501, 3501))
                .isInstanceOf(ProductNotSelectableException.class)
                .hasMessage("The selected product variant is not available for a new order.");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/products/catalog/2501"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get("/api/products/2501").cookie(salesOfficer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));

        mockMvc.perform(delete("/api/products/2501").cookie(salesOfficer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.id").value(2501))
                .andExpect(jsonPath("$.product.status").value("DISCONTINUED"));
    }

    @Test
    void discontinuationValidatesProductIdAndMissingProduct() throws Exception {
        Cookie salesOfficer = sessionFor(UserRole.SALES_OFFICER);

        mockMvc.perform(delete("/api/products/0").cookie(salesOfficer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.productId").exists());
        mockMvc.perform(delete("/api/products/not-a-number").cookie(salesOfficer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(delete("/api/products/999999").cookie(salesOfficer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }

    private Cookie sessionFor(UserRole role) {
        UserAccount account = new UserAccount(
                200L, "product@example.com", "not-used", "Product Test User", role, true);
        return new Cookie(AuthCookieService.COOKIE_NAME, authTokenService.issue(account));
    }

    private void insertCategory(long id, String name, CategoryStatus status) {
        jdbcTemplate.update(
                "INSERT INTO garment_categories (id, name, status) VALUES (?, ?, ?)",
                id,
                name,
                status.name());
    }

    private void insertProduct(long id, long categoryId, String name, ProductStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO garment_products (id, category_id, name, status)
                VALUES (?, ?, ?, ?)
                """,
                id,
                categoryId,
                name,
                status.name());
    }

    private void insertVariant(
            long id,
            long productId,
            String size,
            String color,
            String price,
            VariantStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO garment_product_variants
                    (id, product_id, size, color, price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                productId,
                size,
                color,
                new BigDecimal(price),
                status.name());
    }

    private String validProduct(String category) {
        return """
                {
                  "name": "Oxford Shirt",
                  "category": "%s",
                  "size": "L",
                  "color": "White",
                  "price": "3990.00",
                  "availability": "UNAVAILABLE"
                }
                """.formatted(category);
    }

    private String validProductUpdate(long variantId, String category) {
        return """
                {
                  "variantId": %d,
                  "name": "  Updated   Oxford Shirt  ",
                  "category": "%s",
                  "imageUrl": "/products/demo/ceylon-batik-resort-shirt.jpg",
                  "size": " XL ",
                  "color": " Ivory ",
                  "price": "4290.50",
                  "availability": "AVAILABLE"
                }
                """.formatted(variantId, category);
    }
}

package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Defines all database operations required by the product module.
 *
 * <p>The service layer uses this repository to manage garment categories,
 * products, product variants, and public catalog lookups without depending on a
 * specific persistence implementation.
 */
public interface ProductRepository {

    /**
     * Finds a product category by name using a case-insensitive lookup.
     *
     * @param categoryName category name entered by the user
     * @return the matching category, or an empty result when no category exists
     */
    Optional<ProductCategory> findCategoryByName(String categoryName);

    /**
     * Creates a new garment category.
     *
     * @param categoryName name of the category to create
     * @return generated database ID of the new category
     */
    long createCategory(String categoryName);

    /**
     * Creates a new active garment product under an existing category.
     *
     * @param categoryId database ID of the category that owns the product
     * @param productName display name of the product
     * @param description detailed product description
     * @param imageUrl stored image URL or path for the product
     * @return generated database ID of the new product
     */
    long createProduct(
            long categoryId, String productName, String description, String imageUrl);

    /**
     * Creates a size/color/price variant for an existing product.
     *
     * @param productId database ID of the parent product
     * @param size garment size for this variant
     * @param color garment color for this variant
     * @param price selling price for this variant
     * @param status current availability status of this variant
     * @return generated database ID of the new variant
     */
    long createVariant(
            long productId,
            String size,
            String color,
            BigDecimal price,
            VariantStatus status);

    /**
     * Updates the main product details and category assignment.
     *
     * @param productId database ID of the product to update
     * @param categoryId database ID of the category to assign
     * @param productName updated display name
     * @param description updated product description
     * @param imageUrl updated image URL or path
     * @return number of product rows updated
     */
    int updateProduct(
            long productId,
            long categoryId,
            String productName,
            String description,
            String imageUrl);

    /**
     * Updates one variant that belongs to the given product.
     *
     * @param productId database ID of the parent product
     * @param variantId database ID of the variant to update
     * @param size updated garment size
     * @param color updated garment color
     * @param price updated selling price
     * @param status updated availability status
     * @return number of variant rows updated
     */
    int updateVariant(
            long productId,
            long variantId,
            String size,
            String color,
            BigDecimal price,
            VariantStatus status);

    /**
     * Marks an active product as discontinued.
     *
     * @param productId database ID of the product to discontinue
     * @return number of product rows changed
     */
    int discontinueProduct(long productId);

    /**
     * Marks every non-discontinued variant of a product as discontinued.
     *
     * @param productId database ID of the product whose variants should be discontinued
     * @return number of variant rows changed
     */
    int discontinueVariants(long productId);

    /**
     * Loads a product with its category and all variants, including records that
     * are not visible in the public catalog.
     *
     * @param productId database ID of the product to load
     * @return full product details, or an empty result when the product is missing
     */
    Optional<GarmentProductDetails> findProductById(long productId);

    /**
     * Loads one public catalog product by ID.
     *
     * <p>Only active categories, active products, and available variants are
     * returned by this lookup.
     *
     * @param productId database ID of the product to load
     * @return public product details, or an empty result when it is missing or not selectable
     */
    Optional<GarmentProductDetails> findPublicCatalogProductById(long productId);

    /**
     * Finds public catalog products that match the supplied filters.
     *
     * <p>The public catalog contains only active categories, active products, and
     * variants that satisfy the requested availability.
     *
     * @param filter optional search, category, size, color, and availability filters
     * @return immutable-style list of matching public catalog product details
     */
    List<GarmentProductDetails> findPublicCatalog(ProductCatalogFilter filter);

    /**
     * Finds every product currently visible in the public catalog.
     *
     * @return public catalog products with no additional filters applied
     */
    default List<GarmentProductDetails> findPublicCatalog() {
        return findPublicCatalog(ProductCatalogFilter.empty());
    }
}

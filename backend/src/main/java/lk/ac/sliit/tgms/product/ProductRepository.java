package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<ProductCategory> findCategoryByName(String categoryName);

    long createCategory(String categoryName);

    long createProduct(
            long categoryId, String productName, String description, String imageUrl);

    long createVariant(
            long productId,
            String size,
            String color,
            BigDecimal price,
            VariantStatus status);

    int updateProduct(
            long productId,
            long categoryId,
            String productName,
            String description,
            String imageUrl,
            ProductStatus status);

    int updateVariant(
            long productId,
            long variantId,
            String size,
            String color,
            BigDecimal price,
            VariantStatus status);

    boolean isHistoricallyReferenced(long productId);

    int deleteVariants(long productId);

    int deleteProduct(long productId);

    boolean isImageUrlReferenced(String imageUrl);

    boolean isImageUrlReferencedElsewhere(String imageUrl, long productId);

    Optional<GarmentProductDetails> findProductById(long productId);

    Optional<GarmentProductDetails> findPublicCatalogProductById(long productId);

    List<GarmentProductDetails> findPublicCatalog(ProductCatalogFilter filter);

    List<GarmentProductDetails> findManagementCatalog(ProductManagementFilter filter);

    default List<GarmentProductDetails> findPublicCatalog() {
        return findPublicCatalog(ProductCatalogFilter.empty());
    }
}

package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageStorageService productImageStorageService;

    public ProductService(
            ProductRepository productRepository,
            ProductImageStorageService productImageStorageService) {
        this.productRepository = productRepository;
        this.productImageStorageService = productImageStorageService;
    }

    @Transactional
    public GarmentProductDetails createProduct(
            String name,
            String categoryName,
            String imageUrl,
            String size,
            String color,
            BigDecimal price,
            CreateProductAvailability availability) {
        String normalizedName = normalizeText(name);
        String normalizedCategory = normalizeText(categoryName);
        String normalizedImageUrl = normalizeOptionalText(imageUrl);
        String normalizedSize = normalizeText(size);
        String normalizedColor = normalizeText(color);
        validate(
                normalizedName,
                normalizedCategory,
                null,
                normalizedSize,
                normalizedColor,
                normalizedImageUrl,
                price,
                availability);

        ProductCategory category = findOrCreateCategory(normalizedCategory);
        if (category.status() != CategoryStatus.ACTIVE) {
            throw new InactiveProductCategoryException();
        }

        long productId = productRepository.createProduct(
                category.id(), normalizedName, null, normalizedImageUrl);
        productRepository.createVariant(
                productId,
                normalizedSize,
                normalizedColor,
                price,
                availability.toVariantStatus());

        return productRepository.findProductById(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Created garment product could not be read back."));
    }

    @Transactional
    public GarmentProductDetails createProduct(
            String name,
            String categoryName,
            String description,
            String imageUrl,
            List<CreateProductVariantCommand> variants) {
        String normalizedName = normalizeText(name);
        String normalizedCategory = normalizeText(categoryName);
        String normalizedDescription = normalizeOptionalText(description);
        String normalizedImageUrl = normalizeOptionalText(imageUrl);
        List<CreateProductVariantCommand> normalizedVariants = normalizeAndValidateVariants(
                normalizedName,
                normalizedCategory,
                normalizedDescription,
                normalizedImageUrl,
                variants);

        ProductCategory category = findOrCreateCategory(normalizedCategory);
        if (category.status() != CategoryStatus.ACTIVE) {
            throw new InactiveProductCategoryException();
        }

        long productId = productRepository.createProduct(
                category.id(),
                normalizedName,
                normalizedDescription,
                normalizedImageUrl);
        try {
            for (CreateProductVariantCommand variant : normalizedVariants) {
                productRepository.createVariant(
                        productId,
                        variant.size(),
                        variant.color(),
                        variant.price(),
                        variant.availability().toVariantStatus());
            }
        } catch (DuplicateKeyException exception) {
            throw new ProductValidationException(Map.of(
                    "variants", "Each size and colour combination must be unique."));
        }

        return productRepository.findProductById(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Created garment product could not be read back."));
    }

    private void scheduleReplacedImageCleanup(String oldImageUrl, String newImageUrl) {
        if (Objects.equals(oldImageUrl, newImageUrl)
                || oldImageUrl == null
                || productRepository.isImageUrlReferenced(oldImageUrl)) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                productImageStorageService.deleteManagedImage(oldImageUrl);
            }
        });
    }

    @Transactional
    public GarmentProductDetails updateProduct(
            long productId,
            long variantId,
            String name,
            String categoryName,
            String description,
            String imageUrl,
            String size,
            String color,
            BigDecimal price,
            CreateProductAvailability availability,
            ProductStatus status) {
        validateProductId(productId);
        validateVariantId(variantId);

        String normalizedName = normalizeText(name);
        String normalizedCategory = normalizeText(categoryName);
        String normalizedDescription = normalizeOptionalText(description);
        String normalizedImageUrl = normalizeOptionalText(imageUrl);
        String normalizedSize = normalizeText(size);
        String normalizedColor = normalizeText(color);
        validate(
                normalizedName,
                normalizedCategory,
                normalizedDescription,
                normalizedSize,
                normalizedColor,
                normalizedImageUrl,
                price,
                availability);

        GarmentProductDetails existing = productRepository.findProductById(productId)
                .orElseThrow(ProductNotFoundException::new);
        ProductStatus resolvedStatus = status == null ? existing.product().status() : status;
        if (existing.variants().stream().noneMatch(variant -> variant.id() == variantId)) {
            throw new ProductValidationException(Map.of(
                    "variantId", "The selected variant does not belong to this product."));
        }
        ProductCategory category = findOrCreateCategory(normalizedCategory);
        if (category.status() != CategoryStatus.ACTIVE) {
            throw new InactiveProductCategoryException();
        }

        try {
            int updatedProducts = productRepository.updateProduct(
                    productId,
                    category.id(),
                    normalizedName,
                    normalizedDescription,
                    normalizedImageUrl,
                    resolvedStatus);
            int updatedVariants = productRepository.updateVariant(
                    productId,
                    variantId,
                    normalizedSize,
                    normalizedColor,
                    price,
                    availability.toVariantStatus());
            if (updatedProducts != 1 || updatedVariants != 1) {
                throw new IllegalStateException("Garment product update affected an unexpected row count.");
            }
        } catch (DuplicateKeyException exception) {
            throw new ProductValidationException(Map.of(
                    "size", "This product already has a variant with the selected size and color.",
                    "color", "This product already has a variant with the selected size and color."));
        }

        scheduleReplacedImageCleanup(existing.product().imageUrl(), normalizedImageUrl);

        return productRepository.findProductById(productId)
                .orElseThrow(() -> new IllegalStateException(
                        "Updated garment product could not be read back."));
    }

    @Transactional
    public void deleteProduct(long productId) {
        validateProductId(productId);
        GarmentProductDetails existing = productRepository.findProductById(productId)
                .orElseThrow(ProductNotFoundException::new);
        if (productRepository.isHistoricallyReferenced(productId)) {
            throw new ProductInUseException();
        }

        String imageUrl = existing.product().imageUrl();
        boolean imageUsedElsewhere = imageUrl != null
                && productRepository.isImageUrlReferencedElsewhere(imageUrl, productId);
        try {
            int deletedVariants = productRepository.deleteVariants(productId);
            if (deletedVariants != existing.variants().size()) {
                throw new IllegalStateException(
                        "Garment product deletion affected an unexpected variant row count.");
            }
            if (productRepository.deleteProduct(productId) != 1) {
                throw new ProductNotFoundException();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ProductInUseException();
        }

        if (imageUrl != null && !imageUsedElsewhere) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    productImageStorageService.deleteManagedImage(imageUrl);
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public OrderProductSelection requireOrderSelectableVariant(
            long productId, long variantId) {
        validateProductId(productId);
        validateVariantId(variantId);
        GarmentProductDetails details = productRepository.findProductById(productId)
                .orElseThrow(ProductNotSelectableException::new);
        if (details.category().status() != CategoryStatus.ACTIVE
                || details.product().status() != ProductStatus.ACTIVE) {
            throw new ProductNotSelectableException();
        }

        ProductVariant variant = details.variants().stream()
                .filter(candidate -> candidate.id() == variantId)
                .filter(candidate -> candidate.status() == VariantStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(ProductNotSelectableException::new);
        return new OrderProductSelection(
                details.product().id(),
                variant.id(),
                details.product().name(),
                details.product().imageUrl(),
                details.category().id(),
                details.category().name(),
                variant.size(),
                variant.color(),
                variant.price(),
                variant.status());
    }

    @Transactional(readOnly = true)
    public GarmentProductDetails getProduct(long productId) {
        validateProductId(productId);
        return productRepository.findProductById(productId)
                .orElseThrow(ProductNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<GarmentProductDetails> getManagementCatalog(String search, String status) {
        String normalizedSearch = normalizeOptionalText(search);
        Map<String, String> fields = new LinkedHashMap<>();
        validateOptionalText(fields, "search", normalizedSearch, 160, "Search");

        ProductStatus normalizedStatus = null;
        String normalizedStatusText = normalizeOptionalText(status);
        if (normalizedStatusText != null) {
            try {
                normalizedStatus = ProductStatus.valueOf(
                        normalizedStatusText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                fields.put("status", "Product status must be ACTIVE or INACTIVE.");
            }
        }
        if (!fields.isEmpty()) {
            throw new ProductValidationException(fields);
        }
        return productRepository.findManagementCatalog(
                new ProductManagementFilter(normalizedSearch, normalizedStatus));
    }

    @Transactional(readOnly = true)
    public GarmentProductDetails getPublicCatalogProduct(long productId) {
        validateProductId(productId);
        return productRepository.findPublicCatalogProductById(productId)
                .orElseThrow(ProductNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<GarmentProductDetails> getPublicCatalog() {
        return productRepository.findPublicCatalog();
    }

    @Transactional(readOnly = true)
    public List<GarmentProductDetails> getPublicCatalog(
            String search,
            String category,
            String size,
            String color,
            String availability) {
        String normalizedSearch = normalizeOptionalText(search);
        String normalizedCategory = normalizeOptionalText(category);
        String normalizedSize = normalizeOptionalText(size);
        String normalizedColor = normalizeOptionalText(color);
        Map<String, String> fields = new LinkedHashMap<>();

        validateOptionalText(fields, "search", normalizedSearch, 160, "Search");
        validateOptionalText(fields, "category", normalizedCategory, 100, "Category");
        validateOptionalText(fields, "size", normalizedSize, 32, "Size");
        validateOptionalText(fields, "color", normalizedColor, 64, "Color");
        VariantStatus normalizedAvailability = parseAvailability(availability, fields);
        if (normalizedAvailability == VariantStatus.UNAVAILABLE) {
            fields.put("availability",
                    "The public catalogue only contains AVAILABLE variants.");
        }

        if (!fields.isEmpty()) {
            throw new ProductValidationException(fields);
        }

        return productRepository.findPublicCatalog(new ProductCatalogFilter(
                normalizedSearch,
                normalizedCategory,
                normalizedSize,
                normalizedColor,
                normalizedAvailability));
    }

    private ProductCategory findOrCreateCategory(String categoryName) {
        var existing = productRepository.findCategoryByName(categoryName);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            long categoryId = productRepository.createCategory(categoryName);
            return productRepository.findCategoryByName(categoryName)
                    .filter(category -> category.id() == categoryId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Created garment category could not be read back."));
        } catch (DuplicateKeyException exception) {
            return productRepository.findCategoryByName(categoryName).orElseThrow(() -> exception);
        }
    }

    private void validateProductId(long productId) {
        if (productId <= 0) {
            throw new ProductValidationException(
                    Map.of("productId", "Product ID must be a positive number."));
        }
    }

    private void validateVariantId(long variantId) {
        if (variantId <= 0) {
            throw new ProductValidationException(
                    Map.of("variantId", "Variant ID must be a positive number."));
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        String normalized = normalizeText(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private VariantStatus parseAvailability(
            String availability, Map<String, String> fields) {
        String normalized = normalizeOptionalText(availability);
        if (normalized == null) {
            return null;
        }
        try {
            return VariantStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            fields.put(
                    "availability",
                    "Availability must be AVAILABLE or UNAVAILABLE.");
            return null;
        }
    }

    private void validateOptionalText(
            Map<String, String> fields,
            String field,
            String value,
            int maximumLength,
            String label) {
        if (value != null && value.length() > maximumLength) {
            fields.put(field, label + " must not exceed " + maximumLength + " characters.");
        }
    }

    private void validate(
            String name,
            String category,
            String description,
            String size,
            String color,
            String imageUrl,
            BigDecimal price,
            CreateProductAvailability availability) {
        Map<String, String> fields = new LinkedHashMap<>();
        validateText(fields, "name", name, 160, "Product name");
        validateText(fields, "category", category, 100, "Category");
        validateOptionalText(fields, "description", description, 2000, "Description");
        validateText(fields, "size", size, 32, "Size");
        validateText(fields, "color", color, 64, "Color");
        validateImageUrl(fields, imageUrl);

        if (price == null
                || price.compareTo(BigDecimal.ZERO) <= 0
                || price.scale() > 2
                || price.precision() - price.scale() > 10) {
            fields.put("price", "Price must be positive with at most 10 digits and 2 decimals.");
        }
        if (availability == null) {
            fields.put("availability", "Availability is required.");
        }
        if (!fields.isEmpty()) {
            throw new ProductValidationException(fields);
        }
    }

    private List<CreateProductVariantCommand> normalizeAndValidateVariants(
            String name,
            String category,
            String description,
            String imageUrl,
            List<CreateProductVariantCommand> variants) {
        Map<String, String> fields = new LinkedHashMap<>();
        validateText(fields, "name", name, 160, "Product name");
        validateText(fields, "category", category, 100, "Category");
        validateOptionalText(fields, "description", description, 2000, "Description");
        validateImageUrl(fields, imageUrl);

        if (variants == null || variants.isEmpty()) {
            fields.put("variants", "Add at least one size and colour option.");
            throw new ProductValidationException(fields);
        }
        if (variants.size() > 100) {
            fields.put("variants", "A product can contain at most 100 variants.");
            throw new ProductValidationException(fields);
        }

        var uniqueCombinations = new HashSet<String>();
        var normalized = new java.util.ArrayList<CreateProductVariantCommand>();
        for (int index = 0; index < variants.size(); index++) {
            CreateProductVariantCommand variant = variants.get(index);
            String prefix = "variants[" + index + "]";
            if (variant == null) {
                fields.put(prefix, "Variant details are required.");
                continue;
            }

            String size = normalizeText(variant.size());
            String color = normalizeText(variant.color());
            validateText(fields, prefix + ".size", size, 32, "Size");
            validateText(fields, prefix + ".color", color, 64, "Colour");
            validatePrice(fields, prefix + ".price", variant.price());
            if (variant.availability() == null) {
                fields.put(prefix + ".availability", "Availability is required.");
            }

            String combination = size.toLowerCase(Locale.ROOT)
                    + "\u0000"
                    + color.toLowerCase(Locale.ROOT);
            if (!size.isBlank() && !color.isBlank() && !uniqueCombinations.add(combination)) {
                fields.put(prefix + ".color", "This size and colour combination is duplicated.");
            }
            normalized.add(new CreateProductVariantCommand(
                    size, color, variant.price(), variant.availability()));
        }

        if (!fields.isEmpty()) {
            throw new ProductValidationException(fields);
        }
        return List.copyOf(normalized);
    }

    private void validatePrice(Map<String, String> fields, String field, BigDecimal price) {
        if (price == null
                || price.compareTo(BigDecimal.ZERO) <= 0
                || price.scale() > 2
                || price.precision() - price.scale() > 10) {
            fields.put(field, "Price must be positive with at most 10 digits and 2 decimals.");
        }
    }

    private void validateImageUrl(Map<String, String> fields, String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        if (imageUrl.length() > 500) {
            fields.put("imageUrl", "Product image URL must not exceed 500 characters.");
            return;
        }
        if ((imageUrl.startsWith("/products/")
                        || imageUrl.startsWith("/api/product-images/"))
                && !imageUrl.contains("..")) {
            return;
        }
        try {
            URI uri = new URI(imageUrl);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                return;
            }
        } catch (URISyntaxException ignored) {
            // Report one field-level validation error below.
        }
        fields.put(
                "imageUrl",
                "Use an HTTPS image URL or an uploaded local product image.");
    }

    private void validateText(
            Map<String, String> fields,
            String field,
            String value,
            int maximumLength,
            String label) {
        if (value.isBlank()) {
            fields.put(field, label + " is required.");
        } else if (value.length() > maximumLength) {
            fields.put(field, label + " must not exceed " + maximumLength + " characters.");
        }
    }
}

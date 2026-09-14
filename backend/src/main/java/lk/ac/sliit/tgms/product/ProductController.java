package lk.ac.sliit.tgms.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.authorization.RoleGuards.SalesOfficerOnly;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @SalesOfficerOnly
    public ResponseEntity<CreateProductResponse> create(
            @Valid @RequestBody CreateProductRequest request) {
        GarmentProductDetails details;
        if (request.variants() == null) {
            details = productService.createProduct(
                    request.name(),
                    request.category(),
                    request.imageUrl(),
                    request.size(),
                    request.color(),
                    request.price(),
                    request.availability());
        } else {
            details = productService.createProduct(
                    request.name(), request.category(), request.description(), request.imageUrl(),
                    request.variants().stream()
                            .map(variant -> variant == null
                                    ? null
                                    : new CreateProductVariantCommand(
                                            variant.size(),
                                            variant.color(),
                                            variant.price(),
                                            variant.availability()))
                            .toList());
        }
        ProductResponse product = ProductResponse.from(details);
        return ResponseEntity.created(URI.create("/api/products/" + product.id()))
                .body(new CreateProductResponse(
                        "Garment product created successfully.", product));
    }

    @GetMapping
    public List<ProductResponse> listPublicCatalog(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String availability) {
        return productService
                .getPublicCatalog(search, category, size, color, availability)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/catalog/{productId}")
    public ProductResponse getPublicCatalogProduct(@PathVariable String productId) {
        return ProductResponse.from(
                productService.getPublicCatalogProduct(parseProductId(productId)));
    }

    @GetMapping("/{productId}")
    @SalesOfficerOnly
    public ProductResponse getById(@PathVariable String productId) {
        return ProductResponse.from(productService.getProduct(parseProductId(productId)));
    }

    @PutMapping("/{productId}")
    @SalesOfficerOnly
    public UpdateProductResponse update(
            @PathVariable String productId,
            @Valid @RequestBody UpdateProductRequest request) {
        GarmentProductDetails details = productService.updateProduct(
                parseProductId(productId),
                request.variantId(),
                request.name(),
                request.category(),
                request.description(),
                request.imageUrl(),
                request.size(),
                request.color(),
                request.price(),
                request.availability());
        return new UpdateProductResponse(
                "Garment product updated successfully.", ProductResponse.from(details));
    }

    @DeleteMapping("/{productId}")
    @SalesOfficerOnly
    public DiscontinueProductResponse discontinue(@PathVariable String productId) {
        GarmentProductDetails details = productService.discontinueProduct(
                parseProductId(productId));
        return new DiscontinueProductResponse(
                "Garment product discontinued successfully.", ProductResponse.from(details));
    }

    private long parseProductId(String productId) {
        try {
            return Long.parseLong(productId);
        } catch (NumberFormatException exception) {
            throw new ProductValidationException(
                    Map.of("productId", "Product ID must be a positive number."));
        }
    }

    public record CreateProductRequest(
            String name,
            String category,
            String description,
            String imageUrl,
            String size,
            String color,
            BigDecimal price,
            CreateProductAvailability availability,
            List<CreateProductVariantRequest> variants) {}

    public record CreateProductVariantRequest(
            String size,
            String color,
            BigDecimal price,
            CreateProductAvailability availability) {}

    public record CreateProductResponse(String message, ProductResponse product) {}

    public record UpdateProductRequest(
            @NotNull(message = "Variant is required.")
                    @Positive(message = "Variant ID must be a positive number.")
                    Long variantId,
            @NotBlank(message = "Product name is required.")
                    @Size(max = 160, message = "Product name must not exceed 160 characters.")
                    String name,
            @NotBlank(message = "Category is required.")
                    @Size(max = 100, message = "Category must not exceed 100 characters.")
                    String category,
            @Size(max = 2000, message = "Description must not exceed 2000 characters.")
                    String description,
            @Size(max = 500, message = "Product image URL must not exceed 500 characters.")
                    String imageUrl,
            @NotBlank(message = "Size is required.")
                    @Size(max = 32, message = "Size must not exceed 32 characters.")
                    String size,
            @NotBlank(message = "Color is required.")
                    @Size(max = 64, message = "Color must not exceed 64 characters.")
                    String color,
            @NotNull(message = "Price is required.")
                    @DecimalMin(value = "0.01", message = "Price must be greater than zero.")
                    @Digits(
                            integer = 10,
                            fraction = 2,
                            message = "Price must have at most 10 digits and 2 decimals.")
                    BigDecimal price,
            @NotNull(message = "Availability is required.")
                    CreateProductAvailability availability) {}

    public record UpdateProductResponse(String message, ProductResponse product) {}

    public record DiscontinueProductResponse(String message, ProductResponse product) {}

    public record ProductResponse(
            long id,
            long categoryId,
            String name,
            String description,
            String imageUrl,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt,
            CategoryResponse category,
            List<VariantResponse> variants) {

        static ProductResponse from(GarmentProductDetails details) {
            GarmentProduct product = details.product();
            return new ProductResponse(
                    product.id(),
                    product.categoryId(),
                    product.name(),
                    product.description(),
                    product.imageUrl(),
                    product.status(),
                    product.createdAt(),
                    product.updatedAt(),
                    CategoryResponse.from(details.category()),
                    details.variants().stream().map(VariantResponse::from).toList());
        }
    }

    public record CategoryResponse(
            long id,
            String name,
            String description,
            CategoryStatus status,
            Instant createdAt,
            Instant updatedAt) {

        static CategoryResponse from(ProductCategory category) {
            return new CategoryResponse(
                    category.id(),
                    category.name(),
                    category.description(),
                    category.status(),
                    category.createdAt(),
                    category.updatedAt());
        }
    }

    public record VariantResponse(
            long id,
            long productId,
            String size,
            String color,
            String price,
            VariantStatus status,
            Instant createdAt,
            Instant updatedAt) {

        static VariantResponse from(ProductVariant variant) {
            return new VariantResponse(
                    variant.id(),
                    variant.productId(),
                    variant.size(),
                    variant.color(),
                    variant.price().toPlainString(),
                    variant.status(),
                    variant.createdAt(),
                    variant.updatedAt());
        }
    }
}

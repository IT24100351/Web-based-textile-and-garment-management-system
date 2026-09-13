package lk.ac.sliit.tgms.product;

import java.time.Duration;
import lk.ac.sliit.tgms.authorization.RoleGuards.SalesOfficerOnly;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductImageController {

    private final ProductImageStorageService imageStorageService;

    public ProductImageController(ProductImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(
            path = "/api/products/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SalesOfficerOnly
    public UploadProductImageResponse upload(@RequestPart MultipartFile imageFile) {
        var stored = imageStorageService.store(imageFile);
        return new UploadProductImageResponse(
                "Product photo uploaded successfully.",
                stored.imageUrl(),
                stored.mediaType(),
                stored.size());
    }

    @GetMapping("/api/product-images/{fileName:.+}")
    public ResponseEntity<Resource> read(@PathVariable String fileName) {
        return imageStorageService.find(fileName)
                .map(stored -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                        .contentType(MediaType.parseMediaType(stored.mediaType()))
                        .contentLength(stored.size())
                        .body(stored.resource()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record UploadProductImageResponse(
            String message,
            String imageUrl,
            String mediaType,
            long size) {}
}

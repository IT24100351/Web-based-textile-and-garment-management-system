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

    /** Service responsible for persisting and retrieving product image files. */
    private final ProductImageStorageService imageStorageService;

    /**
     * Creates the controller with its image storage dependency.
     *
     * @param imageStorageService service used to store and load product images
     */
    public ProductImageController(ProductImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * Uploads a product image and returns metadata for the stored file.
     * Only sales officers may upload product images.
     *
     * @param imageFile image supplied as a multipart form-data request part
     * @return confirmation message and details required to access the uploaded image
     */
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

    /**
     * Serves a previously uploaded product image by file name.
     * Successful responses are publicly cacheable for one year; an unknown file
     * name produces a {@code 404 Not Found} response.
     *
     * @param fileName stored image file name, including its extension
     * @return the image resource with its media type and file size, or {@code 404}
     */
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

    /**
     * Response returned after a product image has been stored successfully.
     *
     * @param message human-readable upload result
     * @param imageUrl URL from which the image can be retrieved
     * @param mediaType MIME type of the stored image
     * @param size size of the stored file in bytes
     */
    public record UploadProductImageResponse(
            String message,
            String imageUrl,
            String mediaType,
            long size) {}
}

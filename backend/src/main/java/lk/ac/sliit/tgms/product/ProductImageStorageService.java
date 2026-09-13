package lk.ac.sliit.tgms.product;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageStorageService {

    static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private final Path storageDirectory;

    public ProductImageStorageService(
            @Value("${tgms.products.image-storage-directory:./data/product-images}")
                    String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredProductImage store(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            throw invalidImage("Choose a product photo to upload.");
        }
        if (imageFile.getSize() > MAX_IMAGE_BYTES) {
            throw invalidImage("Product photos must be 5 MB or smaller.");
        }

        byte[] bytes;
        try {
            bytes = imageFile.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("The product photo could not be read.", exception);
        }

        ImageType imageType = detectImageType(bytes)
                .orElseThrow(() -> invalidImage(
                        "Use a valid JPEG, PNG, or WebP product photo."));
        String fileName = UUID.randomUUID() + imageType.extension();
        Path destination = storageDirectory.resolve(fileName).normalize();
        if (!destination.getParent().equals(storageDirectory)) {
            throw new IllegalStateException("The product photo destination is invalid.");
        }

        try {
            Files.createDirectories(storageDirectory);
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException exception) {
            throw new IllegalStateException("The product photo could not be stored.", exception);
        }
        return new StoredProductImage(
                "/api/product-images/" + fileName,
                imageType.mediaType(),
                bytes.length);
    }

    public Optional<StoredImageResource> find(String fileName) {
        if (fileName == null
                || !fileName.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp)")) {
            return Optional.empty();
        }
        Path candidate = storageDirectory.resolve(fileName).normalize();
        if (!candidate.getParent().equals(storageDirectory) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        try {
            Resource resource = new UrlResource(candidate.toUri());
            return resource.isReadable()
                    ? Optional.of(new StoredImageResource(
                            resource,
                            mediaTypeForFileName(fileName),
                            Files.size(candidate)))
                    : Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<ImageType> detectImageType(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return Optional.of(ImageType.JPEG);
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return Optional.of(ImageType.PNG);
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }

    private String mediaTypeForFileName(String fileName) {
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private ProductValidationException invalidImage(String message) {
        return new ProductValidationException(Map.of("imageFile", message));
    }

    public record StoredProductImage(String imageUrl, String mediaType, long size) {}

    public record StoredImageResource(Resource resource, String mediaType, long size) {}

    private enum ImageType {
        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png"),
        WEBP(".webp", "image/webp");

        private final String extension;
        private final String mediaType;

        ImageType(String extension, String mediaType) {
            this.extension = extension;
            this.mediaType = mediaType;
        }

        String extension() {
            return extension;
        }

        String mediaType() {
            return mediaType;
        }
    }
}

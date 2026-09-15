package lk.ac.sliit.tgms.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ProductImageStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndReadsAValidatedLocalProductImage() {
        var service = new ProductImageStorageService(temporaryDirectory.toString());
        byte[] png = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00
        };

        var stored = service.store(new MockMultipartFile(
                "imageFile", "shirt.png", "image/png", png));

        assertThat(stored.imageUrl())
                .matches("/api/product-images/[0-9a-f-]{36}\\.png");
        assertThat(stored.mediaType()).isEqualTo("image/png");
        String fileName = stored.imageUrl().substring(stored.imageUrl().lastIndexOf('/') + 1);
        var loaded = service.find(fileName);
        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().size()).isEqualTo(png.length);
    }

    @Test
    void rejectsAFileWhoseBytesAreNotASupportedImage() {
        var service = new ProductImageStorageService(temporaryDirectory.toString());

        assertThatThrownBy(() -> service.store(new MockMultipartFile(
                        "imageFile", "not-an-image.jpg", "image/jpeg", "plain text".getBytes())))
                .isInstanceOf(ProductValidationException.class)
                .satisfies(exception -> assertThat(
                                ((ProductValidationException) exception).fields())
                        .containsKey("imageFile"));
    }
}

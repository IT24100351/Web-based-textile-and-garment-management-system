package lk.ac.sliit.tgms.inventory;

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
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.authorization.RoleGuards.InventoryAvailabilityReadable;
import lk.ac.sliit.tgms.authorization.RoleGuards.InventoryManagerOnly;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
@RequestMapping("/api/inventory-materials")
public class InventoryMaterialController {

    private final InventoryMaterialService inventoryMaterialService;

    public InventoryMaterialController(InventoryMaterialService inventoryMaterialService) {
        this.inventoryMaterialService = inventoryMaterialService;
    }

    @PostMapping
    @InventoryManagerOnly
    public ResponseEntity<CreateInventoryMaterialResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInventoryMaterialRequest request) {
        InventoryMaterial material = inventoryMaterialService.createMaterial(
                currentUserId(jwt),
                request.sourceMaterialSupplyId(),
                request.materialCode(),
                request.materialName(),
                request.materialDescription(),
                request.materialType(),
                request.unitOfMeasure(),
                request.currentQuantity(),
                request.lowStockThreshold());
        InventoryMaterialResponse response = InventoryMaterialResponse.from(material);
        return ResponseEntity.created(URI.create("/api/inventory-materials/" + response.id()))
                .body(new CreateInventoryMaterialResponse(
                        "Inventory material created successfully.", response));
    }

    @GetMapping
    @InventoryManagerOnly
    public InventoryMaterialListResponse list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialType) {
        return new InventoryMaterialListResponse(
                inventoryMaterialService.getMaterials(search, status, materialType)
                        .stream()
                        .map(InventoryMaterialResponse::from)
                        .toList());
    }

    @GetMapping("/low-stock")
    @InventoryManagerOnly
    public LowStockInventoryMaterialListResponse lowStock() {
        List<InventoryMaterialResponse> materials = inventoryMaterialService.getLowStockMaterials()
                .stream()
                .map(InventoryMaterialResponse::from)
                .toList();
        return new LowStockInventoryMaterialListResponse(materials.size(), materials);
    }

    @GetMapping("/{materialId}")
    @InventoryManagerOnly
    public InventoryMaterialResponse getById(@PathVariable long materialId) {
        return InventoryMaterialResponse.from(inventoryMaterialService.requireMaterial(materialId));
    }

    @PutMapping("/{materialId}")
    @InventoryManagerOnly
    public UpdateInventoryMaterialResponse update(
            @PathVariable long materialId,
            @Valid @RequestBody UpdateInventoryMaterialRequest request) {
        InventoryMaterial material = inventoryMaterialService.updateMaterialMetadata(
                materialId,
                request.materialCode(),
                request.materialName(),
                request.materialDescription(),
                request.materialType(),
                request.unitOfMeasure(),
                request.lowStockThreshold(),
                request.status());
        return new UpdateInventoryMaterialResponse(
                "Inventory material updated successfully.",
                InventoryMaterialResponse.from(material));
    }

    @DeleteMapping("/{materialId}")
    @InventoryManagerOnly
    public ArchiveInventoryMaterialResponse archive(@PathVariable long materialId) {
        InventoryMaterial material = inventoryMaterialService.archiveMaterial(materialId);
        return new ArchiveInventoryMaterialResponse(
                "Inventory material archived successfully.",
                InventoryMaterialResponse.from(material));
    }

    @GetMapping("/{materialId}/availability")
    @InventoryAvailabilityReadable
    public InventoryMaterialAvailabilityResponse availability(
            @PathVariable long materialId,
            @RequestParam(required = false) BigDecimal requiredQuantity) {
        return InventoryMaterialAvailabilityResponse.from(
                inventoryMaterialService.checkAvailability(materialId, requiredQuantity));
    }

    @PostMapping("/{materialId}/consume")
    @InventoryManagerOnly
    public ConsumeInventoryMaterialResponse consume(
            @PathVariable long materialId,
            @Valid @RequestBody ConsumeInventoryMaterialRequest request) {
        InventoryMaterial material = inventoryMaterialService.consumeStock(
                materialId, request.quantity());
        return new ConsumeInventoryMaterialResponse(
                "Inventory stock consumed successfully.",
                InventoryMaterialResponse.from(material));
    }

    private long currentUserId(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidSessionException();
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidSessionException();
        }
    }

    public record CreateInventoryMaterialRequest(
            @Positive(message = "Source material supply ID must be a positive number.")
                    Long sourceMaterialSupplyId,
            @NotBlank(message = "Material code is required.")
                    @Size(max = 64, message = "Material code must not exceed 64 characters.")
                    String materialCode,
            @NotBlank(message = "Material name is required.")
                    @Size(max = 160, message = "Material name must not exceed 160 characters.")
                    String materialName,
            @Size(max = 500, message = "Description must not exceed 500 characters.")
                    String materialDescription,
            @NotNull(message = "Material type is required.")
                    InventoryMaterialType materialType,
            @NotBlank(message = "Unit of measure is required.")
                    @Size(max = 32, message = "Unit of measure must not exceed 32 characters.")
                    String unitOfMeasure,
            @NotNull(message = "Opening quantity is required.")
                    @DecimalMin(value = "0.001", message = "Opening quantity must be greater than zero.")
                    @Digits(
                            integer = 11,
                            fraction = 3,
                            message = "Opening quantity must have at most 11 digits and 3 decimals.")
                    BigDecimal currentQuantity,
            @NotNull(message = "Low-stock threshold is required.")
                    @DecimalMin(value = "0.00", message = "Low-stock threshold cannot be negative.")
                    @Digits(
                            integer = 11,
                            fraction = 3,
                            message = "Low-stock threshold must have at most 11 digits and 3 decimals.")
                    BigDecimal lowStockThreshold) {}

    public record UpdateInventoryMaterialRequest(
            @NotBlank(message = "Material code is required.")
                    @Size(max = 64, message = "Material code must not exceed 64 characters.")
                    String materialCode,
            @NotBlank(message = "Material name is required.")
                    @Size(max = 160, message = "Material name must not exceed 160 characters.")
                    String materialName,
            @Size(max = 500, message = "Description must not exceed 500 characters.")
                    String materialDescription,
            @NotNull(message = "Material type is required.")
                    InventoryMaterialType materialType,
            @NotBlank(message = "Unit of measure is required.")
                    @Size(max = 32, message = "Unit of measure must not exceed 32 characters.")
                    String unitOfMeasure,
            @NotNull(message = "Low-stock threshold is required.")
                    @DecimalMin(value = "0.00", message = "Low-stock threshold cannot be negative.")
                    @Digits(
                            integer = 11,
                            fraction = 3,
                            message = "Low-stock threshold must have at most 11 digits and 3 decimals.")
                    BigDecimal lowStockThreshold,
            @NotNull(message = "Status is required.")
                    InventoryMaterialStatus status) {}

    public record CreateInventoryMaterialResponse(
            String message, InventoryMaterialResponse material) {}

    public record UpdateInventoryMaterialResponse(
            String message, InventoryMaterialResponse material) {}

    public record ArchiveInventoryMaterialResponse(
            String message, InventoryMaterialResponse material) {}

    public record ConsumeInventoryMaterialRequest(
            @NotNull(message = "Usage quantity is required.")
                    @DecimalMin(
                            value = "0.001",
                            message = "Usage quantity must be greater than zero.")
                    @Digits(
                            integer = 11,
                            fraction = 3,
                            message = "Usage quantity must have at most 11 digits and 3 decimals.")
                    BigDecimal quantity) {}

    public record ConsumeInventoryMaterialResponse(
            String message, InventoryMaterialResponse material) {}

    public record InventoryMaterialListResponse(List<InventoryMaterialResponse> materials) {}

    public record LowStockInventoryMaterialListResponse(
            int count, List<InventoryMaterialResponse> materials) {}

    public record InventoryMaterialAvailabilityResponse(
            long materialId,
            String currentQuantity,
            String requiredQuantity,
            InventoryMaterialStatus status,
            boolean available,
            InventoryAvailabilityState availabilityState) {

        static InventoryMaterialAvailabilityResponse from(
                InventoryMaterialAvailability availability) {
            return new InventoryMaterialAvailabilityResponse(
                    availability.materialId(),
                    availability.currentQuantity().toPlainString(),
                    availability.requiredQuantity().toPlainString(),
                    availability.status(),
                    availability.available(),
                    availability.availabilityState());
        }
    }

    public record InventoryMaterialResponse(
            long id,
            Long sourceMaterialSupplyId,
            String materialCode,
            String materialName,
            String materialDescription,
            InventoryMaterialType materialType,
            String unitOfMeasure,
            String currentQuantity,
            String lowStockThreshold,
            InventoryMaterialStatus status,
            InventoryStockState stockState,
            Instant createdAt,
            Instant updatedAt) {

        static InventoryMaterialResponse from(InventoryMaterial material) {
            return new InventoryMaterialResponse(
                    material.id(),
                    material.sourceMaterialSupplyId(),
                    material.materialCode(),
                    material.materialName(),
                    material.materialDescription(),
                    material.materialType(),
                    material.unitOfMeasure(),
                    material.currentQuantity().toPlainString(),
                    material.lowStockThreshold().toPlainString(),
                    material.status(),
                    material.stockState(),
                    material.createdAt(),
                    material.updatedAt());
        }
    }
}

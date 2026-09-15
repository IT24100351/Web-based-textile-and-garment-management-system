package lk.ac.sliit.tgms.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.authorization.RoleGuards.SupplierOnly;
import lk.ac.sliit.tgms.authorization.RoleGuards.SupplyRecordsReadable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/material-supplies")
public class MaterialSupplyController {

    private final MaterialSupplyService materialSupplyService;

    public MaterialSupplyController(MaterialSupplyService materialSupplyService) {
        this.materialSupplyService = materialSupplyService;
    }

    @PostMapping
    @SupplierOnly
    public ResponseEntity<CreateMaterialSupplyResponse> createOwnSupply(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMaterialSupplyRequest request) {
        MaterialSupply supply = materialSupplyService.createOwnSupply(
                currentUserId(jwt),
                request.materialCode(),
                request.materialName(),
                request.materialDescription(),
                request.quantity(),
                request.unitOfMeasure(),
                request.unitPrice(),
                request.deliveryLeadTimeDays(),
                request.deliveryNotes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateMaterialSupplyResponse(
                        "Material supply created successfully.",
                        MaterialSupplyResponse.from(supply)));
    }

    @GetMapping
    @SupplyRecordsReadable
    public MaterialSupplyListResponse getPermittedSupplies(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        List<MaterialSupplyListItemResponse> supplies = materialSupplyService
                .getPermittedSupplies(currentUserId(jwt), search, status)
                .stream()
                .map(MaterialSupplyListItemResponse::from)
                .toList();
        return new MaterialSupplyListResponse(supplies);
    }

    @GetMapping("/{supplyId}")
    @SupplyRecordsReadable
    public MaterialSupplyResponse getPermittedSupply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long supplyId) {
        return MaterialSupplyResponse.from(
                materialSupplyService.getPermittedSupply(currentUserId(jwt), supplyId));
    }

    @PutMapping("/{supplyId}")
    @SupplierOnly
    public UpdateMaterialSupplyResponse updateOwnSupply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long supplyId,
            @Valid @RequestBody UpdateMaterialSupplyRequest request) {
        MaterialSupply supply = materialSupplyService.updateOwnSupplyDetails(
                currentUserId(jwt),
                supplyId,
                request.quantity(),
                request.unitPrice(),
                request.deliveryLeadTimeDays(),
                request.deliveryNotes());
        return new UpdateMaterialSupplyResponse(
                "Material supply updated successfully.",
                MaterialSupplyResponse.from(supply));
    }

    @DeleteMapping("/{supplyId}")
    @SupplierOnly
    public ArchiveMaterialSupplyResponse archiveOwnSupply(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long supplyId) {
        MaterialSupply supply = materialSupplyService.archiveOwnSupply(
                currentUserId(jwt), supplyId);
        return new ArchiveMaterialSupplyResponse(
                "Material supply archived successfully.",
                MaterialSupplyResponse.from(supply));
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

    public record CreateMaterialSupplyRequest(
            @NotBlank(message = "Material code is required.")
                    @Size(max = 64, message = "Material code must not exceed 64 characters.")
                    String materialCode,
            @NotBlank(message = "Material name is required.")
                    @Size(max = 160, message = "Material name must not exceed 160 characters.")
                    String materialName,
            @Size(max = 500, message = "Description must not exceed 500 characters.")
                    String materialDescription,
            @NotNull(message = "Quantity is required.")
                    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero.")
                    @Digits(
                            integer = 11,
                            fraction = 3,
                            message = "Quantity must have at most 11 digits and 3 decimals.")
                    BigDecimal quantity,
            @NotBlank(message = "Unit of measure is required.")
                    @Size(max = 32, message = "Unit of measure must not exceed 32 characters.")
                    String unitOfMeasure,
            @NotNull(message = "Unit price is required.")
                    @DecimalMin(value = "0.01", message = "Unit price must be greater than zero.")
                    @Digits(
                            integer = 10,
                            fraction = 2,
                            message = "Unit price must have at most 10 digits and 2 decimals.")
                    BigDecimal unitPrice,
            @NotNull(message = "Delivery lead time is required.")
                    @PositiveOrZero(message = "Delivery lead time must not be negative.")
                    Integer deliveryLeadTimeDays,
            @Size(max = 500, message = "Delivery notes must not exceed 500 characters.")
                    String deliveryNotes) {}

    public record CreateMaterialSupplyResponse(
            String message, MaterialSupplyResponse supply) {}

    public record UpdateMaterialSupplyRequest(
            @NotNull(message = "Quantity is required.")
                    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero.")
                    @Digits(
                            integer = 11,
                            fraction = 3,
                            message = "Quantity must have at most 11 digits and 3 decimals.")
                    BigDecimal quantity,
            @NotNull(message = "Unit price is required.")
                    @DecimalMin(value = "0.01", message = "Unit price must be greater than zero.")
                    @Digits(
                            integer = 10,
                            fraction = 2,
                            message = "Unit price must have at most 10 digits and 2 decimals.")
                    BigDecimal unitPrice,
            @NotNull(message = "Delivery lead time is required.")
                    @PositiveOrZero(message = "Delivery lead time must not be negative.")
                    Integer deliveryLeadTimeDays,
            @Size(max = 500, message = "Delivery notes must not exceed 500 characters.")
                    String deliveryNotes) {}

    public record UpdateMaterialSupplyResponse(
            String message, MaterialSupplyResponse supply) {}

    public record ArchiveMaterialSupplyResponse(
            String message, MaterialSupplyResponse supply) {}

    public record MaterialSupplyListResponse(List<MaterialSupplyListItemResponse> supplies) {}

    public record MaterialSupplyListItemResponse(
            long id,
            long supplierId,
            String supplierBusinessName,
            String materialCode,
            String materialName,
            String materialDescription,
            String quantity,
            String unitOfMeasure,
            String unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes,
            MaterialSupplyStatus status,
            Instant createdAt,
            Instant updatedAt) {

        static MaterialSupplyListItemResponse from(MaterialSupplyListItem item) {
            MaterialSupply supply = item.supply();
            return new MaterialSupplyListItemResponse(
                    supply.id(),
                    supply.supplierId(),
                    item.supplierBusinessName(),
                    supply.materialCode(),
                    supply.materialName(),
                    supply.materialDescription(),
                    supply.quantity().toPlainString(),
                    supply.unitOfMeasure(),
                    supply.unitPrice().toPlainString(),
                    supply.deliveryLeadTimeDays(),
                    supply.deliveryNotes(),
                    supply.status(),
                    supply.createdAt(),
                    supply.updatedAt());
        }
    }

    public record MaterialSupplyResponse(
            long id,
            long supplierId,
            String materialCode,
            String materialName,
            String materialDescription,
            String quantity,
            String unitOfMeasure,
            String unitPrice,
            int deliveryLeadTimeDays,
            String deliveryNotes,
            MaterialSupplyStatus status,
            Instant createdAt,
            Instant updatedAt) {

        static MaterialSupplyResponse from(MaterialSupply supply) {
            return new MaterialSupplyResponse(
                    supply.id(),
                    supply.supplierId(),
                    supply.materialCode(),
                    supply.materialName(),
                    supply.materialDescription(),
                    supply.quantity().toPlainString(),
                    supply.unitOfMeasure(),
                    supply.unitPrice().toPlainString(),
                    supply.deliveryLeadTimeDays(),
                    supply.deliveryNotes(),
                    supply.status(),
                    supply.createdAt(),
                    supply.updatedAt());
        }
    }
}

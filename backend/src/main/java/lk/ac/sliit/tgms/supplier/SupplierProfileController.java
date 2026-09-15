package lk.ac.sliit.tgms.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.authorization.RoleGuards.SupplierOnly;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supplier-profile")
public class SupplierProfileController {

    private final SupplierProfileService supplierProfileService;

    public SupplierProfileController(SupplierProfileService supplierProfileService) {
        this.supplierProfileService = supplierProfileService;
    }

    @GetMapping
    @SupplierOnly
    public SupplierProfileResponse getOwnProfile(@AuthenticationPrincipal Jwt jwt) {
        return SupplierProfileResponse.from(
                supplierProfileService.getOwnProfile(currentUserId(jwt)));
    }

    @PutMapping
    @SupplierOnly
    public ResponseEntity<SaveSupplierProfileResponse> saveOwnProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveSupplierProfileRequest request) {
        SupplierProfileSaveResult result = supplierProfileService.saveOwnProfile(
                currentUserId(jwt),
                request.businessName(),
                request.contactPhone(),
                request.address());
        String message = result.created()
                ? "Supplier profile created successfully."
                : "Supplier profile updated successfully.";
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(new SaveSupplierProfileResponse(
                        message, SupplierProfileResponse.from(result.profile())));
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

    public record SaveSupplierProfileRequest(
            @NotBlank(message = "Business name is required.")
                    @Size(max = 160, message = "Business name must not exceed 160 characters.")
                    String businessName,
            @NotBlank(message = "Contact phone is required.")
                    @Size(max = 32, message = "Contact phone must not exceed 32 characters.")
                    String contactPhone,
            @NotBlank(message = "Address is required.")
                    @Size(max = 500, message = "Address must not exceed 500 characters.")
                    String address) {}

    public record SaveSupplierProfileResponse(
            String message, SupplierProfileResponse profile) {}

    public record SupplierProfileResponse(
            long id,
            long userId,
            String businessName,
            String contactPhone,
            String address,
            Instant createdAt,
            Instant updatedAt) {

        static SupplierProfileResponse from(SupplierProfile profile) {
            return new SupplierProfileResponse(
                    profile.id(),
                    profile.userId(),
                    profile.businessName(),
                    profile.contactPhone(),
                    profile.address(),
                    profile.createdAt(),
                    profile.updatedAt());
        }
    }
}

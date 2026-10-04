package lk.ac.sliit.tgms.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.auth.UserRole;
import lk.ac.sliit.tgms.authorization.RoleGuards.AdministratorOnly;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@AdministratorOnly
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public List<AdminUserResponse> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String active) {
        return adminUserService.listUsers(
                        search, parseRole(role, true), parseActive(active))
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @GetMapping("/{userId}")
    public AdminUserResponse getUser(@PathVariable long userId) {
        return AdminUserResponse.from(adminUserService.getUser(userId));
    }

    @PostMapping
    public ResponseEntity<AdminUserMutationResponse> createUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateInternalUserRequest request) {
        AdminUserAccount account = adminUserService.createInternalAccount(
                authenticatedUserId(jwt),
                request.fullName(),
                request.email(),
                request.password(),
                parseRole(request.role(), false));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdminUserMutationResponse(
                        "Internal user account created successfully.",
                        AdminUserResponse.from(account)));
    }

    @PatchMapping("/{userId}/role")
    public AdminUserMutationResponse changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        AdminUserAccount account = adminUserService.changeRole(
                authenticatedUserId(jwt), userId, parseRole(request.role(), false));
        return new AdminUserMutationResponse(
                "User role updated successfully.", AdminUserResponse.from(account));
    }

    @PatchMapping("/{userId}/status")
    public AdminUserMutationResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long userId,
            @Valid @RequestBody ChangeStatusRequest request) {
        AdminUserAccount account = adminUserService.changeActiveState(
                authenticatedUserId(jwt), userId, request.active());
        return new AdminUserMutationResponse(
                request.active()
                        ? "User account activated successfully."
                        : "User account deactivated successfully.",
                AdminUserResponse.from(account));
    }

    private long authenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidSessionException();
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new InvalidSessionException();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new InvalidSessionException();
        }
    }

    private UserRole parseRole(String value, boolean optional) {
        if (value == null || value.isBlank()) {
            if (optional) {
                return null;
            }
            throw new AdminUserValidationException(
                    "A role is required.", Map.of("role", "Select a supported role."));
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AdminUserValidationException(
                    "Role is not supported.",
                    Map.of(
                            "role",
                            "Role must be one of: "
                                    + String.join(
                                            ", ",
                                            Arrays.stream(UserRole.values())
                                                    .map(Enum::name)
                                                    .toList())));
        }
    }

    private Boolean parseActive(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new AdminUserValidationException(
                "Account status filter is invalid.",
                Map.of("active", "Active must be true or false."));
    }

    public record CreateInternalUserRequest(
            @NotBlank(message = "Full name is required.")
                    @Size(max = 120, message = "Full name must not exceed 120 characters.")
                    String fullName,
            @NotBlank(message = "Email is required.")
                    @Email(message = "Enter a valid email address.")
                    @Size(max = 254, message = "Email must not exceed 254 characters.")
                    String email,
            @NotBlank(message = "Password is required.")
                    @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters.")
                    String password,
            @NotBlank(message = "Role is required.") String role) {}

    public record ChangeRoleRequest(@NotBlank(message = "Role is required.") String role) {}

    public record ChangeStatusRequest(@NotNull(message = "Active state is required.") Boolean active) {}

    public record AdminUserMutationResponse(String message, AdminUserResponse user) {}

    public record AdminUserResponse(
            long id,
            String fullName,
            String email,
            UserRole role,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        static AdminUserResponse from(AdminUserAccount account) {
            return new AdminUserResponse(
                    account.id(),
                    account.fullName(),
                    account.email(),
                    account.role(),
                    account.active(),
                    account.createdAt(),
                    account.updatedAt());
        }
    }
}

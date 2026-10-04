package lk.ac.sliit.tgms.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lk.ac.sliit.tgms.auth.AuthCookieService;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserRole;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final AuthCookieService authCookieService;

    public UserProfileController(
            UserProfileService userProfileService, AuthCookieService authCookieService) {
        this.userProfileService = userProfileService;
        this.authCookieService = authCookieService;
    }

    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(userProfileService.getProfile(authenticatedUserId(jwt)));
    }

    @PutMapping
    public ProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(userProfileService.updateProfile(
                authenticatedUserId(jwt), request.fullName()));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(
                authenticatedUserId(jwt), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearSessionCookie())
                .body(new MessageResponse(
                        "Password changed successfully. Please sign in again."));
    }

    private long authenticatedUserId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new InvalidSessionException();
        }
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Full name is required.")
                    @Size(max = 120, message = "Full name must not exceed 120 characters.")
                    String fullName) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required.")
                    @Size(max = 72, message = "Current password must not exceed 72 characters.")
                    String currentPassword,
            @NotBlank(message = "New password is required.")
                    @Size(max = 72, message = "New password must not exceed 72 characters.")
                    String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[currentPassword=<redacted>, newPassword=<redacted>]";
        }
    }

    public record MessageResponse(String message) {}

    public record ProfileResponse(long id, String fullName, String email, UserRole role) {
        static ProfileResponse from(UserAccount account) {
            return new ProfileResponse(
                    account.id(), account.fullName(), account.email(), account.role());
        }
    }
}

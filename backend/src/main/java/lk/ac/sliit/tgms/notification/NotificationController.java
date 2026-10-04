package lk.ac.sliit.tgms.notification;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationInboxResponse getInbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") String unreadOnly,
            @RequestParam(defaultValue = "50") String limit) {
        var inbox = notificationService.getInbox(
                authenticatedUserId(jwt), parseUnreadOnly(unreadOnly), parseLimit(limit));
        return new NotificationInboxResponse(
                inbox.notifications().stream().map(NotificationResponse::from).toList(),
                inbox.unreadCount());
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationMutationResponse markRead(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String notificationId) {
        notificationService.markRead(authenticatedUserId(jwt), parseNotificationId(notificationId));
        return new NotificationMutationResponse("Notification marked as read.");
    }

    @PatchMapping("/read-all")
    public NotificationMutationResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        int updated = notificationService.markAllRead(authenticatedUserId(jwt));
        return new NotificationMutationResponse(
                updated == 0 ? "No unread notifications." : "All notifications marked as read.");
    }

    private boolean parseUnreadOnly(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new NotificationValidationException(
                "Notification filters are invalid.",
                Map.of("unreadOnly", "Unread-only filter must be true or false."));
    }

    private long parseNotificationId(String value) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value.trim());
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new NotificationValidationException(
                    "Notification ID is invalid.",
                    Map.of("notificationId", "Notification ID must be a positive integer."));
        }
    }

    private int parseLimit(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed < 1 || parsed > 100) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new NotificationValidationException(
                    "Notification filters are invalid.",
                    Map.of("limit", "Limit must be a whole number between 1 and 100."));
        }
    }

    private long authenticatedUserId(Jwt jwt) {
        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw new InvalidSessionException();
            }
            return userId;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new InvalidSessionException();
        }
    }

    public record NotificationInboxResponse(
            List<NotificationResponse> notifications, long unreadCount) {}

    public record NotificationResponse(
            long id,
            NotificationKind kind,
            String title,
            String message,
            NotificationSourceModule sourceModule,
            long sourceRecordId,
            boolean unread,
            Instant readAt,
            Instant createdAt) {
        static NotificationResponse from(OperationalNotification notification) {
            return new NotificationResponse(
                    notification.id(),
                    notification.kind(),
                    notification.title(),
                    notification.message(),
                    notification.sourceModule(),
                    notification.sourceRecordId(),
                    notification.unread(),
                    notification.readAt(),
                    notification.createdAt());
        }
    }

    public record NotificationMutationResponse(String message) {}
}

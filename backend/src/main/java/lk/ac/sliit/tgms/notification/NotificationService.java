package lk.ac.sliit.tgms.notification;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationInbox getInbox(long recipientUserId, boolean unreadOnly, int limit) {
        requirePositiveUserId(recipientUserId);
        validateLimit(limit);
        return new NotificationInbox(
                notificationRepository.findForRecipient(recipientUserId, unreadOnly, limit),
                notificationRepository.countUnread(recipientUserId));
    }

    @Transactional
    public void markRead(long recipientUserId, long notificationId) {
        requirePositiveUserId(recipientUserId);
        if (notificationId <= 0) {
            throw new NotificationNotFoundException();
        }
        if (!notificationRepository.markRead(recipientUserId, notificationId)) {
            // Recipient scope is intentionally part of the UPDATE so another user's notification
            // is indistinguishable from an unknown notification ID.
            throw new NotificationNotFoundException();
        }
    }

    @Transactional
    public int markAllRead(long recipientUserId) {
        requirePositiveUserId(recipientUserId);
        return notificationRepository.markAllRead(recipientUserId);
    }

    private void requirePositiveUserId(long recipientUserId) {
        if (recipientUserId <= 0) {
            throw new IllegalArgumentException("Authenticated user ID must be positive.");
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Notification limit must be between 1 and 100.");
        }
    }

    public record NotificationInbox(List<OperationalNotification> notifications, long unreadCount) {}
}

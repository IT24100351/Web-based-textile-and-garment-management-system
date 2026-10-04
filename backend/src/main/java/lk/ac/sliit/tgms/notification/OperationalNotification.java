package lk.ac.sliit.tgms.notification;

import java.time.Instant;

public record OperationalNotification(
        long id,
        long recipientUserId,
        NotificationKind kind,
        String title,
        String message,
        NotificationSourceModule sourceModule,
        long sourceRecordId,
        Instant readAt,
        Instant createdAt) {

    public boolean unread() {
        return readAt == null;
    }
}

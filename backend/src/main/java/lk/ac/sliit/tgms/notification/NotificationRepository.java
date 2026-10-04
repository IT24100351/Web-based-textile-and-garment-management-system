package lk.ac.sliit.tgms.notification;

import java.util.List;

public interface NotificationRepository {

    void create(
            long recipientUserId,
            NotificationKind kind,
            String title,
            String message,
            NotificationSourceModule sourceModule,
            long sourceRecordId);

    List<OperationalNotification> findForRecipient(long recipientUserId, boolean unreadOnly, int limit);

    long countUnread(long recipientUserId);

    boolean markRead(long recipientUserId, long notificationId);

    int markAllRead(long recipientUserId);
}

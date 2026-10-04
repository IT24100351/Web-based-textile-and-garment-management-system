package lk.ac.sliit.tgms.notification;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException() {
        super("Notification was not found.");
    }
}

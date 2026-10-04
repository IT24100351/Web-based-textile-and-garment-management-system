package lk.ac.sliit.tgms.notification;

import java.util.Map;

public class NotificationValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public NotificationValidationException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

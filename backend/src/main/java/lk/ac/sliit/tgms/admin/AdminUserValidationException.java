package lk.ac.sliit.tgms.admin;

import java.util.Map;

public class AdminUserValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public AdminUserValidationException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

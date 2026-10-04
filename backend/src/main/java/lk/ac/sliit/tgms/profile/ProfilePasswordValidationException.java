package lk.ac.sliit.tgms.profile;

import java.util.Map;

public class ProfilePasswordValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public ProfilePasswordValidationException(String field, String message) {
        super("Please correct the highlighted password fields.");
        this.fields = Map.of(field, message);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

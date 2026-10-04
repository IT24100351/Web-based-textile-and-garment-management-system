package lk.ac.sliit.tgms.search;

import java.util.Map;

public class SharedSearchValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public SharedSearchValidationException(Map<String, String> fields) {
        super("Please correct the highlighted fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

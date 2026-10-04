package lk.ac.sliit.tgms.report;

import java.util.Map;

public class ReportValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public ReportValidationException(Map<String, String> fields) {
        super("The report filters are invalid.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

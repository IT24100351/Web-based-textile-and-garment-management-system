package lk.ac.sliit.tgms.quotation;

import java.util.Map;

public class QuotationValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public QuotationValidationException(Map<String, String> fields) {
        super("Please correct the highlighted quotation fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

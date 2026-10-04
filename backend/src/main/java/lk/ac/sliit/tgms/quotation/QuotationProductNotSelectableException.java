package lk.ac.sliit.tgms.quotation;

import java.util.Map;

public class QuotationProductNotSelectableException extends RuntimeException {
    private final Map<String, String> fields;

    public QuotationProductNotSelectableException(Map<String, String> fields) {
        super("One or more quoted garment selections are no longer available.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

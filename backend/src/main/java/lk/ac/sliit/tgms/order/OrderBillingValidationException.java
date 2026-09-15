package lk.ac.sliit.tgms.order;

import java.util.Map;



    public OrderBillingValidationException(Map<String, String> fields) {
        super("Please correct the highlighted billing fields.");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }
}

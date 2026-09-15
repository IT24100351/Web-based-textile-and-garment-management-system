package lk.ac.sliit.tgms.production;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductionTaskStatusTransitionException extends RuntimeException {

    private final Map<String, String> fields;

    public ProductionTaskStatusTransitionException(
            ProductionTaskStatus current,
            ProductionTaskStatus requested) {
        this(
                "Production status cannot move from " + current.name() + " to " + requested.name() + ".",
                Map.of("status", allowedMessage(current)));
    }

    public ProductionTaskStatusTransitionException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> fields() {
        return fields;
    }

    private static String allowedMessage(ProductionTaskStatus current) {
        List<ProductionTaskStatus> allowed = ProductionTaskStatusLifecycle.allowedTransitions(current);
        if (allowed.isEmpty()) {
            return "This production task is completed and cannot be changed.";
        }
        return "Allowed next status: " + allowed.stream()
                .map(ProductionTaskStatus::name)
                .collect(Collectors.joining(" or ")) + ".";
    }
}

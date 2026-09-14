package lk.ac.sliit.tgms.order;

public class OrderBillingStateException extends RuntimeException {
    private final String code;

    public OrderBillingStateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

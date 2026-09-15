package lk.ac.sliit.tgms.order;

public class OrderCancellationException extends RuntimeException {
    public OrderCancellationException() {
        super("An order with a recorded payment cannot be cancelled. Resolve the payment record first.");
    }
}

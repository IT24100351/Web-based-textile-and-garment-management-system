package lk.ac.sliit.tgms.order;

public class OrderInUseException extends RuntimeException {
    public OrderInUseException() {
        super("This order cannot be deleted because it has already entered the business workflow.");
    }
}

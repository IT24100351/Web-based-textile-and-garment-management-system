package lk.ac.sliit.tgms.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("Order was not found.");
    }
}

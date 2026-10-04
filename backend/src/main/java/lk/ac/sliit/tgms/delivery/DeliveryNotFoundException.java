package lk.ac.sliit.tgms.delivery;

public class DeliveryNotFoundException extends RuntimeException {
    public DeliveryNotFoundException() {
        super("The requested delivery record was not found.");
    }
}

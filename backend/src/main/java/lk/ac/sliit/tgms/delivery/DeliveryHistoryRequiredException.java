package lk.ac.sliit.tgms.delivery;

public class DeliveryHistoryRequiredException extends RuntimeException {
    public DeliveryHistoryRequiredException() {
        super("This delivery cannot be deleted because it is part of active or completed delivery history.");
    }
}

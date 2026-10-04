package lk.ac.sliit.tgms.production;

public class ProductionTaskInUseException extends RuntimeException {
    public ProductionTaskInUseException() {
        super("Only pending production tasks that have not started can be deleted.");
    }
}

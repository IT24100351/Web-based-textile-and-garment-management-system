package lk.ac.sliit.tgms.production;

public class ProductionTaskCompletedException extends RuntimeException {

    public ProductionTaskCompletedException() {
        super("This production task is completed and its historical work details cannot be changed.");
    }
}

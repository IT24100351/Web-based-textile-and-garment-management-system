package lk.ac.sliit.tgms.production;

public class ProductionTaskNotFoundException extends RuntimeException {

    public ProductionTaskNotFoundException() {
        super("The requested production task was not found.");
    }
}

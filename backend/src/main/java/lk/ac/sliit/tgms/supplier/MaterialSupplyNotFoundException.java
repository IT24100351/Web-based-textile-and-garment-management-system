package lk.ac.sliit.tgms.supplier;

public class MaterialSupplyNotFoundException extends RuntimeException {

    public MaterialSupplyNotFoundException() {
        super("The requested material supply was not found.");
    }
}

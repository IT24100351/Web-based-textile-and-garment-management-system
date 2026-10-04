package lk.ac.sliit.tgms.supplier;

public class MaterialSupplyInUseException extends RuntimeException {

    public MaterialSupplyInUseException() {
        super("This material supply cannot be deleted because it is already used by inventory records.");
    }
}

package lk.ac.sliit.tgms.supplier;

public class MaterialSupplyArchivedException extends RuntimeException {

    public MaterialSupplyArchivedException() {
        super("An archived material supply cannot be edited.");
    }
}

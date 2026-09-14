package lk.ac.sliit.tgms.inventory;

public class InventoryMaterialArchivedException extends RuntimeException {

    public InventoryMaterialArchivedException() {
        super("An archived inventory material cannot be edited.");
    }
}

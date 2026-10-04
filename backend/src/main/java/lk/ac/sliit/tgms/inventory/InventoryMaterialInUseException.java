package lk.ac.sliit.tgms.inventory;

public class InventoryMaterialInUseException extends RuntimeException {

    public InventoryMaterialInUseException() {
        super("This material cannot be deleted because it is already used in existing records.");
    }
}

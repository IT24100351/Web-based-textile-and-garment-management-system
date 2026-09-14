package lk.ac.sliit.tgms.inventory;

public class InventoryMaterialCodeExistsException extends RuntimeException {

    public InventoryMaterialCodeExistsException() {
        super("An inventory material with this material code already exists.");
    }
}

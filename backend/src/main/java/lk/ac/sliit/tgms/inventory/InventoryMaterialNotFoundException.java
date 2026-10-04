package lk.ac.sliit.tgms.inventory;

public class InventoryMaterialNotFoundException extends RuntimeException {

    public InventoryMaterialNotFoundException() {
        super("Inventory material was not found.");
    }
}

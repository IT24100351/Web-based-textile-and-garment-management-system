package lk.ac.sliit.tgms.inventory;

public class InventoryMaterialUnavailableException extends RuntimeException {

    public InventoryMaterialUnavailableException(InventoryMaterialStatus status) {
        super("Inventory material is not active and cannot be consumed. Current status: "
                + status.name() + ".");
    }
}

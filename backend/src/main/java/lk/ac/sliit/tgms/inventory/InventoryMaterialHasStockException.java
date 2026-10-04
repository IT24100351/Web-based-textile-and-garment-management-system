package lk.ac.sliit.tgms.inventory;

public class InventoryMaterialHasStockException extends RuntimeException {
    public InventoryMaterialHasStockException() {
        super("This inventory material cannot be deleted while stock quantity is greater than zero.");
    }
}

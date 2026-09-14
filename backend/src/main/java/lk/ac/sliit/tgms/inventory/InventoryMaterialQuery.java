package lk.ac.sliit.tgms.inventory;

/** Validated filters for an inventory-material list query. */
public record InventoryMaterialQuery(
        String search,
        InventoryMaterialStatus status,
        InventoryMaterialType materialType) {

    public static InventoryMaterialQuery empty() {
        return new InventoryMaterialQuery(null, null, null);
    }
}

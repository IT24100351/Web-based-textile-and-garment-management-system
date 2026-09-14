package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;

public enum InventoryStockState {
    LOW_STOCK,
    SUFFICIENT;

    public static InventoryStockState from(
            BigDecimal currentQuantity, BigDecimal lowStockThreshold) {
        if (currentQuantity == null || lowStockThreshold == null) {
            throw new IllegalArgumentException(
                    "Current quantity and low-stock threshold are required.");
        }
        return currentQuantity.compareTo(lowStockThreshold) <= 0
                ? LOW_STOCK
                : SUFFICIENT;
    }
}

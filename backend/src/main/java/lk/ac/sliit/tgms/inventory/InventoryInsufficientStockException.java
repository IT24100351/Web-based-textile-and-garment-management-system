package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;

public class InventoryInsufficientStockException extends RuntimeException {

    private final BigDecimal requestedQuantity;
    private final BigDecimal currentQuantity;

    public InventoryInsufficientStockException(
            BigDecimal requestedQuantity, BigDecimal currentQuantity) {
        super("Insufficient stock. Requested " + requestedQuantity.toPlainString()
                + " but only " + currentQuantity.toPlainString() + " is available.");
        this.requestedQuantity = requestedQuantity;
        this.currentQuantity = currentQuantity;
    }

    public BigDecimal requestedQuantity() {
        return requestedQuantity;
    }

    public BigDecimal currentQuantity() {
        return currentQuantity;
    }
}

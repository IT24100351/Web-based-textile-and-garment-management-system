package lk.ac.sliit.tgms.quotation;

import java.math.BigDecimal;
import java.time.Instant;

public record QuotationItem(
        long id,
        long quotationId,
        long productId,
        long variantId,
        String productNameSnapshot,
        int quantity,
        String selectedSize,
        String selectedColor,
        BigDecimal unitPriceSnapshot,
        Instant createdAt) {

    public BigDecimal lineTotal() {
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }
}

package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;

public record OrderDetailItem(
        long id,
        long productId,
        long variantId,
        String productName,
        String productImageUrl,
        int quantity,
        String selectedSize,
        String selectedColor,
        BigDecimal unitPriceSnapshot,
        BigDecimal lineTotal) {}

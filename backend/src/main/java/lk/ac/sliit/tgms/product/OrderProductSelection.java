package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;

/** Authoritative product snapshot returned to Order Management before a new order is saved. */
public record OrderProductSelection(
        long productId,
        long variantId,
        String productName,
        long categoryId,
        String categoryName,
        String size,
        String color,
        BigDecimal currentPrice,
        VariantStatus availability) {}

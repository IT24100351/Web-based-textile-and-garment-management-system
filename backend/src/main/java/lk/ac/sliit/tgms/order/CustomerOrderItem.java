package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Historical order-line snapshot owned by Order Management.
 *
 * <p>The product and variant IDs keep the stable Product Management references. Product
 * name/image, size, color and unit price are copied as immutable order-time transaction snapshots
 * so later catalog edits cannot rewrite an existing customer's order history.
 */
public record CustomerOrderItem(
        long id,
        long orderId,
        long productId,
        long variantId,
        String productNameSnapshot,
        String productImageUrlSnapshot,
        int quantity,
        String selectedSize,
        String selectedColor,
        BigDecimal unitPriceSnapshot,
        Instant createdAt,
        Instant updatedAt) {}

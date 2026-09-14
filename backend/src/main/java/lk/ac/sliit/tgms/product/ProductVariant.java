package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductVariant(
        long id,
        long productId,
        String size,
        String color,
        BigDecimal price,
        VariantStatus status,
        Instant createdAt,
        Instant updatedAt) {}

package lk.ac.sliit.tgms.product;

import java.math.BigDecimal;

public record CreateProductVariantCommand(
        String size,
        String color,
        BigDecimal price,
        CreateProductAvailability availability) {}

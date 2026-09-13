package lk.ac.sliit.tgms.product;

import java.util.List;

public record GarmentProductDetails(
        ProductCategory category,
        GarmentProduct product,
        List<ProductVariant> variants) {

    public GarmentProductDetails {
        variants = List.copyOf(variants);
    }
}

package lk.ac.sliit.tgms.product;

public record ProductCatalogFilter(
        String search,
        String category,
        String size,
        String color,
        VariantStatus availability) {

    public static ProductCatalogFilter empty() {
        return new ProductCatalogFilter(null, null, null, null, null);
    }
}

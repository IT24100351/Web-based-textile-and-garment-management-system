package lk.ac.sliit.tgms.product;

public record ProductManagementFilter(String search, ProductStatus status) {

    public static ProductManagementFilter empty() {
        return new ProductManagementFilter(null, null);
    }
}

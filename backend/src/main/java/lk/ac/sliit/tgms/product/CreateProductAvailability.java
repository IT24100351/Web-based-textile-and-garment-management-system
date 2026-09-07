package lk.ac.sliit.tgms.product;

public enum CreateProductAvailability {
    AVAILABLE,
    UNAVAILABLE;

    VariantStatus toVariantStatus() {
        return VariantStatus.valueOf(name());
    }
}

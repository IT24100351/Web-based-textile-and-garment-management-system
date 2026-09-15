package lk.ac.sliit.tgms.supplier;

/** Validated filters for an authorized material-supply list query. */
public record MaterialSupplyQuery(
        Long supplierId,
        String search,
        MaterialSupplyStatus status) {}

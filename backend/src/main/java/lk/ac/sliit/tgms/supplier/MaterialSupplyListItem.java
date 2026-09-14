package lk.ac.sliit.tgms.supplier;

/** A material supply enriched with the owning supplier's display name. */
public record MaterialSupplyListItem(
        MaterialSupply supply,
        String supplierBusinessName) {}

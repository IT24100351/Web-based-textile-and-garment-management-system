package lk.ac.sliit.tgms.supplier;

import java.time.Instant;

/** Supplier-owned business profile linked to one authenticated user account. */
public record SupplierProfile(
        long id,
        long userId,
        String businessName,
        String contactPhone,
        String address,
        Instant createdAt,
        Instant updatedAt) {}

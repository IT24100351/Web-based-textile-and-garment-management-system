package lk.ac.sliit.tgms.product;

import java.time.Instant;

public record ProductCategory(
        long id,
        String name,
        String description,
        CategoryStatus status,
        Instant createdAt,
        Instant updatedAt) {}

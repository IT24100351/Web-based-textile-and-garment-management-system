package lk.ac.sliit.tgms.inventory;

import java.math.BigDecimal;

public record InventoryMaterialAvailability(
        long materialId,
        BigDecimal currentQuantity,
        BigDecimal requiredQuantity,
        InventoryMaterialStatus status,
        boolean available,
        InventoryAvailabilityState availabilityState) {}

package lk.ac.sliit.tgms.supplier;

import java.math.BigDecimal;
import java.time.Instant;

public record MaterialSupply(
        long id,
        long supplierId,
        String materialCode,
        String materialName,
        String materialDescription,
        BigDecimal quantity,
        String unitOfMeasure,
        BigDecimal unitPrice,
        int deliveryLeadTimeDays,
        String deliveryNotes,
        MaterialSupplyStatus status,
        Instant createdAt,
        Instant updatedAt) {}

package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderInvoice(
        long id,
        long orderId,
        String invoiceNumber,
        BigDecimal totalAmount,
        long issuedByUserId,
        Instant issuedAt) {}

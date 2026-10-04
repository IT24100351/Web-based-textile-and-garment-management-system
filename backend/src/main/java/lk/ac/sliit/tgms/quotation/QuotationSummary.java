package lk.ac.sliit.tgms.quotation;

import java.math.BigDecimal;
import java.time.Instant;

public record QuotationSummary(
        long id,
        String quotationNumber,
        long customerId,
        String customerName,
        String customerEmail,
        Instant issuedAt,
        int itemCount,
        BigDecimal totalAmount) {}

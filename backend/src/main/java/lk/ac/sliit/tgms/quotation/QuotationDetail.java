package lk.ac.sliit.tgms.quotation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record QuotationDetail(
        long id,
        String quotationNumber,
        long customerId,
        String customerName,
        String customerEmail,
        long issuedByUserId,
        Instant issuedAt,
        List<QuotationItem> items,
        BigDecimal totalAmount) {}

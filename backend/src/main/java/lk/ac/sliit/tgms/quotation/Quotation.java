package lk.ac.sliit.tgms.quotation;

import java.time.Instant;

public record Quotation(
        long id,
        String quotationNumber,
        long customerId,
        long issuedByUserId,
        Instant issuedAt) {}

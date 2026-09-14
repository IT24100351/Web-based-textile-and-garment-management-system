package lk.ac.sliit.tgms.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPaymentRecord(
        long id,
        long orderId,
        long invoiceId,
        OrderPaymentStatus paymentStatus,
        BigDecimal amountPaid,
        OrderPaymentMethod paymentMethod,
        String paymentReference,
        String note,
        long recordedByUserId,
        Instant recordedAt,
        Instant updatedAt) {}

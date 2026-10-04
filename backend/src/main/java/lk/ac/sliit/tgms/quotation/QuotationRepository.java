package lk.ac.sliit.tgms.quotation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface QuotationRepository {
    Quotation createQuotation(long customerId, long issuedByUserId, String quotationNumber);

    QuotationItem createItem(
            long quotationId,
            long productId,
            long variantId,
            String productNameSnapshot,
            int quantity,
            String selectedSize,
            String selectedColor,
            BigDecimal unitPriceSnapshot);

    List<QuotationSummary> findAll();

    Optional<QuotationDetail> findById(long quotationId);
}

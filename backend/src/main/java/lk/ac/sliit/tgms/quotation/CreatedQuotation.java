package lk.ac.sliit.tgms.quotation;

import java.math.BigDecimal;
import java.util.List;

public record CreatedQuotation(
        Quotation quotation,
        QuotationCustomerOption customer,
        List<QuotationItem> items) {

    public BigDecimal totalAmount() {
        return items.stream()
                .map(QuotationItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

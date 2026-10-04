package lk.ac.sliit.tgms.quotation;

public record CreateQuotationItemCommand(Long productId, Long variantId, Integer quantity) {}

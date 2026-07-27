package lk.ac.sliit.tgms.order;

/** Raw order-line input. TGMS-44 intentionally keeps nullable wrapper values so all
 * order-creation paths can be validated by the shared OrderValidator instead of
 * duplicating request validation in controllers. */
public record CreateOrderItemCommand(Long productId, Long variantId, Integer quantity) {}

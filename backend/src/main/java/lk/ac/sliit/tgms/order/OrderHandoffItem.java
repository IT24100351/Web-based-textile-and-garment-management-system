package lk.ac.sliit.tgms.order;

public record OrderHandoffItem(
        long orderItemId,
        long productId,
        long variantId,
        int quantity,
        String selectedSize,
        String selectedColor) {

    static OrderHandoffItem from(OrderDetailItem item) {
        return new OrderHandoffItem(
                item.id(),
                item.productId(),
                item.variantId(),
                item.quantity(),
                item.selectedSize(),
                item.selectedColor());
    }
}

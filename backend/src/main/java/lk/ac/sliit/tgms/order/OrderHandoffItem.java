package lk.ac.sliit.tgms.order;

public record OrderHandoffItem(
        long orderItemId,
        long productId,
        long variantId,
        String productName,
        String productImageUrl,
        int quantity,
        String selectedSize,
        String selectedColor) {

    static OrderHandoffItem from(OrderDetailItem item) {
        return new OrderHandoffItem(
                item.id(),
                item.productId(),
                item.variantId(),
                item.productName(),
                item.productImageUrl(),
                item.quantity(),
                item.selectedSize(),
                item.selectedColor());
    }
}

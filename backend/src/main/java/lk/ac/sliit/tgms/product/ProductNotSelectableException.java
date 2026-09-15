package lk.ac.sliit.tgms.product;

public class ProductNotSelectableException extends RuntimeException {

    public ProductNotSelectableException() {
        super("The selected product variant is not available for a new order.");
    }
}

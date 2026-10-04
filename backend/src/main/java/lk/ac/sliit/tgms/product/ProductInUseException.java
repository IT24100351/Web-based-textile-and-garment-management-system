package lk.ac.sliit.tgms.product;

public class ProductInUseException extends RuntimeException {

    public ProductInUseException() {
        super("This product cannot be deleted because it is already used by existing orders or quotations.");
    }
}

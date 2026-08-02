package lk.ac.sliit.tgms.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("The requested garment product was not found.");
    }
}

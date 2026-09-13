package lk.ac.sliit.tgms.product;

public class InactiveProductCategoryException extends RuntimeException {

    public InactiveProductCategoryException() {
        super("The selected category is inactive and cannot receive new products.");
    }
}

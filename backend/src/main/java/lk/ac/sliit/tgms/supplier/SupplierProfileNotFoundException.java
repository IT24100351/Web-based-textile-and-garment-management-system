package lk.ac.sliit.tgms.supplier;

public class SupplierProfileNotFoundException extends RuntimeException {

    public SupplierProfileNotFoundException() {
        super("No supplier profile has been created for this account.");
    }
}

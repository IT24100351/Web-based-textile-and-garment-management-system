package lk.ac.sliit.tgms.supplier;

public class MaterialSupplyCodeExistsException extends RuntimeException {

    public MaterialSupplyCodeExistsException() {
        super("This supplier already has a material supply with that code.");
    }
}

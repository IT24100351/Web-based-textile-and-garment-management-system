package lk.ac.sliit.tgms.admin;

public class LastAdministratorException extends RuntimeException {
    public LastAdministratorException() {
        super("At least one active administrator account must remain available.");
    }
}

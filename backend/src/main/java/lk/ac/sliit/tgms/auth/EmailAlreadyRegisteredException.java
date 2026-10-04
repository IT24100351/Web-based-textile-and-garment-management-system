package lk.ac.sliit.tgms.auth;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists.");
    }
}

package lk.ac.sliit.tgms.auth;

public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("The authenticated session is no longer valid.");
    }
}

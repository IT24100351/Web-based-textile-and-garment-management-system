package lk.ac.sliit.tgms.auth;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verify your email address before signing in.");
    }
}

package lk.ac.sliit.tgms.auth;

public class InvalidEmailVerificationException extends RuntimeException {

    public InvalidEmailVerificationException() {
        super("The verification code is invalid or expired. Request a new code and try again.");
    }
}

package lk.ac.sliit.tgms.auth;

public class InvalidPasswordResetException extends RuntimeException {

    public InvalidPasswordResetException() {
        super("This password reset link is invalid or has expired. Request a new reset link.");
    }
}

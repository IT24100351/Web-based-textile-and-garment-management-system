package lk.ac.sliit.tgms.auth;

public class PasswordPolicyException extends RuntimeException {

    public PasswordPolicyException() {
        super("Password must contain between 8 and 72 UTF-8 bytes.");
    }
}

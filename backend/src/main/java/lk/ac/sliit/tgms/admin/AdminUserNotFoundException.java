package lk.ac.sliit.tgms.admin;

public class AdminUserNotFoundException extends RuntimeException {
    public AdminUserNotFoundException() {
        super("User account was not found.");
    }
}

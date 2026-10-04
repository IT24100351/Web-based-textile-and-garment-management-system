package lk.ac.sliit.tgms.admin;

public class AdminSelfAccessException extends RuntimeException {
    public AdminSelfAccessException() {
        super("You cannot remove your own administrator access or deactivate your own account.");
    }
}

package lk.ac.sliit.tgms.auth;

class PasswordResetRequestProcessingException extends RuntimeException {

    PasswordResetRequestProcessingException(Throwable cause) {
        super("Password reset request processing failed.", cause);
    }
}

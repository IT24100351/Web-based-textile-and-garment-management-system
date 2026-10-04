package lk.ac.sliit.tgms.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MINIMUM_PASSWORD_BYTES = 8;
    private static final int MAXIMUM_PASSWORD_BYTES = 72;

    public void validate(String password) {
        if (!isBcryptCompatible(password)
                || password.getBytes(StandardCharsets.UTF_8).length < MINIMUM_PASSWORD_BYTES) {
            throw new PasswordPolicyException();
        }
    }

    public boolean isBcryptCompatible(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_PASSWORD_BYTES;
    }
}

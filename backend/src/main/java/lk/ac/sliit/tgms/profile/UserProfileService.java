package lk.ac.sliit.tgms.profile;

import java.text.Normalizer;
import java.time.Instant;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.auth.PasswordPolicy;
import lk.ac.sliit.tgms.auth.PasswordPolicyException;
import lk.ac.sliit.tgms.auth.PasswordResetTokenRepository;
import lk.ac.sliit.tgms.auth.UserAccount;
import lk.ac.sliit.tgms.auth.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public UserProfileService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    public UserAccount getProfile(long authenticatedUserId) {
        return requireActiveUser(authenticatedUserId);
    }

    @Transactional
    public UserAccount updateProfile(long authenticatedUserId, String fullName) {
        UserAccount current = requireActiveUser(authenticatedUserId);
        String normalizedName = normalizeName(fullName);
        return userAccountRepository.updateProfile(current.id(), normalizedName);
    }

    @Transactional
    public void changePassword(
            long authenticatedUserId, String currentPassword, String newPassword) {
        UserAccount current = requireActiveUser(authenticatedUserId);
        boolean currentPasswordMatches = passwordPolicy.isBcryptCompatible(currentPassword)
                && passwordEncoder.matches(currentPassword, current.passwordHash());
        if (!currentPasswordMatches) {
            throw new ProfilePasswordValidationException(
                    "currentPassword", "Current password is incorrect.");
        }

        try {
            passwordPolicy.validate(newPassword);
        } catch (PasswordPolicyException exception) {
            throw new ProfilePasswordValidationException(
                    "newPassword", exception.getMessage());
        }

        if (passwordEncoder.matches(newPassword, current.passwordHash())) {
            throw new ProfilePasswordValidationException(
                    "newPassword", "New password must be different from the current password.");
        }

        userAccountRepository.updatePasswordHash(
                current.id(), passwordEncoder.encode(newPassword));
        passwordResetTokenRepository.invalidateUnusedForUser(current.id(), Instant.now());
    }

    private UserAccount requireActiveUser(long userId) {
        return userAccountRepository.findById(userId)
                .filter(UserAccount::active)
                .orElseThrow(InvalidSessionException::new);
    }

    private String normalizeName(String fullName) {
        return Normalizer.normalize(fullName.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
    }
}

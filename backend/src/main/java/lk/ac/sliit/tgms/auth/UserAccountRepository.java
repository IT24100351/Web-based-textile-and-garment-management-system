package lk.ac.sliit.tgms.auth;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface UserAccountRepository {

    Optional<UserAccount> findByEmail(String normalizedEmail);

    Optional<UserAccount> findById(long id);

    Optional<UserAccountSecurityState> findSecurityStateById(long id);

    List<UserAccount> findActiveByRole(UserRole role, String search);

    UserAccount create(String fullName, String normalizedEmail, String passwordHash, UserRole role);

    UserAccount updateProfile(long userId, String fullName);

    void updatePasswordHash(long userId, String passwordHash);

    boolean isEmailVerified(long userId);

    void markEmailVerified(long userId, Instant verifiedAt);
}

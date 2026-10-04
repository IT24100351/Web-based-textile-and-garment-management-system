package lk.ac.sliit.tgms.admin;

import java.text.Normalizer;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import lk.ac.sliit.tgms.auth.AuthService;
import lk.ac.sliit.tgms.auth.InvalidSessionException;
import lk.ac.sliit.tgms.auth.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final int MAX_SEARCH_LENGTH = 120;

    private final AdminUserRepository adminUserRepository;
    private final AuthService authService;

    public AdminUserService(AdminUserRepository adminUserRepository, AuthService authService) {
        this.adminUserRepository = adminUserRepository;
        this.authService = authService;
    }

    public List<AdminUserAccount> listUsers(String search, UserRole role, Boolean active) {
        return adminUserRepository.findAll(
                new AdminUserQuery(normalizeSearch(search), role, active));
    }

    public AdminUserAccount getUser(long userId) {
        requirePositiveId(userId);
        return adminUserRepository.findById(userId).orElseThrow(AdminUserNotFoundException::new);
    }

    @Transactional
    public AdminUserAccount createInternalAccount(
            long actingAdministratorId,
            String fullName,
            String email,
            String password,
            UserRole role) {
        lockAndRequireActingAdministrator(actingAdministratorId);
        if (role == null || role == UserRole.CUSTOMER) {
            throw new AdminUserValidationException(
                    "Select an internal system role.",
                    Map.of("role", "Customer accounts must use public registration."));
        }
        var account = authService.createInternalAccount(fullName, email, password, role);
        return adminUserRepository.findById(account.id())
                .orElseThrow(() -> new IllegalStateException("Created user account could not be reloaded."));
    }

    @Transactional
    public AdminUserAccount changeRole(long actingAdministratorId, long userId, UserRole role) {
        requirePositiveId(actingAdministratorId);
        requirePositiveId(userId);
        if (role == null) {
            throw new AdminUserValidationException(
                    "A role is required.", Map.of("role", "Select a supported role."));
        }
        List<Long> activeAdministratorIds = lockAndRequireActingAdministrator(actingAdministratorId);
        if (actingAdministratorId == userId && role != UserRole.ADMINISTRATOR) {
            throw new AdminSelfAccessException();
        }

        AdminUserAccount target = adminUserRepository.findByIdForUpdate(userId)
                .orElseThrow(AdminUserNotFoundException::new);
        if (target.role() == UserRole.ADMINISTRATOR
                && target.active()
                && role != UserRole.ADMINISTRATOR
                && activeAdministratorIds.size() <= 1) {
            throw new LastAdministratorException();
        }
        if (target.role() != role) {
            adminUserRepository.updateRole(userId, role);
        }
        return adminUserRepository.findById(userId).orElseThrow(AdminUserNotFoundException::new);
    }

    @Transactional
    public AdminUserAccount changeActiveState(
            long actingAdministratorId, long userId, boolean active) {
        requirePositiveId(actingAdministratorId);
        requirePositiveId(userId);
        List<Long> activeAdministratorIds = lockAndRequireActingAdministrator(actingAdministratorId);
        if (actingAdministratorId == userId && !active) {
            throw new AdminSelfAccessException();
        }

        AdminUserAccount target = adminUserRepository.findByIdForUpdate(userId)
                .orElseThrow(AdminUserNotFoundException::new);
        if (target.role() == UserRole.ADMINISTRATOR
                && target.active()
                && !active
                && activeAdministratorIds.size() <= 1) {
            throw new LastAdministratorException();
        }
        if (target.active() != active) {
            adminUserRepository.updateActive(userId, active);
        }
        return adminUserRepository.findById(userId).orElseThrow(AdminUserNotFoundException::new);
    }

    private List<Long> lockAndRequireActingAdministrator(long actingAdministratorId) {
        requirePositiveId(actingAdministratorId);
        List<Long> activeAdministratorIds = adminUserRepository.lockActiveAdministratorIds();
        if (activeAdministratorIds.contains(actingAdministratorId)) {
            return activeAdministratorIds;
        }

        AdminUserAccount actor = adminUserRepository.findByIdForUpdate(actingAdministratorId)
                .orElseThrow(InvalidSessionException::new);
        if (!actor.active()) {
            throw new InvalidSessionException();
        }
        throw new AccessDeniedException("Administrator permission is no longer available.");
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(search.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new AdminUserValidationException(
                    "Search is too long.",
                    Map.of("search", "Search must not exceed 120 characters."));
        }
        return normalized;
    }

    private void requirePositiveId(long userId) {
        if (userId <= 0) {
            throw new AdminUserValidationException(
                    "User ID must be positive.",
                    Map.of("userId", "User ID must be a positive integer."));
        }
    }
}

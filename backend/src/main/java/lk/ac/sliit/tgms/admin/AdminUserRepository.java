package lk.ac.sliit.tgms.admin;

import java.util.List;
import java.util.Optional;
import lk.ac.sliit.tgms.auth.UserRole;

public interface AdminUserRepository {

    List<AdminUserAccount> findAll(AdminUserQuery query);

    Optional<AdminUserAccount> findById(long userId);

    Optional<AdminUserAccount> findByIdForUpdate(long userId);

    List<Long> lockActiveAdministratorIds();

    void updateRole(long userId, UserRole role);

    void updateActive(long userId, boolean active);
}

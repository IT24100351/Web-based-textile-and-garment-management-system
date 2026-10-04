package lk.ac.sliit.tgms.admin;

import java.time.Instant;
import lk.ac.sliit.tgms.auth.UserRole;

public record AdminUserAccount(
        long id,
        String email,
        String fullName,
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}

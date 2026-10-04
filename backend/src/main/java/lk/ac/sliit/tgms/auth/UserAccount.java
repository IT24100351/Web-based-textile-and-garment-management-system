package lk.ac.sliit.tgms.auth;

public record UserAccount(
        long id,
        String email,
        String passwordHash,
        String fullName,
        UserRole role,
        boolean active) {}

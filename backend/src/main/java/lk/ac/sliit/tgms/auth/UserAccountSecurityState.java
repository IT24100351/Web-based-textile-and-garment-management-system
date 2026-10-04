package lk.ac.sliit.tgms.auth;

public record UserAccountSecurityState(
        long id, UserRole role, boolean active, int sessionVersion) {}

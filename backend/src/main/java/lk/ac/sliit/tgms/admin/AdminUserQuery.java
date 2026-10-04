package lk.ac.sliit.tgms.admin;

import lk.ac.sliit.tgms.auth.UserRole;

public record AdminUserQuery(String search, UserRole role, Boolean active) {}

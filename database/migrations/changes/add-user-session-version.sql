--liquibase formatted sql

--changeset tgms:20260923090000-add-user-session-version logicalFilePath:db/changelog/changes/add-user-session-version.sql
--comment: Add a monotonic version that invalidates existing JWT sessions after a password change or reset.
ALTER TABLE users
    ADD COLUMN session_version INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_session_version CHECK (session_version >= 0);

--rollback ALTER TABLE users DROP CONSTRAINT ck_users_session_version;
--rollback ALTER TABLE users DROP COLUMN session_version;

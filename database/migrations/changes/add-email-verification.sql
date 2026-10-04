--liquibase formatted sql

--changeset tgms:20260830080000-add-email-verification logicalFilePath:db/changelog/changes/add-email-verification.sql
--comment: Add email-verification status and secure, expiring verification codes for user accounts.
ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6);

UPDATE users
SET email_verified_at = CURRENT_TIMESTAMP(6)
WHERE email_verified_at IS NULL;

CREATE TABLE email_verification_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_salt CHAR(32) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_email_verification_codes PRIMARY KEY (id),
    CONSTRAINT fk_email_verification_codes_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT ck_email_verification_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE INDEX idx_email_verification_codes_user_open
    ON email_verification_codes (user_id, used_at, expires_at);

--rollback DROP TABLE email_verification_codes;
--rollback ALTER TABLE users DROP COLUMN email_verified_at;

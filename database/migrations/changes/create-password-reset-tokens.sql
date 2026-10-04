--liquibase formatted sql

--changeset tgms:20260826113000-create-password-reset-tokens logicalFilePath:db/changelog/changes/20260826113000_create_password_reset_tokens.sql
--comment: Store hashed, expiring, single-use password reset tokens without persisting raw reset secrets.
CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user_open
    ON password_reset_tokens (user_id, used_at, expires_at);

--rollback DROP TABLE password_reset_tokens;

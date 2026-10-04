--liquibase formatted sql

--changeset tgms:20260822181000-create-users logicalFilePath:db/changelog/changes/20260822181000_create_users.sql
--comment: Create the shared account source of truth for customer and internal-user authentication.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (
        role IN (
            'ADMINISTRATOR',
            'SUPPLIER',
            'INVENTORY_MANAGER',
            'PRODUCTION_MANAGER',
            'SALES_OFFICER',
            'CUSTOMER'
        )
    )
);

--rollback DROP TABLE users;

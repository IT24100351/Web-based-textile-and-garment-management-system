--liquibase formatted sql

--changeset tgms:20260823120500-create-supplier-profiles logicalFilePath:db/changelog/changes/20260823120500_create_supplier_profiles.sql
--comment: Create supplier identity and contact profiles linked to authenticated user accounts.
CREATE TABLE supplier_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    business_name VARCHAR(160) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    address VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_supplier_profiles PRIMARY KEY (id),
    CONSTRAINT uq_supplier_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_supplier_profiles_user_id FOREIGN KEY (user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_supplier_profiles_business_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(business_name)) > 0),
    CONSTRAINT ck_supplier_profiles_contact_phone_not_blank
        CHECK (CHAR_LENGTH(TRIM(contact_phone)) > 0),
    CONSTRAINT ck_supplier_profiles_address_not_blank
        CHECK (CHAR_LENGTH(TRIM(address)) > 0)
);

--rollback DROP TABLE supplier_profiles;

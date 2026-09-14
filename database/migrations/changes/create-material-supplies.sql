--liquibase formatted sql

--changeset tgms:20260823123000-create-material-supplies logicalFilePath:db/changelog/changes/20260823123000_create_material_supplies.sql
--comment: Create supplier-owned material supply records for later Inventory references.
CREATE TABLE material_supplies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id BIGINT NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_description VARCHAR(500) NULL,
    quantity DECIMAL(14, 3) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    delivery_lead_time_days INT NOT NULL,
    delivery_notes VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_material_supplies PRIMARY KEY (id),
    CONSTRAINT fk_material_supplies_supplier_id FOREIGN KEY (supplier_id)
        REFERENCES supplier_profiles (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_material_supplies_supplier_code UNIQUE (supplier_id, material_code),
    CONSTRAINT ck_material_supplies_code_not_blank
        CHECK (CHAR_LENGTH(TRIM(material_code)) > 0),
    CONSTRAINT ck_material_supplies_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(material_name)) > 0),
    CONSTRAINT ck_material_supplies_description_not_blank
        CHECK (material_description IS NULL OR CHAR_LENGTH(TRIM(material_description)) > 0),
    CONSTRAINT ck_material_supplies_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT ck_material_supplies_unit_not_blank
        CHECK (CHAR_LENGTH(TRIM(unit_of_measure)) > 0),
    CONSTRAINT ck_material_supplies_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_material_supplies_delivery_days_non_negative
        CHECK (delivery_lead_time_days >= 0),
    CONSTRAINT ck_material_supplies_delivery_notes_not_blank
        CHECK (delivery_notes IS NULL OR CHAR_LENGTH(TRIM(delivery_notes)) > 0),
    CONSTRAINT ck_material_supplies_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

CREATE INDEX idx_material_supplies_material_name ON material_supplies (material_name);
CREATE INDEX idx_material_supplies_status ON material_supplies (status);

--rollback DROP TABLE material_supplies;

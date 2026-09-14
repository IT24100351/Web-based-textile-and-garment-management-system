--liquibase formatted sql

--changeset tgms:20260823134500-create-inventory-materials logicalFilePath:db/changelog/changes/20260823134500_create_inventory_materials.sql
--comment: Create the fabric and raw-material inventory source-of-truth table.
CREATE TABLE inventory_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_material_supply_id BIGINT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(160) NOT NULL,
    material_description VARCHAR(500) NULL,
    material_type VARCHAR(24) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    current_quantity DECIMAL(14, 3) NOT NULL DEFAULT 0.000,
    low_stock_threshold DECIMAL(14, 3) NOT NULL DEFAULT 0.000,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_inventory_materials PRIMARY KEY (id),
    CONSTRAINT fk_inventory_materials_source_supply_id FOREIGN KEY (source_material_supply_id)
        REFERENCES material_supplies (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_inventory_materials_material_code UNIQUE (material_code),
    CONSTRAINT ck_inventory_materials_code_not_blank
        CHECK (CHAR_LENGTH(TRIM(material_code)) > 0),
    CONSTRAINT ck_inventory_materials_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(material_name)) > 0),
    CONSTRAINT ck_inventory_materials_description_not_blank
        CHECK (material_description IS NULL OR CHAR_LENGTH(TRIM(material_description)) > 0),
    CONSTRAINT ck_inventory_materials_type
        CHECK (material_type IN ('FABRIC', 'RAW_MATERIAL')),
    CONSTRAINT ck_inventory_materials_unit_not_blank
        CHECK (CHAR_LENGTH(TRIM(unit_of_measure)) > 0),
    CONSTRAINT ck_inventory_materials_current_quantity_non_negative
        CHECK (current_quantity >= 0),
    CONSTRAINT ck_inventory_materials_low_stock_threshold_non_negative
        CHECK (low_stock_threshold >= 0),
    CONSTRAINT ck_inventory_materials_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

CREATE INDEX idx_inventory_materials_source_supply_id
    ON inventory_materials (source_material_supply_id);
CREATE INDEX idx_inventory_materials_type_status
    ON inventory_materials (material_type, status);
CREATE INDEX idx_inventory_materials_material_name
    ON inventory_materials (material_name);

--rollback DROP TABLE inventory_materials;

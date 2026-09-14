--liquibase formatted sql

--changeset tgms:20260823203000-create-production-task-material-requirements logicalFilePath:db/changelog/changes/20260823203000_create_production_task_material_requirements.sql
--comment: Link Production tasks to required Inventory materials without duplicating Inventory source-of-truth data.
CREATE TABLE production_task_material_requirements (
    production_task_id BIGINT NOT NULL,
    inventory_material_id BIGINT NOT NULL,
    required_quantity DECIMAL(14, 3) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_production_task_material_requirements PRIMARY KEY (
        production_task_id,
        inventory_material_id
    ),
    CONSTRAINT fk_production_task_material_requirements_task_id FOREIGN KEY (production_task_id)
        REFERENCES production_tasks (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_production_task_material_requirements_inventory_id FOREIGN KEY (inventory_material_id)
        REFERENCES inventory_materials (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_production_task_material_requirements_quantity CHECK (
        required_quantity > 0.000
    )
);

CREATE INDEX idx_production_task_material_requirements_inventory_id
    ON production_task_material_requirements (inventory_material_id);

--rollback DROP TABLE production_task_material_requirements;

--liquibase formatted sql

--changeset tgms:20260823211500-create-production-task-material-usage logicalFilePath:db/changelog/changes/20260823211500_create_production_task_material_usage.sql
--comment: Record idempotent Production material usage while preserving the Inventory-owned stock source of truth.
CREATE TABLE production_task_material_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    production_task_id BIGINT NOT NULL,
    inventory_material_id BIGINT NOT NULL,
    quantity_used DECIMAL(14, 3) NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_production_task_material_usage PRIMARY KEY (id),
    CONSTRAINT uq_production_task_material_usage_task_material UNIQUE (
        production_task_id,
        inventory_material_id
    ),
    CONSTRAINT fk_production_task_material_usage_requirement FOREIGN KEY (
        production_task_id,
        inventory_material_id
    ) REFERENCES production_task_material_requirements (
        production_task_id,
        inventory_material_id
    ) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_production_task_material_usage_recorded_by FOREIGN KEY (recorded_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_production_task_material_usage_quantity CHECK (
        quantity_used > 0.000
    )
);

CREATE INDEX idx_production_task_material_usage_inventory_id
    ON production_task_material_usage (inventory_material_id, recorded_at);
CREATE INDEX idx_production_task_material_usage_recorded_by
    ON production_task_material_usage (recorded_by_user_id, recorded_at);

--rollback DROP TABLE production_task_material_usage;

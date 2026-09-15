--liquibase formatted sql

--changeset tgms:20260823183600-create-production-tasks logicalFilePath:db/changelog/changes/20260823183600_create_production_tasks.sql
--comment: Create the Production Management task source of truth linked to valid customer orders.
CREATE TABLE production_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_number VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_production_tasks PRIMARY KEY (id),
    CONSTRAINT uq_production_tasks_task_number UNIQUE (task_number),
    CONSTRAINT fk_production_tasks_order_id FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_production_tasks_task_number_not_blank CHECK (
        CHAR_LENGTH(TRIM(task_number)) > 0
    ),
    CONSTRAINT ck_production_tasks_status CHECK (
        status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')
    ),
    CONSTRAINT ck_production_tasks_status_timestamps CHECK (
        (status = 'PENDING' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'IN_PROGRESS' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND started_at IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_production_tasks_completion_after_start CHECK (
        completed_at IS NULL OR completed_at >= started_at
    )
);

CREATE INDEX idx_production_tasks_order_id
    ON production_tasks (order_id);
CREATE INDEX idx_production_tasks_status_created_at
    ON production_tasks (status, created_at);

--rollback DROP TABLE production_tasks;

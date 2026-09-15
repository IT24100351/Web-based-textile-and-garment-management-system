--liquibase formatted sql

--changeset tgms:20260823222500-add-production-quality-control logicalFilePath:db/changelog/changes/20260823222500_add_production_quality_control.sql
--comment: Add a simple Production-owned quality-control result to each production task.
ALTER TABLE production_tasks
    ADD COLUMN quality_control_result VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE production_tasks
    ADD COLUMN quality_checked_by_user_id BIGINT NULL;
ALTER TABLE production_tasks
    ADD COLUMN quality_checked_at TIMESTAMP(6) NULL;
ALTER TABLE production_tasks
    ADD CONSTRAINT fk_production_tasks_quality_checked_by FOREIGN KEY (quality_checked_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT;
ALTER TABLE production_tasks
    ADD CONSTRAINT ck_production_tasks_quality_control_result CHECK (
        quality_control_result IN ('PENDING', 'PASSED', 'FAILED')
    );
ALTER TABLE production_tasks
    ADD CONSTRAINT ck_production_tasks_quality_control_consistency CHECK (
        (quality_control_result = 'PENDING' AND quality_checked_by_user_id IS NULL AND quality_checked_at IS NULL)
        OR (quality_control_result IN ('PASSED', 'FAILED')
            AND quality_checked_by_user_id IS NOT NULL
            AND quality_checked_at IS NOT NULL)
    );

CREATE INDEX idx_production_tasks_quality_status
    ON production_tasks (quality_control_result, status);

--rollback DROP INDEX idx_production_tasks_quality_status ON production_tasks;
--rollback ALTER TABLE production_tasks DROP CONSTRAINT ck_production_tasks_quality_control_consistency;
--rollback ALTER TABLE production_tasks DROP CONSTRAINT ck_production_tasks_quality_control_result;
--rollback ALTER TABLE production_tasks DROP CONSTRAINT fk_production_tasks_quality_checked_by;
--rollback ALTER TABLE production_tasks DROP COLUMN quality_checked_at;
--rollback ALTER TABLE production_tasks DROP COLUMN quality_checked_by_user_id;
--rollback ALTER TABLE production_tasks DROP COLUMN quality_control_result;

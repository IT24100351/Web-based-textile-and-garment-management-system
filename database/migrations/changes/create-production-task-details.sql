--liquibase formatted sql

--changeset tgms:20260823195600-create-production-task-details logicalFilePath:db/changelog/changes/20260823195600_create_production_task_details.sql
--comment: Store Production Manager work details and simple work assignment for production tasks.
CREATE TABLE production_task_details (
    production_task_id BIGINT NOT NULL,
    work_details VARCHAR(1000) NOT NULL,
    work_assignment VARCHAR(255) NOT NULL,
    work_notes VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_production_task_details PRIMARY KEY (production_task_id),
    CONSTRAINT fk_production_task_details_task_id FOREIGN KEY (production_task_id)
        REFERENCES production_tasks (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_production_task_details_work_details_not_blank CHECK (
        CHAR_LENGTH(TRIM(work_details)) > 0
    ),
    CONSTRAINT ck_production_task_details_work_assignment_not_blank CHECK (
        CHAR_LENGTH(TRIM(work_assignment)) > 0
    ),
    CONSTRAINT ck_production_task_details_work_notes_not_blank CHECK (
        work_notes IS NULL OR CHAR_LENGTH(TRIM(work_notes)) > 0
    )
);

--rollback DROP TABLE production_task_details;

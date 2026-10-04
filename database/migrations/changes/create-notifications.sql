--liquibase formatted sql

--changeset tgms:20260826160000-create-notifications logicalFilePath:db/changelog/changes/20260826160000_create_notifications.sql
--comment: Create recipient-scoped in-system notifications as supporting records for operational events.
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    source_module VARCHAR(32) NOT NULL,
    source_record_id BIGINT NOT NULL,
    read_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient_user_id FOREIGN KEY (recipient_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_kind CHECK (
        kind IN ('LOW_STOCK', 'ORDER_STATUS', 'PRODUCTION_STATUS', 'DELIVERY_STATUS')
    ),
    CONSTRAINT ck_notifications_source_module CHECK (
        source_module IN ('INVENTORY', 'ORDER', 'PRODUCTION', 'DELIVERY')
    ),
    CONSTRAINT ck_notifications_title_not_blank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_notifications_message_not_blank CHECK (CHAR_LENGTH(TRIM(message)) > 0),
    CONSTRAINT ck_notifications_source_record_id_positive CHECK (source_record_id > 0)
);

CREATE INDEX idx_notifications_recipient_created_at
    ON notifications (recipient_user_id, created_at, id);
CREATE INDEX idx_notifications_recipient_read_created_at
    ON notifications (recipient_user_id, read_at, created_at, id);
CREATE INDEX idx_notifications_source
    ON notifications (source_module, source_record_id, created_at);

--rollback DROP TABLE notifications;

--liquibase formatted sql

--changeset tgms:20260823170000-create-order-status-history logicalFilePath:db/changelog/changes/20260823170000_create_order_status_history.sql
--comment: Record controlled customer-order status transitions for audit and future Production/Delivery synchronization.
CREATE TABLE order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order_id FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_status_history_changed_by FOREIGN KEY (changed_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_order_status_history_from_status CHECK (
        from_status IN (
            'PENDING',
            'CONFIRMED',
            'IN_PRODUCTION',
            'READY_FOR_DELIVERY',
            'COMPLETED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_order_status_history_to_status CHECK (
        to_status IN (
            'PENDING',
            'CONFIRMED',
            'IN_PRODUCTION',
            'READY_FOR_DELIVERY',
            'COMPLETED',
            'CANCELLED'
        )
    ),
    CONSTRAINT ck_order_status_history_status_changed CHECK (from_status <> to_status)
);

CREATE INDEX idx_order_status_history_order_changed_at
    ON order_status_history (order_id, changed_at);

--rollback DROP TABLE order_status_history;

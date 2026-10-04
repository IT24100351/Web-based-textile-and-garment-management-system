--liquibase formatted sql

--changeset tgms:20260824053500-create-deliveries logicalFilePath:db/changelog/changes/20260824053500_create_deliveries.sql
--comment: Create the Delivery Management record source of truth linked to valid customer orders.
CREATE TABLE deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_number VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP(6) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    delivery_notes VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_deliveries PRIMARY KEY (id),
    CONSTRAINT uq_deliveries_delivery_number UNIQUE (delivery_number),
    CONSTRAINT uq_deliveries_order_id UNIQUE (order_id),
    CONSTRAINT fk_deliveries_order_id FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_deliveries_delivery_number_not_blank CHECK (
        CHAR_LENGTH(TRIM(delivery_number)) > 0
    ),
    CONSTRAINT ck_deliveries_delivery_address_not_blank CHECK (
        CHAR_LENGTH(TRIM(delivery_address)) > 0
    ),
    CONSTRAINT ck_deliveries_status CHECK (
        status IN ('SCHEDULED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED')
    )
);

CREATE INDEX idx_deliveries_status_scheduled_at
    ON deliveries (status, scheduled_at);

--rollback DROP TABLE deliveries;

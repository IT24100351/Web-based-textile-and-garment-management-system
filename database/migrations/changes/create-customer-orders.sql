--liquibase formatted sql

--changeset tgms:20260823155500-create-customer-orders logicalFilePath:db/changelog/changes/20260823155500_create_customer_orders.sql
--comment: Create the customer order header source of truth for the Order Management lifecycle.
CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    order_number VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_customer_id FOREIGN KEY (customer_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_orders_order_number_not_blank CHECK (CHAR_LENGTH(TRIM(order_number)) > 0),
    CONSTRAINT ck_orders_status CHECK (
        status IN (
            'PENDING',
            'CONFIRMED',
            'IN_PRODUCTION',
            'READY_FOR_DELIVERY',
            'COMPLETED',
            'CANCELLED'
        )
    )
);

CREATE INDEX idx_orders_customer_id_created_at
    ON orders (customer_id, created_at);
CREATE INDEX idx_orders_status_created_at
    ON orders (status, created_at);

--rollback DROP TABLE orders;

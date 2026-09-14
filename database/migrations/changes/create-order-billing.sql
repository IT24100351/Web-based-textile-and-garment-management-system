--liquibase formatted sql

--changeset tgms:20260823180500-create-order-billing logicalFilePath:db/changelog/changes/20260823180500_create_order_billing.sql
--comment: Add immutable order invoices and manual payment-record state without any third-party payment gateway.
CREATE TABLE order_invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    invoice_number VARCHAR(32) NOT NULL,
    total_amount DECIMAL(14, 2) NOT NULL,
    issued_by_user_id BIGINT NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_invoices PRIMARY KEY (id),
    CONSTRAINT uq_order_invoices_order_id UNIQUE (order_id),
    CONSTRAINT uq_order_invoices_invoice_number UNIQUE (invoice_number),
    CONSTRAINT fk_order_invoices_order_id FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_invoices_issued_by FOREIGN KEY (issued_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_order_invoices_number_not_blank CHECK (CHAR_LENGTH(TRIM(invoice_number)) > 0),
    CONSTRAINT ck_order_invoices_total_positive CHECK (total_amount > 0.00)
);

CREATE UNIQUE INDEX uq_order_invoices_order_id_id
    ON order_invoices (order_id, id);

CREATE TABLE order_payment_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    invoice_id BIGINT NOT NULL,
    payment_status VARCHAR(24) NOT NULL DEFAULT 'UNPAID',
    amount_paid DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(24) NULL,
    payment_reference VARCHAR(120) NULL,
    note VARCHAR(500) NULL,
    recorded_by_user_id BIGINT NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_payment_records PRIMARY KEY (id),
    CONSTRAINT uq_order_payment_records_order_id UNIQUE (order_id),
    CONSTRAINT uq_order_payment_records_invoice_id UNIQUE (invoice_id),
    CONSTRAINT fk_order_payment_records_order_id FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_payment_records_order_invoice FOREIGN KEY (order_id, invoice_id)
        REFERENCES order_invoices (order_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_payment_records_recorded_by FOREIGN KEY (recorded_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_order_payment_records_status CHECK (
        payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')
    ),
    CONSTRAINT ck_order_payment_records_amount_non_negative CHECK (amount_paid >= 0.00),
    CONSTRAINT ck_order_payment_records_method CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'OTHER')
    )
);

CREATE INDEX idx_order_invoices_issued_at ON order_invoices (issued_at);
CREATE INDEX idx_order_payment_records_status ON order_payment_records (payment_status, updated_at);

--rollback DROP TABLE order_payment_records;
--rollback DROP TABLE order_invoices;

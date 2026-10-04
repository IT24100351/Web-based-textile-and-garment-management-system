--liquibase formatted sql

--changeset tgms:20260823173800-create-customer-quotations logicalFilePath:db/changelog/changes/20260823173800_create_customer_quotations.sql
--comment: Create immutable issued customer quotations and line snapshots for Sales Officer quotation preparation.
CREATE TABLE quotations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quotation_number VARCHAR(32) NOT NULL,
    customer_id BIGINT NOT NULL,
    issued_by_user_id BIGINT NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_quotations PRIMARY KEY (id),
    CONSTRAINT uq_quotations_quotation_number UNIQUE (quotation_number),
    CONSTRAINT fk_quotations_customer_id FOREIGN KEY (customer_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quotations_issued_by_user_id FOREIGN KEY (issued_by_user_id)
        REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_quotations_number_not_blank CHECK (CHAR_LENGTH(TRIM(quotation_number)) > 0)
);

CREATE TABLE quotation_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    quotation_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    product_name_snapshot VARCHAR(160) NOT NULL,
    quantity INT NOT NULL,
    selected_size VARCHAR(32) NOT NULL,
    selected_color VARCHAR(64) NOT NULL,
    unit_price_snapshot DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_quotation_items PRIMARY KEY (id),
    CONSTRAINT fk_quotation_items_quotation_id FOREIGN KEY (quotation_id)
        REFERENCES quotations (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quotation_items_product_id FOREIGN KEY (product_id)
        REFERENCES garment_products (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_quotation_items_product_variant FOREIGN KEY (product_id, variant_id)
        REFERENCES garment_product_variants (product_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_quotation_items_product_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(product_name_snapshot)) > 0),
    CONSTRAINT ck_quotation_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_quotation_items_selected_size_not_blank
        CHECK (CHAR_LENGTH(TRIM(selected_size)) > 0),
    CONSTRAINT ck_quotation_items_selected_color_not_blank
        CHECK (CHAR_LENGTH(TRIM(selected_color)) > 0),
    CONSTRAINT ck_quotation_items_price_positive CHECK (unit_price_snapshot > 0.00)
);

CREATE INDEX idx_quotations_customer_issued_at ON quotations (customer_id, issued_at);
CREATE INDEX idx_quotations_issued_at ON quotations (issued_at);
CREATE INDEX idx_quotation_items_quotation_id ON quotation_items (quotation_id);

--rollback DROP TABLE quotation_items;
--rollback DROP TABLE quotations;

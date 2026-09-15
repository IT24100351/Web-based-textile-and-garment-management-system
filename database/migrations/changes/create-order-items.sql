--liquibase formatted sql

--changeset tgms:20260823160200-create-order-items logicalFilePath:db/changelog/changes/20260823160200_create_order_items.sql
--validCheckSum: 9:8327298f823fcee2c3830023edcf9368
--comment: Create customer order line items linked to the Product catalog with immutable order-time selection and price snapshots.
ALTER TABLE garment_product_variants
    ADD CONSTRAINT uq_garment_product_variants_product_id_id
    UNIQUE (product_id, id);

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected_size VARCHAR(32) NOT NULL,
    selected_color VARCHAR(64) NOT NULL,
    unit_price_snapshot DECIMAL(12, 2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id)
        REFERENCES orders (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_product_id FOREIGN KEY (product_id)
        REFERENCES garment_products (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_product_variant FOREIGN KEY (product_id, variant_id)
        REFERENCES garment_product_variants (product_id, id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_selected_size_not_blank
        CHECK (CHAR_LENGTH(TRIM(selected_size)) > 0),
    CONSTRAINT ck_order_items_selected_color_not_blank
        CHECK (CHAR_LENGTH(TRIM(selected_color)) > 0),
    CONSTRAINT ck_order_items_unit_price_snapshot_positive
        CHECK (unit_price_snapshot > 0.00)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_variant ON order_items (product_id, variant_id);

--rollback DROP TABLE order_items;
--rollback ALTER TABLE garment_product_variants DROP CONSTRAINT uq_garment_product_variants_product_id_id;

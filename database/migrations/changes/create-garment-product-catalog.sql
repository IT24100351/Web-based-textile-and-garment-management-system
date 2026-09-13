--liquibase formatted sql

--changeset tgms:20260823105000-create-garment-product-catalog logicalFilePath:db/changelog/changes/20260823105000_create_garment_product_catalog.sql
--comment: Create the garment category, product, and sellable variant source-of-truth tables.
CREATE TABLE garment_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_garment_categories PRIMARY KEY (id),
    CONSTRAINT uq_garment_categories_name UNIQUE (name),
    CONSTRAINT ck_garment_categories_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_garment_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE garment_products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_garment_products PRIMARY KEY (id),
    CONSTRAINT fk_garment_products_category_id FOREIGN KEY (category_id)
        REFERENCES garment_categories (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_garment_products_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_garment_products_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED')
    )
);

CREATE INDEX idx_garment_products_category_id_status
    ON garment_products (category_id, status);
CREATE INDEX idx_garment_products_name ON garment_products (name);

CREATE TABLE garment_product_variants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    size VARCHAR(32) NOT NULL,
    color VARCHAR(64) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_garment_product_variants PRIMARY KEY (id),
    CONSTRAINT fk_garment_product_variants_product_id FOREIGN KEY (product_id)
        REFERENCES garment_products (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT uq_garment_product_variants_product_size_color
        UNIQUE (product_id, size, color),
    CONSTRAINT ck_garment_product_variants_size_not_blank
        CHECK (CHAR_LENGTH(TRIM(size)) > 0),
    CONSTRAINT ck_garment_product_variants_color_not_blank
        CHECK (CHAR_LENGTH(TRIM(color)) > 0),
    CONSTRAINT ck_garment_product_variants_price_positive CHECK (price > 0.00),
    CONSTRAINT ck_garment_product_variants_status CHECK (
        status IN ('AVAILABLE', 'UNAVAILABLE', 'DISCONTINUED')
    )
);

CREATE INDEX idx_garment_product_variants_product_id_status
    ON garment_product_variants (product_id, status);

--rollback DROP TABLE garment_product_variants;
--rollback DROP TABLE garment_products;
--rollback DROP TABLE garment_categories;

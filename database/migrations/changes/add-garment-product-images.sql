--liquibase formatted sql

--changeset tgms:add-garment-product-images logicalFilePath:db/changelog/changes/add-garment-product-images.sql
--comment: Store an optional database-driven image path or HTTPS URL for each garment product.
ALTER TABLE garment_products
    ADD COLUMN image_url VARCHAR(500) NULL AFTER description;

ALTER TABLE garment_products
    ADD CONSTRAINT ck_garment_products_image_url_not_blank CHECK (
        image_url IS NULL OR CHAR_LENGTH(TRIM(image_url)) > 0
    );

--rollback ALTER TABLE garment_products DROP CONSTRAINT ck_garment_products_image_url_not_blank;
--rollback ALTER TABLE garment_products DROP COLUMN image_url;

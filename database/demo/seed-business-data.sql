-- Supplemental realistic TGMS business data.
-- This script creates no users. It reuses active users already present in each role.
START TRANSACTION;

SET @supplier_user_id := (
    SELECT id FROM users
    WHERE role = 'SUPPLIER' AND is_active = TRUE
    ORDER BY CASE WHEN email = 'demo.supplier@tgms.example' THEN 0 ELSE 1 END, id
    LIMIT 1
);
SET @inventory_user_id := (
    SELECT id FROM users
    WHERE role = 'INVENTORY_MANAGER' AND is_active = TRUE
    ORDER BY CASE WHEN email = 'demo.inventory@tgms.example' THEN 0 ELSE 1 END, id
    LIMIT 1
);
SET @production_user_id := (
    SELECT id FROM users
    WHERE role = 'PRODUCTION_MANAGER' AND is_active = TRUE
    ORDER BY CASE WHEN email = 'demo.production@tgms.example' THEN 0 ELSE 1 END, id
    LIMIT 1
);
SET @sales_user_id := (
    SELECT id FROM users
    WHERE role = 'SALES_OFFICER' AND is_active = TRUE
    ORDER BY CASE WHEN email = 'demo.sales@tgms.example' THEN 0 ELSE 1 END, id
    LIMIT 1
);
SET @customer_user_id := (
    SELECT id FROM users
    WHERE role = 'CUSTOMER' AND is_active = TRUE
    ORDER BY CASE WHEN email = 'demo.customer@tgms.example' THEN 0 ELSE 1 END, id
    LIMIT 1
);

DROP TEMPORARY TABLE IF EXISTS seed_required_users;
CREATE TEMPORARY TABLE seed_required_users (
    supplier_user_id BIGINT NOT NULL,
    inventory_user_id BIGINT NOT NULL,
    production_user_id BIGINT NOT NULL,
    sales_user_id BIGINT NOT NULL,
    customer_user_id BIGINT NOT NULL
);
INSERT INTO seed_required_users VALUES (
    @supplier_user_id,
    @inventory_user_id,
    @production_user_id,
    @sales_user_id,
    @customer_user_id
);

-- Idempotent cleanup for this supplemental seed only.
DELETE FROM notifications
WHERE (source_module = 'INVENTORY' AND source_record_id IN (
    SELECT id FROM inventory_materials WHERE material_code LIKE 'SEED-INV-%'
)) OR (source_module = 'ORDER' AND source_record_id IN (
    SELECT id FROM orders WHERE order_number LIKE 'SEED-ORD-%'
)) OR (source_module = 'PRODUCTION' AND source_record_id IN (
    SELECT id FROM production_tasks WHERE task_number LIKE 'SEED-PRD-%'
)) OR (source_module = 'DELIVERY' AND source_record_id IN (
    SELECT id FROM deliveries WHERE delivery_number LIKE 'SEED-DEL-%'
));

DELETE FROM deliveries
WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'SEED-ORD-%');
DELETE FROM production_task_material_usage
WHERE production_task_id IN (SELECT id FROM production_tasks WHERE task_number LIKE 'SEED-PRD-%');
DELETE FROM production_task_material_requirements
WHERE production_task_id IN (SELECT id FROM production_tasks WHERE task_number LIKE 'SEED-PRD-%');
DELETE FROM production_task_details
WHERE production_task_id IN (SELECT id FROM production_tasks WHERE task_number LIKE 'SEED-PRD-%');
DELETE FROM production_tasks WHERE task_number LIKE 'SEED-PRD-%';
DELETE FROM order_payment_records
WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'SEED-ORD-%');
DELETE FROM order_invoices
WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'SEED-ORD-%');
DELETE FROM order_status_history
WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'SEED-ORD-%');
DELETE FROM order_items
WHERE order_id IN (SELECT id FROM orders WHERE order_number LIKE 'SEED-ORD-%');
DELETE FROM orders WHERE order_number LIKE 'SEED-ORD-%';
DELETE FROM quotation_items
WHERE quotation_id IN (SELECT id FROM quotations WHERE quotation_number LIKE 'SEED-QUO-%');
DELETE FROM quotations WHERE quotation_number LIKE 'SEED-QUO-%';
DELETE FROM inventory_materials WHERE material_code LIKE 'SEED-INV-%';
DELETE FROM material_supplies WHERE material_code LIKE 'SEED-%';
DELETE FROM garment_product_variants
WHERE product_id IN (
    SELECT p.id FROM garment_products p
    JOIN garment_categories c ON c.id = p.category_id
    WHERE c.description LIKE '%[BUSINESS_SEED]%'
);
DELETE p FROM garment_products p
JOIN garment_categories c ON c.id = p.category_id
WHERE c.description LIKE '%[BUSINESS_SEED]%';
DELETE FROM garment_categories WHERE description LIKE '%[BUSINESS_SEED]%';

INSERT INTO supplier_profiles (user_id, business_name, contact_phone, address)
SELECT @supplier_user_id, 'Ceylon Weave & Trims (Pvt) Ltd', '+94 11 274 6182',
       'No. 18, Export Processing Zone Road, Biyagama, Sri Lanka'
WHERE NOT EXISTS (
    SELECT 1 FROM supplier_profiles WHERE user_id = @supplier_user_id
);

SET @supplier_profile_id := (
    SELECT id FROM supplier_profiles WHERE user_id = @supplier_user_id LIMIT 1
);

INSERT INTO material_supplies (
    supplier_id, material_code, material_name, material_description, quantity,
    unit_of_measure, unit_price, delivery_lead_time_days, delivery_notes, status
)
SELECT @supplier_profile_id, seed.material_code, seed.material_name, seed.material_description,
       seed.quantity, seed.unit_of_measure, seed.unit_price, seed.lead_days,
       seed.delivery_notes, seed.status
FROM (
    SELECT 'SEED-FAB-DENIM-001' material_code, 'Midweight Indigo Denim' material_name,
           '10.5 oz cotton denim for jackets and trousers.' material_description,
           920.000 quantity, 'metres' unit_of_measure, 1640.00 unit_price, 6 lead_days,
           'Rolls are shade batched and wrapped against moisture.' delivery_notes, 'ACTIVE' status
    UNION ALL SELECT 'SEED-FAB-VOILE-002', 'Soft Cotton Voile',
           'Fine cotton voile for resort dresses and blouses.', 760.000, 'metres', 1125.00, 4,
           'Inspect before cutting because the cloth is lightweight.', 'ACTIVE'
    UNION ALL SELECT 'SEED-FAB-TWILL-003', 'Stretch Cotton Twill',
           'Durable stretch twill for tapered chinos.', 640.000, 'metres', 1510.00, 5,
           'Store flat to protect elastane recovery.', 'ACTIVE'
    UNION ALL SELECT 'SEED-FAB-JERSEY-004', 'Organic Cotton Jersey',
           'Single jersey knit certified for babywear and loungewear.', 830.000, 'metres', 1380.00, 5,
           'Relax fabric for 24 hours before cutting.', 'ACTIVE'
    UNION ALL SELECT 'SEED-FAB-RAYON-005', 'Printed Rayon Challis',
           'Soft drape rayon challis with small botanical print.', 510.000, 'metres', 1290.00, 7,
           'Pre-shrink before production approval.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-ZIPPER-006', 'YKK Nylon Coil Zippers',
           'Colour matched nylon coil zippers for dresses and trousers.', 2400.000, 'pieces', 92.00, 3,
           'Packed by colour and length.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-LABEL-007', 'Woven Brand Labels',
           'Soft edge woven neck labels for LankaWear garments.', 8500.000, 'pieces', 18.00, 4,
           'Keep dry and away from direct sunlight.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-ELASTIC-008', 'Knitted Waistband Elastic',
           '40 mm knitted elastic for lounge pants and skirts.', 1200.000, 'metres', 240.00, 3,
           'Cartons contain 100 metre rolls.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-PACKAGING-009', 'Compostable Garment Bags',
           'Transparent compostable bags with ventilation holes.', 6000.000, 'pieces', 26.00, 2,
           'Do not stack near heat sources.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-SNAP-010', 'Antique Brass Snap Buttons',
           'Four-part snap buttons for denim and utility garments.', 4200.000, 'pieces', 34.00, 6,
           'Requires matching press die.', 'ACTIVE'
    UNION ALL SELECT 'SEED-FAB-INTERLOCK-011', 'Cotton Interlock Knit',
           'Stable double-knit cotton for premium polos and structured tees.', 700.000, 'metres', 1460.00, 4,
           'Relax rolls before spreading for size stability.', 'ACTIVE'
    UNION ALL SELECT 'SEED-FAB-POPLIN-012', 'Wrinkle-resistant Cotton Poplin',
           'Lightweight shirting poplin with easy-care finish.', 680.000, 'metres', 1325.00, 5,
           'Keep wrapped until cutting to avoid dust pickup.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-CORD-013', 'Braided Cotton Drawcord',
           'Soft braided cotton drawcord for resort shorts and lounge pants.', 1600.000, 'metres', 86.00, 3,
           'Packed in 200 metre reels.', 'ACTIVE'
    UNION ALL SELECT 'SEED-RAW-CARE-014', 'Printed Care Labels',
           'Wash-care labels printed in English, Sinhala, and Tamil.', 10000.000, 'pieces', 12.00, 2,
           'Verify care symbol artwork before use.', 'ACTIVE'
) seed;

INSERT INTO inventory_materials (
    source_material_supply_id, material_code, material_name, material_description,
    material_type, unit_of_measure, current_quantity, low_stock_threshold, status
)
SELECT ms.id,
       REPLACE(ms.material_code, 'SEED-', 'SEED-INV-'),
       ms.material_name,
       CONCAT('Warehouse batch from supplier record ', ms.material_code, '.'),
       CASE WHEN ms.material_code LIKE 'SEED-FAB-%' THEN 'FABRIC' ELSE 'RAW_MATERIAL' END,
       ms.unit_of_measure,
       CASE ms.material_code
           WHEN 'SEED-FAB-RAYON-005' THEN 22.000
           WHEN 'SEED-RAW-ZIPPER-006' THEN 180.000
           WHEN 'SEED-RAW-LABEL-007' THEN 780.000
           WHEN 'SEED-RAW-CARE-014' THEN 920.000
           ELSE ROUND(ms.quantity * 0.62, 3)
       END,
       CASE WHEN ms.material_code LIKE 'SEED-FAB-%' THEN 40.000
            WHEN ms.material_code = 'SEED-RAW-ZIPPER-006' THEN 250.000
            WHEN ms.material_code = 'SEED-RAW-LABEL-007' THEN 1000.000
            WHEN ms.material_code = 'SEED-RAW-CARE-014' THEN 1200.000
            ELSE 300.000 END,
       'ACTIVE'
FROM material_supplies ms
WHERE ms.material_code LIKE 'SEED-%';

INSERT INTO garment_categories (name, description, status) VALUES
('Workwear Staples', '[BUSINESS_SEED] Durable garments for office and field teams.', 'ACTIVE'),
('Resort Capsule', '[BUSINESS_SEED] Lightweight resortwear for warm climates.', 'ACTIVE'),
('Denim Studio', '[BUSINESS_SEED] Small-batch denim products with practical trims.', 'ACTIVE'),
('Loungewear Basics', '[BUSINESS_SEED] Soft cotton everyday essentials.', 'ACTIVE'),
('Kids Organic', '[BUSINESS_SEED] Organic cotton garments for children.', 'ACTIVE'),
('Travel Essentials', '[BUSINESS_SEED] Easy-care garments for travel and hospitality teams.', 'ACTIVE');

INSERT INTO garment_products (category_id, name, description, image_url, status)
SELECT c.id, seed.product_name, seed.product_description, seed.image_url, 'ACTIVE'
FROM garment_categories c
JOIN (
    SELECT 'Workwear Staples' category_name, 'Biyagama Stretch Chino' product_name,
           'A clean tapered chino cut from stretch cotton twill for office uniforms.' product_description,
           '/products/demo/colombo-classic-polo.jpg' image_url
    UNION ALL SELECT 'Workwear Staples', 'Kurunegala Utility Overshirt',
           'A structured overshirt with reinforced seams and snap-button closure.',
           '/products/demo/ceylon-batik-resort-shirt.jpg'
    UNION ALL SELECT 'Resort Capsule', 'Mirissa Cotton Voile Blouse',
           'A soft voile blouse with a relaxed collar and airy sleeves.',
           '/products/demo/sigiriya-linen-dress.jpg'
    UNION ALL SELECT 'Resort Capsule', 'Bentota Rayon Wrap Skirt',
           'A botanical printed wrap skirt with adjustable side ties.',
           '/products/demo/lotus-handloom-saree.jpg'
    UNION ALL SELECT 'Denim Studio', 'Galle Indigo Denim Jacket',
           'A midweight denim jacket finished with antique brass snap buttons.',
           '/products/demo/ceylon-batik-resort-shirt.jpg'
    UNION ALL SELECT 'Denim Studio', 'Negombo Worker Denim Trouser',
           'Straight-leg denim trouser with reinforced pocket bags.',
           '/products/demo/colombo-classic-polo.jpg'
    UNION ALL SELECT 'Loungewear Basics', 'Kandy Organic Jersey Tee',
           'A breathable organic cotton jersey tee for everyday wear.',
           '/products/demo/colombo-classic-polo.jpg'
    UNION ALL SELECT 'Kids Organic', 'Nuwara Eliya Kids Jersey Set',
           'A soft organic cotton two-piece set for children.',
           '/products/demo/sigiriya-linen-dress.jpg'
    UNION ALL SELECT 'Loungewear Basics', 'Matara Interlock Polo',
           'A structured cotton interlock polo with a clean collar and soft hand feel.',
           '/products/demo/colombo-classic-polo.jpg'
    UNION ALL SELECT 'Travel Essentials', 'Ella Easy-care Travel Shirt',
           'A wrinkle-resistant poplin shirt designed for resort and travel staff.',
           '/products/demo/ceylon-batik-resort-shirt.jpg'
    UNION ALL SELECT 'Travel Essentials', 'Jaffna Drawcord Resort Short',
           'A relaxed resort short with breathable cotton drawcord and deep pockets.',
           '/products/demo/sigiriya-linen-dress.jpg'
    UNION ALL SELECT 'Resort Capsule', 'Anuradhapura A-line Skirt',
           'A crisp cotton poplin skirt with practical side pockets.',
           '/products/demo/lotus-handloom-saree.jpg'
) seed ON seed.category_name = c.name;

INSERT INTO garment_product_variants (product_id, size, color, price, status)
SELECT p.id, seed.size, seed.color, seed.price, seed.status
FROM garment_products p
JOIN (
    SELECT 'Biyagama Stretch Chino' product_name, '30' size, 'Khaki' color, 6850.00 price, 'AVAILABLE' status
    UNION ALL SELECT 'Biyagama Stretch Chino', '32', 'Khaki', 6850.00, 'AVAILABLE'
    UNION ALL SELECT 'Biyagama Stretch Chino', '34', 'Navy', 6950.00, 'AVAILABLE'
    UNION ALL SELECT 'Kurunegala Utility Overshirt', 'M', 'Olive', 7900.00, 'AVAILABLE'
    UNION ALL SELECT 'Kurunegala Utility Overshirt', 'L', 'Olive', 7900.00, 'AVAILABLE'
    UNION ALL SELECT 'Mirissa Cotton Voile Blouse', 'S', 'White', 5400.00, 'AVAILABLE'
    UNION ALL SELECT 'Mirissa Cotton Voile Blouse', 'M', 'White', 5400.00, 'AVAILABLE'
    UNION ALL SELECT 'Bentota Rayon Wrap Skirt', 'S', 'Palm Print', 6250.00, 'AVAILABLE'
    UNION ALL SELECT 'Bentota Rayon Wrap Skirt', 'M', 'Palm Print', 6250.00, 'AVAILABLE'
    UNION ALL SELECT 'Galle Indigo Denim Jacket', 'M', 'Indigo', 9800.00, 'AVAILABLE'
    UNION ALL SELECT 'Galle Indigo Denim Jacket', 'L', 'Indigo', 9800.00, 'AVAILABLE'
    UNION ALL SELECT 'Negombo Worker Denim Trouser', '32', 'Indigo', 7600.00, 'AVAILABLE'
    UNION ALL SELECT 'Negombo Worker Denim Trouser', '34', 'Indigo', 7600.00, 'AVAILABLE'
    UNION ALL SELECT 'Kandy Organic Jersey Tee', 'M', 'Coconut Milk', 3200.00, 'AVAILABLE'
    UNION ALL SELECT 'Kandy Organic Jersey Tee', 'L', 'Coconut Milk', 3200.00, 'AVAILABLE'
    UNION ALL SELECT 'Nuwara Eliya Kids Jersey Set', '4Y', 'Sage', 4100.00, 'AVAILABLE'
    UNION ALL SELECT 'Nuwara Eliya Kids Jersey Set', '6Y', 'Sage', 4100.00, 'AVAILABLE'
    UNION ALL SELECT 'Matara Interlock Polo', 'M', 'Deep Sea', 4650.00, 'AVAILABLE'
    UNION ALL SELECT 'Matara Interlock Polo', 'L', 'Deep Sea', 4650.00, 'AVAILABLE'
    UNION ALL SELECT 'Ella Easy-care Travel Shirt', 'M', 'Pearl White', 5750.00, 'AVAILABLE'
    UNION ALL SELECT 'Ella Easy-care Travel Shirt', 'L', 'Pearl White', 5750.00, 'AVAILABLE'
    UNION ALL SELECT 'Jaffna Drawcord Resort Short', 'M', 'Sand Beige', 5200.00, 'AVAILABLE'
    UNION ALL SELECT 'Jaffna Drawcord Resort Short', 'L', 'Sand Beige', 5200.00, 'AVAILABLE'
    UNION ALL SELECT 'Anuradhapura A-line Skirt', 'S', 'Navy', 6100.00, 'AVAILABLE'
    UNION ALL SELECT 'Anuradhapura A-line Skirt', 'M', 'Navy', 6100.00, 'AVAILABLE'
) seed ON seed.product_name = p.name;

INSERT INTO quotations (quotation_number, customer_id, issued_by_user_id, issued_at)
SELECT seed.quotation_number, @customer_user_id, @sales_user_id,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.days_ago DAY
FROM (
    SELECT 'SEED-QUO-001' quotation_number, 14 days_ago
    UNION ALL SELECT 'SEED-QUO-002', 9
    UNION ALL SELECT 'SEED-QUO-003', 3
    UNION ALL SELECT 'SEED-QUO-004', 2
    UNION ALL SELECT 'SEED-QUO-005', 1
) seed;

INSERT INTO quotation_items (
    quotation_id, product_id, variant_id, product_name_snapshot, quantity,
    selected_size, selected_color, unit_price_snapshot
)
SELECT q.id, p.id, v.id, p.name, seed.quantity, v.size, v.color, v.price
FROM quotations q
JOIN (
    SELECT 'SEED-QUO-001' quotation_number, 'Biyagama Stretch Chino' product_name, '32' size, 'Khaki' color, 18 quantity
    UNION ALL SELECT 'SEED-QUO-001', 'Kurunegala Utility Overshirt', 'M', 'Olive', 12
    UNION ALL SELECT 'SEED-QUO-002', 'Mirissa Cotton Voile Blouse', 'M', 'White', 16
    UNION ALL SELECT 'SEED-QUO-002', 'Bentota Rayon Wrap Skirt', 'S', 'Palm Print', 10
    UNION ALL SELECT 'SEED-QUO-003', 'Nuwara Eliya Kids Jersey Set', '4Y', 'Sage', 20
    UNION ALL SELECT 'SEED-QUO-004', 'Ella Easy-care Travel Shirt', 'M', 'Pearl White', 30
    UNION ALL SELECT 'SEED-QUO-004', 'Jaffna Drawcord Resort Short', 'M', 'Sand Beige', 30
    UNION ALL SELECT 'SEED-QUO-005', 'Matara Interlock Polo', 'L', 'Deep Sea', 24
    UNION ALL SELECT 'SEED-QUO-005', 'Anuradhapura A-line Skirt', 'M', 'Navy', 12
) seed ON seed.quotation_number = q.quotation_number
JOIN garment_products p ON p.name = seed.product_name
JOIN garment_product_variants v ON v.product_id = p.id
    AND v.size = seed.size AND v.color = seed.color;

INSERT INTO orders (customer_id, order_number, status, created_at, updated_at)
SELECT @customer_user_id, seed.order_number, seed.status,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.age_days DAY,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.updated_hours HOUR
FROM (
    SELECT 'SEED-ORD-001' order_number, 'PENDING' status, 1 age_days, 4 updated_hours
    UNION ALL SELECT 'SEED-ORD-002', 'CONFIRMED', 3, 9
    UNION ALL SELECT 'SEED-ORD-003', 'IN_PRODUCTION', 6, 2
    UNION ALL SELECT 'SEED-ORD-004', 'IN_PRODUCTION', 8, 5
    UNION ALL SELECT 'SEED-ORD-005', 'READY_FOR_DELIVERY', 10, 6
    UNION ALL SELECT 'SEED-ORD-006', 'READY_FOR_DELIVERY', 12, 8
    UNION ALL SELECT 'SEED-ORD-007', 'COMPLETED', 18, 24
    UNION ALL SELECT 'SEED-ORD-008', 'CANCELLED', 5, 48
    UNION ALL SELECT 'SEED-ORD-009', 'READY_FOR_DELIVERY', 7, 2
    UNION ALL SELECT 'SEED-ORD-010', 'PENDING', 1, 1
    UNION ALL SELECT 'SEED-ORD-011', 'CONFIRMED', 2, 6
    UNION ALL SELECT 'SEED-ORD-012', 'IN_PRODUCTION', 4, 3
    UNION ALL SELECT 'SEED-ORD-013', 'READY_FOR_DELIVERY', 9, 2
    UNION ALL SELECT 'SEED-ORD-014', 'COMPLETED', 16, 18
) seed;

INSERT INTO order_items (
    order_id, product_id, variant_id, product_name_snapshot, product_image_url_snapshot,
    quantity, selected_size, selected_color, unit_price_snapshot
)
SELECT o.id, p.id, v.id, p.name, p.image_url, seed.quantity, v.size, v.color, v.price
FROM orders o
JOIN (
    SELECT 'SEED-ORD-001' order_number, 'Mirissa Cotton Voile Blouse' product_name, 'S' size, 'White' color, 6 quantity
    UNION ALL SELECT 'SEED-ORD-001', 'Bentota Rayon Wrap Skirt', 'M', 'Palm Print', 4
    UNION ALL SELECT 'SEED-ORD-002', 'Biyagama Stretch Chino', '32', 'Khaki', 14
    UNION ALL SELECT 'SEED-ORD-002', 'Kurunegala Utility Overshirt', 'L', 'Olive', 8
    UNION ALL SELECT 'SEED-ORD-003', 'Galle Indigo Denim Jacket', 'M', 'Indigo', 10
    UNION ALL SELECT 'SEED-ORD-004', 'Kandy Organic Jersey Tee', 'L', 'Coconut Milk', 24
    UNION ALL SELECT 'SEED-ORD-005', 'Nuwara Eliya Kids Jersey Set', '4Y', 'Sage', 18
    UNION ALL SELECT 'SEED-ORD-006', 'Negombo Worker Denim Trouser', '34', 'Indigo', 12
    UNION ALL SELECT 'SEED-ORD-007', 'Biyagama Stretch Chino', '30', 'Khaki', 20
    UNION ALL SELECT 'SEED-ORD-007', 'Kandy Organic Jersey Tee', 'M', 'Coconut Milk', 20
    UNION ALL SELECT 'SEED-ORD-008', 'Bentota Rayon Wrap Skirt', 'S', 'Palm Print', 5
    UNION ALL SELECT 'SEED-ORD-009', 'Mirissa Cotton Voile Blouse', 'M', 'White', 12
    UNION ALL SELECT 'SEED-ORD-009', 'Bentota Rayon Wrap Skirt', 'M', 'Palm Print', 8
    UNION ALL SELECT 'SEED-ORD-010', 'Matara Interlock Polo', 'M', 'Deep Sea', 8
    UNION ALL SELECT 'SEED-ORD-011', 'Ella Easy-care Travel Shirt', 'M', 'Pearl White', 18
    UNION ALL SELECT 'SEED-ORD-011', 'Jaffna Drawcord Resort Short', 'M', 'Sand Beige', 18
    UNION ALL SELECT 'SEED-ORD-012', 'Anuradhapura A-line Skirt', 'M', 'Navy', 16
    UNION ALL SELECT 'SEED-ORD-013', 'Matara Interlock Polo', 'L', 'Deep Sea', 22
    UNION ALL SELECT 'SEED-ORD-014', 'Ella Easy-care Travel Shirt', 'L', 'Pearl White', 20
    UNION ALL SELECT 'SEED-ORD-014', 'Jaffna Drawcord Resort Short', 'L', 'Sand Beige', 20
) seed ON seed.order_number = o.order_number
JOIN garment_products p ON p.name = seed.product_name
JOIN garment_product_variants v ON v.product_id = p.id
    AND v.size = seed.size AND v.color = seed.color;

INSERT INTO order_status_history (order_id, from_status, to_status, changed_by_user_id, changed_at)
SELECT o.id, seed.from_status, seed.to_status,
       CASE WHEN seed.to_status IN ('IN_PRODUCTION', 'READY_FOR_DELIVERY') THEN @production_user_id ELSE @sales_user_id END,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.hours_ago HOUR
FROM orders o
JOIN (
    SELECT 'SEED-ORD-002' order_number, 'PENDING' from_status, 'CONFIRMED' to_status, 54 hours_ago
    UNION ALL SELECT 'SEED-ORD-003', 'PENDING', 'CONFIRMED', 140
    UNION ALL SELECT 'SEED-ORD-003', 'CONFIRMED', 'IN_PRODUCTION', 96
    UNION ALL SELECT 'SEED-ORD-004', 'PENDING', 'CONFIRMED', 188
    UNION ALL SELECT 'SEED-ORD-004', 'CONFIRMED', 'IN_PRODUCTION', 120
    UNION ALL SELECT 'SEED-ORD-005', 'PENDING', 'CONFIRMED', 220
    UNION ALL SELECT 'SEED-ORD-005', 'CONFIRMED', 'IN_PRODUCTION', 170
    UNION ALL SELECT 'SEED-ORD-005', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 30
    UNION ALL SELECT 'SEED-ORD-006', 'PENDING', 'CONFIRMED', 260
    UNION ALL SELECT 'SEED-ORD-006', 'CONFIRMED', 'IN_PRODUCTION', 210
    UNION ALL SELECT 'SEED-ORD-006', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 34
    UNION ALL SELECT 'SEED-ORD-007', 'PENDING', 'CONFIRMED', 400
    UNION ALL SELECT 'SEED-ORD-007', 'CONFIRMED', 'IN_PRODUCTION', 340
    UNION ALL SELECT 'SEED-ORD-007', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 130
    UNION ALL SELECT 'SEED-ORD-007', 'READY_FOR_DELIVERY', 'COMPLETED', 28
    UNION ALL SELECT 'SEED-ORD-008', 'PENDING', 'CANCELLED', 48
    UNION ALL SELECT 'SEED-ORD-009', 'PENDING', 'CONFIRMED', 150
    UNION ALL SELECT 'SEED-ORD-009', 'CONFIRMED', 'IN_PRODUCTION', 100
    UNION ALL SELECT 'SEED-ORD-009', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 4
    UNION ALL SELECT 'SEED-ORD-011', 'PENDING', 'CONFIRMED', 44
    UNION ALL SELECT 'SEED-ORD-012', 'PENDING', 'CONFIRMED', 86
    UNION ALL SELECT 'SEED-ORD-012', 'CONFIRMED', 'IN_PRODUCTION', 60
    UNION ALL SELECT 'SEED-ORD-013', 'PENDING', 'CONFIRMED', 190
    UNION ALL SELECT 'SEED-ORD-013', 'CONFIRMED', 'IN_PRODUCTION', 144
    UNION ALL SELECT 'SEED-ORD-013', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 10
    UNION ALL SELECT 'SEED-ORD-014', 'PENDING', 'CONFIRMED', 370
    UNION ALL SELECT 'SEED-ORD-014', 'CONFIRMED', 'IN_PRODUCTION', 310
    UNION ALL SELECT 'SEED-ORD-014', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 96
    UNION ALL SELECT 'SEED-ORD-014', 'READY_FOR_DELIVERY', 'COMPLETED', 18
) seed ON seed.order_number = o.order_number;

INSERT INTO order_invoices (order_id, invoice_number, total_amount, issued_by_user_id, issued_at)
SELECT o.id, REPLACE(o.order_number, 'ORD', 'INV'),
       SUM(oi.quantity * oi.unit_price_snapshot), @sales_user_id,
       CURRENT_TIMESTAMP(6) - INTERVAL 20 HOUR
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.order_number LIKE 'SEED-ORD-%'
  AND o.status <> 'CANCELLED'
GROUP BY o.id, o.order_number;

INSERT INTO order_payment_records (
    order_id, invoice_id, payment_status, amount_paid, payment_method,
    payment_reference, note, recorded_by_user_id, recorded_at, updated_at
)
SELECT i.order_id, i.id,
       CASE o.status
           WHEN 'PENDING' THEN 'UNPAID'
           WHEN 'CONFIRMED' THEN 'PARTIALLY_PAID'
           ELSE 'PAID'
       END,
       CASE o.status
           WHEN 'PENDING' THEN 0.00
           WHEN 'CONFIRMED' THEN ROUND(i.total_amount * 0.40, 2)
           ELSE i.total_amount
       END,
       CASE WHEN o.status = 'PENDING' THEN NULL ELSE 'BANK_TRANSFER' END,
       CASE WHEN o.status = 'PENDING' THEN NULL ELSE CONCAT('SEED-CEFT-', RIGHT(o.order_number, 3)) END,
       CASE WHEN o.status = 'PENDING' THEN 'Customer payment pending.'
            ELSE 'Recorded against the business data seed bank reference.' END,
       @sales_user_id, CURRENT_TIMESTAMP(6) - INTERVAL 18 HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL 18 HOUR
FROM order_invoices i
JOIN orders o ON o.id = i.order_id;

INSERT INTO production_tasks (
    task_number, order_id, status, started_at, completed_at,
    quality_control_result, quality_checked_by_user_id, quality_checked_at,
    created_at, updated_at
)
SELECT seed.task_number, o.id, seed.status,
       CASE WHEN seed.status = 'PENDING' THEN NULL ELSE CURRENT_TIMESTAMP(6) - INTERVAL seed.started_hours HOUR END,
       CASE WHEN seed.status = 'COMPLETED' THEN CURRENT_TIMESTAMP(6) - INTERVAL seed.completed_hours HOUR ELSE NULL END,
       CASE WHEN seed.status = 'COMPLETED' THEN 'PASSED' ELSE 'PENDING' END,
       CASE WHEN seed.status = 'COMPLETED' THEN @production_user_id ELSE NULL END,
       CASE WHEN seed.status = 'COMPLETED' THEN CURRENT_TIMESTAMP(6) - INTERVAL seed.completed_hours HOUR ELSE NULL END,
       CURRENT_TIMESTAMP(6) - INTERVAL (seed.started_hours + 12) HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL LEAST(seed.completed_hours, seed.started_hours) HOUR
FROM orders o
JOIN (
    SELECT 'SEED-ORD-002' order_number, 'SEED-PRD-002' task_number, 'PENDING' status, 24 started_hours, 0 completed_hours
    UNION ALL SELECT 'SEED-ORD-003', 'SEED-PRD-003', 'IN_PROGRESS', 90, 0
    UNION ALL SELECT 'SEED-ORD-004', 'SEED-PRD-004', 'IN_PROGRESS', 110, 0
    UNION ALL SELECT 'SEED-ORD-005', 'SEED-PRD-005', 'COMPLETED', 160, 36
    UNION ALL SELECT 'SEED-ORD-006', 'SEED-PRD-006', 'COMPLETED', 180, 40
    UNION ALL SELECT 'SEED-ORD-007', 'SEED-PRD-007', 'COMPLETED', 320, 120
    UNION ALL SELECT 'SEED-ORD-009', 'SEED-PRD-009', 'COMPLETED', 110, 6
    UNION ALL SELECT 'SEED-ORD-011', 'SEED-PRD-011', 'PENDING', 20, 0
    UNION ALL SELECT 'SEED-ORD-012', 'SEED-PRD-012', 'IN_PROGRESS', 54, 0
    UNION ALL SELECT 'SEED-ORD-013', 'SEED-PRD-013', 'COMPLETED', 136, 12
    UNION ALL SELECT 'SEED-ORD-014', 'SEED-PRD-014', 'COMPLETED', 290, 88
) seed ON seed.order_number = o.order_number;

INSERT INTO production_task_details (
    production_task_id, work_details, work_assignment, work_notes
)
SELECT pt.id,
       CASE pt.task_number
           WHEN 'SEED-PRD-002' THEN 'Prepare cutting marker and reserve twill, zippers, labels, and packaging.'
           WHEN 'SEED-PRD-003' THEN 'Cut denim shells, attach pocket bags, complete seams, and prepare snap-button setting.'
           WHEN 'SEED-PRD-004' THEN 'Relax jersey, cut panels, assemble neckline, and complete cover-stitch hems.'
           WHEN 'SEED-PRD-005' THEN 'Complete kids jersey set assembly, label attachment, final press, and packing.'
           WHEN 'SEED-PRD-006' THEN 'Complete denim trouser assembly, waistband setting, trims, press, and QC.'
           WHEN 'SEED-PRD-009' THEN 'Complete resortwear separates, check waist ties, attach labels, final press, and pack by style.'
           WHEN 'SEED-PRD-011' THEN 'Prepare travel set cutting plan, label bundles by style, and verify size ratios.'
           WHEN 'SEED-PRD-012' THEN 'Cut poplin skirt panels, assemble waistbands, attach labels, and complete in-line checks.'
           WHEN 'SEED-PRD-013' THEN 'Complete interlock polo assembly, collar setting, final press, and polybag packing.'
           WHEN 'SEED-PRD-014' THEN 'Complete travel shirt and resort short sets, inspect drawcords, press, and pack together.'
           ELSE 'Complete chino and jersey items, balance measurements, and pack by size run.'
       END,
       CASE pt.task_number
           WHEN 'SEED-PRD-002' THEN 'Planning Desk · Supervisor Ruwani Silva'
           WHEN 'SEED-PRD-003' THEN 'Denim Line 2 · Supervisor Mahesh Perera'
           WHEN 'SEED-PRD-004' THEN 'Knit Line 1 · Supervisor Dilani Costa'
           WHEN 'SEED-PRD-005' THEN 'Kidswear Cell · Supervisor Gayani Mendis'
           WHEN 'SEED-PRD-006' THEN 'Denim Line 1 · Supervisor Nuwan Fernando'
           WHEN 'SEED-PRD-009' THEN 'Resortwear Line · Supervisor Hiruni Jayasekara'
           WHEN 'SEED-PRD-011' THEN 'Planning Desk · Supervisor Ruwani Silva'
           WHEN 'SEED-PRD-012' THEN 'Woven Line 3 · Supervisor Amali Herath'
           WHEN 'SEED-PRD-013' THEN 'Knit Line 2 · Supervisor Sanjeewa Dias'
           WHEN 'SEED-PRD-014' THEN 'Travelwear Line · Supervisor Pradeep Samarasinghe'
           ELSE 'Finishing Line A · Supervisor Isuru Pathirana'
       END,
       'Use approved size specs and record any replacement trims before final QC.'
FROM production_tasks pt
WHERE pt.task_number LIKE 'SEED-PRD-%';

INSERT INTO production_task_material_requirements (
    production_task_id, inventory_material_id, required_quantity
)
SELECT pt.id, im.id, seed.required_quantity
FROM production_tasks pt
JOIN (
    SELECT 'SEED-PRD-002' task_number, 'SEED-INV-FAB-TWILL-003' material_code, 42.000 required_quantity
    UNION ALL SELECT 'SEED-PRD-002', 'SEED-INV-RAW-ZIPPER-006', 22.000
    UNION ALL SELECT 'SEED-PRD-003', 'SEED-INV-FAB-DENIM-001', 58.000
    UNION ALL SELECT 'SEED-PRD-003', 'SEED-INV-RAW-SNAP-010', 80.000
    UNION ALL SELECT 'SEED-PRD-004', 'SEED-INV-FAB-JERSEY-004', 48.000
    UNION ALL SELECT 'SEED-PRD-004', 'SEED-INV-RAW-LABEL-007', 40.000
    UNION ALL SELECT 'SEED-PRD-005', 'SEED-INV-FAB-JERSEY-004', 54.000
    UNION ALL SELECT 'SEED-PRD-005', 'SEED-INV-RAW-LABEL-007', 36.000
    UNION ALL SELECT 'SEED-PRD-005', 'SEED-INV-RAW-PACKAGING-009', 36.000
    UNION ALL SELECT 'SEED-PRD-006', 'SEED-INV-FAB-DENIM-001', 72.000
    UNION ALL SELECT 'SEED-PRD-006', 'SEED-INV-RAW-SNAP-010', 120.000
    UNION ALL SELECT 'SEED-PRD-007', 'SEED-INV-FAB-TWILL-003', 70.000
    UNION ALL SELECT 'SEED-PRD-007', 'SEED-INV-FAB-JERSEY-004', 40.000
    UNION ALL SELECT 'SEED-PRD-007', 'SEED-INV-RAW-LABEL-007', 60.000
    UNION ALL SELECT 'SEED-PRD-009', 'SEED-INV-FAB-VOILE-002', 36.000
    UNION ALL SELECT 'SEED-PRD-009', 'SEED-INV-FAB-RAYON-005', 32.000
    UNION ALL SELECT 'SEED-PRD-009', 'SEED-INV-RAW-LABEL-007', 24.000
    UNION ALL SELECT 'SEED-PRD-009', 'SEED-INV-RAW-PACKAGING-009', 24.000
    UNION ALL SELECT 'SEED-PRD-011', 'SEED-INV-FAB-POPLIN-012', 68.000
    UNION ALL SELECT 'SEED-PRD-011', 'SEED-INV-RAW-CORD-013', 42.000
    UNION ALL SELECT 'SEED-PRD-011', 'SEED-INV-RAW-CARE-014', 36.000
    UNION ALL SELECT 'SEED-PRD-012', 'SEED-INV-FAB-POPLIN-012', 44.000
    UNION ALL SELECT 'SEED-PRD-012', 'SEED-INV-RAW-CARE-014', 18.000
    UNION ALL SELECT 'SEED-PRD-013', 'SEED-INV-FAB-INTERLOCK-011', 66.000
    UNION ALL SELECT 'SEED-PRD-013', 'SEED-INV-RAW-LABEL-007', 22.000
    UNION ALL SELECT 'SEED-PRD-013', 'SEED-INV-RAW-PACKAGING-009', 22.000
    UNION ALL SELECT 'SEED-PRD-014', 'SEED-INV-FAB-POPLIN-012', 74.000
    UNION ALL SELECT 'SEED-PRD-014', 'SEED-INV-RAW-CORD-013', 48.000
    UNION ALL SELECT 'SEED-PRD-014', 'SEED-INV-RAW-CARE-014', 40.000
) seed ON seed.task_number = pt.task_number
JOIN inventory_materials im ON im.material_code = seed.material_code;

INSERT INTO production_task_material_usage (
    production_task_id, inventory_material_id, quantity_used, recorded_by_user_id, recorded_at
)
SELECT requirement.production_task_id, requirement.inventory_material_id,
       requirement.required_quantity, @production_user_id,
       COALESCE(pt.completed_at, CURRENT_TIMESTAMP(6) - INTERVAL 1 HOUR)
FROM production_task_material_requirements requirement
JOIN production_tasks pt ON pt.id = requirement.production_task_id
WHERE pt.status = 'COMPLETED';

INSERT INTO deliveries (
    delivery_number, order_id, active_order_lock_id, scheduled_at, delivery_address,
    delivery_notes, status, created_at, updated_at
)
SELECT seed.delivery_number, o.id,
       CASE WHEN seed.status = 'CANCELLED' THEN NULL ELSE o.id END,
       seed.scheduled_at, seed.delivery_address, seed.delivery_notes, seed.status,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.created_days DAY,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.updated_hours HOUR
FROM orders o
JOIN (
    SELECT 'SEED-ORD-005' order_number, 'SEED-DEL-005' delivery_number,
           CURRENT_TIMESTAMP(6) + INTERVAL 2 DAY scheduled_at,
           '27 Flower Road, Colombo 07, Sri Lanka' delivery_address,
           'Deliver to office reception between 10:00 and 12:00.' delivery_notes,
           'SCHEDULED' status, 2 created_days, 6 updated_hours
    UNION ALL SELECT 'SEED-ORD-006', 'SEED-DEL-006',
           CURRENT_TIMESTAMP(6) + INTERVAL 6 HOUR,
           '16 Dutch Canal Road, Negombo, Sri Lanka',
           'Driver must call before leaving the main road.', 'OUT_FOR_DELIVERY', 3, 1
    UNION ALL SELECT 'SEED-ORD-007', 'SEED-DEL-007',
           CURRENT_TIMESTAMP(6) - INTERVAL 2 DAY,
           '84 Peradeniya Road, Kandy, Sri Lanka',
           'Delivered with signed receipt from store manager.', 'DELIVERED', 5, 24
    UNION ALL SELECT 'SEED-ORD-013', 'SEED-DEL-013',
           CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY,
           '11 Beach Road, Matara, Sri Lanka',
           'Customer requested delivery before lunch service starts.', 'SCHEDULED', 1, 2
    UNION ALL SELECT 'SEED-ORD-014', 'SEED-DEL-014',
           CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY,
           '42 Hospital Street, Galle Fort, Sri Lanka',
           'Delivered to boutique stockroom with item count verified.', 'DELIVERED', 4, 18
) seed ON seed.order_number = o.order_number;

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @inventory_user_id, 'LOW_STOCK', 'Rayon challis stock is low',
       'Printed Rayon Challis has dropped below its 40 metre reorder threshold.',
       'INVENTORY', im.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 45 MINUTE
FROM inventory_materials im WHERE im.material_code = 'SEED-INV-FAB-RAYON-005';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @inventory_user_id, 'LOW_STOCK', 'Zipper stock needs replenishment',
       'YKK Nylon Coil Zippers are below the 250 piece low-stock threshold.',
       'INVENTORY', im.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 35 MINUTE
FROM inventory_materials im WHERE im.material_code = 'SEED-INV-RAW-ZIPPER-006';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @customer_user_id, 'ORDER_STATUS', 'Order SEED-ORD-005 is ready',
       'Your kids jersey set order is ready for scheduled delivery.',
       'ORDER', o.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 6 HOUR
FROM orders o WHERE o.order_number = 'SEED-ORD-005';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @production_user_id, 'PRODUCTION_STATUS', 'Denim jacket work is active',
       'SEED-PRD-003 is in progress on Denim Line 2.',
       'PRODUCTION', pt.id, CURRENT_TIMESTAMP(6) - INTERVAL 1 HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL 8 HOUR
FROM production_tasks pt WHERE pt.task_number = 'SEED-PRD-003';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @customer_user_id, 'DELIVERY_STATUS', 'Delivery is out for delivery',
       'SEED-DEL-006 is out for delivery to Negombo.',
       'DELIVERY', d.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 1 HOUR
FROM deliveries d WHERE d.delivery_number = 'SEED-DEL-006';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @inventory_user_id, 'LOW_STOCK', 'Care labels below threshold',
       'Printed Care Labels are below the 1200 piece low-stock threshold.',
       'INVENTORY', im.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 25 MINUTE
FROM inventory_materials im WHERE im.material_code = 'SEED-INV-RAW-CARE-014';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @sales_user_id, 'ORDER_STATUS', 'Travelwear order completed',
       'SEED-ORD-014 has been completed after delivery confirmation.',
       'ORDER', o.id, CURRENT_TIMESTAMP(6) - INTERVAL 8 HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL 18 HOUR
FROM orders o WHERE o.order_number = 'SEED-ORD-014';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT @customer_user_id, 'DELIVERY_STATUS', 'Matara delivery scheduled',
       'SEED-DEL-013 is scheduled for delivery tomorrow.',
       'DELIVERY', d.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 2 HOUR
FROM deliveries d WHERE d.delivery_number = 'SEED-DEL-013';

COMMIT;

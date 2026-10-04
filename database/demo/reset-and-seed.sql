-- Disposable demonstration data for TGMS.
-- Execute only through scripts/demo/reset-demo.sh against a non-production database.
START TRANSACTION;

-- Delete only records identified by the reserved demo accounts and identifiers.
DELETE FROM notifications
WHERE recipient_user_id IN (SELECT id FROM users WHERE email LIKE 'demo.%@tgms.example');
DELETE FROM password_reset_tokens
WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'demo.%@tgms.example');
DELETE FROM deliveries
WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM production_task_material_usage
WHERE production_task_id IN (SELECT id FROM production_tasks WHERE order_id IN (
    SELECT id FROM orders WHERE customer_id IN (
        SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
    )
));
DELETE FROM production_task_material_requirements
WHERE production_task_id IN (SELECT id FROM production_tasks WHERE order_id IN (
    SELECT id FROM orders WHERE customer_id IN (
        SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
    )
));
DELETE FROM production_task_details
WHERE production_task_id IN (SELECT id FROM production_tasks WHERE order_id IN (
    SELECT id FROM orders WHERE customer_id IN (
        SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
    )
));
DELETE FROM production_tasks
WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM order_payment_records
WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM order_invoices
WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM order_status_history
WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM order_items
WHERE order_id IN (SELECT id FROM orders WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM orders
WHERE customer_id IN (SELECT id FROM users WHERE email = 'demo.customer@tgms.example');
DELETE FROM quotation_items
WHERE quotation_id IN (SELECT id FROM quotations WHERE customer_id IN (
    SELECT id FROM users WHERE email = 'demo.customer@tgms.example'
));
DELETE FROM quotations
WHERE customer_id IN (SELECT id FROM users WHERE email = 'demo.customer@tgms.example');
DELETE FROM inventory_materials WHERE material_code LIKE 'DEMO-%';
DELETE FROM material_supplies WHERE material_code LIKE 'DEMO-%';
DELETE FROM supplier_profiles
WHERE user_id IN (SELECT id FROM users WHERE email = 'demo.supplier@tgms.example');
DELETE FROM garment_product_variants
WHERE product_id IN (
    SELECT p.id FROM garment_products p
    JOIN garment_categories c ON c.id = p.category_id
    WHERE c.description LIKE '%[DEMO_SEED]%'
);
DELETE p FROM garment_products p
JOIN garment_categories c ON c.id = p.category_id
WHERE c.description LIKE '%[DEMO_SEED]%';
DELETE FROM garment_categories WHERE description LIKE '%[DEMO_SEED]%';
-- Remove records from the retired single-product demonstration seed.
DELETE FROM garment_product_variants
WHERE product_id IN (SELECT id FROM garment_products WHERE name = '[DEMO] Essential Polo');
DELETE FROM garment_products WHERE name = '[DEMO] Essential Polo';
DELETE FROM garment_categories WHERE name = '[DEMO] Presentation Collection';
DELETE FROM users WHERE email LIKE 'demo.%@tgms.example';

-- The reset script generates @demo_password_hash from the runtime-only demo password.
INSERT INTO users (
    email, password_hash, full_name, role, is_active, email_verified_at
) VALUES
('demo.admin@tgms.example', @demo_password_hash, 'Nadeesha Perera', 'ADMINISTRATOR', TRUE, CURRENT_TIMESTAMP(6)),
('demo.supplier@tgms.example', @demo_password_hash, 'Chathura Bandara', 'SUPPLIER', TRUE, CURRENT_TIMESTAMP(6)),
('demo.inventory@tgms.example', @demo_password_hash, 'Tharushi Senanayake', 'INVENTORY_MANAGER', TRUE, CURRENT_TIMESTAMP(6)),
('demo.production@tgms.example', @demo_password_hash, 'Kasun Jayawardena', 'PRODUCTION_MANAGER', TRUE, CURRENT_TIMESTAMP(6)),
('demo.sales@tgms.example', @demo_password_hash, 'Dinithi Fernando', 'SALES_OFFICER', TRUE, CURRENT_TIMESTAMP(6)),
('demo.customer@tgms.example', @demo_password_hash, 'Kavindu Dissanayake', 'CUSTOMER', TRUE, CURRENT_TIMESTAMP(6));

-- Supplier and material catalogue records.
INSERT INTO supplier_profiles (user_id, business_name, contact_phone, address)
SELECT id, 'Serendib Textiles (Pvt) Ltd', '+94 37 223 4185',
       'Pannala Industrial Estate, Kuliyapitiya, Sri Lanka'
FROM users WHERE email = 'demo.supplier@tgms.example';

INSERT INTO material_supplies (
    supplier_id, material_code, material_name, material_description, quantity,
    unit_of_measure, unit_price, delivery_lead_time_days, delivery_notes, status
)
SELECT sp.id, seed.material_code, seed.material_name, seed.material_description,
       seed.quantity, seed.unit_of_measure, seed.unit_price, seed.lead_days,
       seed.delivery_notes, 'ACTIVE'
FROM supplier_profiles sp
JOIN users u ON u.id = sp.user_id
JOIN (
    SELECT 'DEMO-FAB-BATIK-001' material_code, 'Hand-dyed Batik Cotton' material_name,
           'Colourfast cotton prepared by a Sri Lankan batik workshop.' material_description,
           500.000 quantity, 'metres' unit_of_measure, 1450.00 unit_price, 5 lead_days,
           'Packed in 25 metre rolls.' delivery_notes
    UNION ALL SELECT 'DEMO-FAB-HANDLOOM-002', 'Sri Lankan Handloom Cotton',
           'Breathable handwoven cotton for sarees and structured separates.',
           300.000, 'metres', 1850.00, 7, 'Protect woven borders during transport.'
    UNION ALL SELECT 'DEMO-FAB-LINEN-003', 'Tropical Linen Blend',
           'Lightweight linen blend selected for warm-weather dresses.',
           350.000, 'metres', 1725.00, 4, 'Natural slub variation is expected.'
    UNION ALL SELECT 'DEMO-FAB-PIQUE-004', 'Combed Cotton Pique',
           'Soft combed piqué knit for premium polo production.',
           600.000, 'metres', 1180.00, 3, 'Shade-matched rolls supplied together.'
    UNION ALL SELECT 'DEMO-RAW-THREAD-005', 'Core-spun Sewing Thread',
           'High-strength colourfast sewing thread for industrial machines.',
           180.000, 'cones', 680.00, 2, 'Mixed forest green, maroon, ivory, and cinnamon shades.'
    UNION ALL SELECT 'DEMO-RAW-BUTTON-006', 'Coconut Shell Buttons',
           'Polished natural coconut-shell buttons from a local craft supplier.',
           5000.000, 'pieces', 38.00, 6, 'Sorted by diameter before dispatch.'
) seed ON TRUE
WHERE u.email = 'demo.supplier@tgms.example';

INSERT INTO inventory_materials (
    source_material_supply_id, material_code, material_name, material_description,
    material_type, unit_of_measure, current_quantity, low_stock_threshold, status
)
SELECT ms.id,
       REPLACE(ms.material_code, 'DEMO-', 'DEMO-INV-'),
       ms.material_name,
       CONCAT('Warehouse stock received from ', ms.material_code, '.'),
       CASE WHEN ms.material_code LIKE 'DEMO-FAB-%' THEN 'FABRIC' ELSE 'RAW_MATERIAL' END,
       ms.unit_of_measure,
       CASE ms.material_code
           WHEN 'DEMO-FAB-BATIK-001' THEN 240.000
           WHEN 'DEMO-FAB-HANDLOOM-002' THEN 108.000
           WHEN 'DEMO-FAB-LINEN-003' THEN 18.000
           WHEN 'DEMO-FAB-PIQUE-004' THEN 390.000
           WHEN 'DEMO-RAW-THREAD-005' THEN 72.000
           ELSE 1240.000
       END,
       CASE WHEN ms.material_code LIKE 'DEMO-FAB-%' THEN 25.000
            WHEN ms.material_code = 'DEMO-RAW-THREAD-005' THEN 20.000
            ELSE 500.000 END,
       'ACTIVE'
FROM material_supplies ms
WHERE ms.material_code LIKE 'DEMO-%';

-- Garment catalogue records with local media references.
INSERT INTO garment_categories (name, description, status) VALUES
('Batik Menswear', '[DEMO_SEED] Contemporary Sri Lankan batik garments.', 'ACTIVE'),
('Handloom Collection', '[DEMO_SEED] Locally inspired handloom apparel.', 'ACTIVE'),
('Tropical Womenswear', '[DEMO_SEED] Lightweight garments for Sri Lankan weather.', 'ACTIVE'),
('Everyday Essentials', '[DEMO_SEED] Premium daily-wear garments.', 'ACTIVE');

INSERT INTO garment_products (category_id, name, description, image_url, status)
SELECT c.id, seed.product_name, seed.product_description, seed.image_url, 'ACTIVE'
FROM garment_categories c
JOIN (
    SELECT 'Batik Menswear' category_name, 'Ceylon Batik Resort Shirt' product_name,
           'A breathable short-sleeve cotton shirt with a contemporary Sri Lankan batik pattern.' product_description,
           '/products/demo/ceylon-batik-resort-shirt.jpg' image_url
    UNION ALL SELECT 'Handloom Collection', 'Lotus Handloom Saree',
           'An ivory and maroon office saree woven with a restrained traditional border.',
           '/products/demo/lotus-handloom-saree.jpg'
    UNION ALL SELECT 'Tropical Womenswear', 'Sigiriya Linen Midi Dress',
           'A cinnamon-toned linen dress designed for polished comfort in tropical weather.',
           '/products/demo/sigiriya-linen-dress.jpg'
    UNION ALL SELECT 'Everyday Essentials', 'Colombo Classic Polo',
           'A refined forest-green combed cotton piqué polo for work and weekend wear.',
           '/products/demo/colombo-classic-polo.jpg'
) seed ON seed.category_name = c.name;

INSERT INTO garment_product_variants (product_id, size, color, price, status)
SELECT p.id, seed.size, seed.color, seed.price, 'AVAILABLE'
FROM garment_products p
JOIN (
    SELECT 'Ceylon Batik Resort Shirt' product_name, 'M' size, 'Ocean Teal' color, 5950.00 price
    UNION ALL SELECT 'Ceylon Batik Resort Shirt', 'L', 'Ocean Teal', 5950.00
    UNION ALL SELECT 'Lotus Handloom Saree', 'Free Size', 'Ivory and Maroon', 12500.00
    UNION ALL SELECT 'Lotus Handloom Saree', 'Free Size', 'Ivory and Indigo', 12800.00
    UNION ALL SELECT 'Sigiriya Linen Midi Dress', 'S', 'Cinnamon', 8900.00
    UNION ALL SELECT 'Sigiriya Linen Midi Dress', 'M', 'Cinnamon', 8900.00
    UNION ALL SELECT 'Colombo Classic Polo', 'M', 'Forest Green', 4200.00
    UNION ALL SELECT 'Colombo Classic Polo', 'L', 'Forest Green', 4200.00
) seed ON seed.product_name = p.name;

-- Issued quotations with immutable pricing snapshots.
INSERT INTO quotations (quotation_number, customer_id, issued_by_user_id, issued_at)
SELECT 'DEMO-QUO-001', customer.id, sales.id, CURRENT_TIMESTAMP(6) - INTERVAL 8 DAY
FROM users customer JOIN users sales
WHERE customer.email = 'demo.customer@tgms.example'
  AND sales.email = 'demo.sales@tgms.example';

INSERT INTO quotation_items (
    quotation_id, product_id, variant_id, product_name_snapshot, quantity,
    selected_size, selected_color, unit_price_snapshot
)
SELECT q.id, p.id, v.id, p.name, 12, v.size, v.color, v.price
FROM quotations q
JOIN garment_products p ON p.name = 'Ceylon Batik Resort Shirt'
JOIN garment_product_variants v ON v.product_id = p.id AND v.size = 'M'
WHERE q.quotation_number = 'DEMO-QUO-001';

-- Orders representing the principal lifecycle states.
INSERT INTO orders (customer_id, order_number, status, created_at, updated_at)
SELECT customer.id, seed.order_number, seed.status,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.age_days DAY,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.update_hours HOUR
FROM users customer
JOIN (
    SELECT 'DEMO-ORD-001' order_number, 'PENDING' status, 2 age_days, 5 update_hours
    UNION ALL SELECT 'DEMO-ORD-002', 'IN_PRODUCTION', 7, 8
    UNION ALL SELECT 'DEMO-ORD-003', 'READY_FOR_DELIVERY', 12, 3
    UNION ALL SELECT 'DEMO-ORD-004', 'COMPLETED', 20, 24
    UNION ALL SELECT 'DEMO-ORD-005', 'READY_FOR_DELIVERY', 9, 2
) seed ON TRUE
WHERE customer.email = 'demo.customer@tgms.example';

INSERT INTO order_items (
    order_id, product_id, variant_id, product_name_snapshot, product_image_url_snapshot,
    quantity, selected_size, selected_color, unit_price_snapshot
)
SELECT o.id, p.id, MIN(v.id), p.name, p.image_url,
       seed.quantity, seed.size, seed.color, MIN(v.price)
FROM orders o
JOIN (
    SELECT 'DEMO-ORD-001' order_number, 'Ceylon Batik Resort Shirt' product_name,
           'M' size, 'Ocean Teal' color, 12 quantity
    UNION ALL SELECT 'DEMO-ORD-002', 'Lotus Handloom Saree',
           'Free Size', 'Ivory and Maroon', 8
    UNION ALL SELECT 'DEMO-ORD-003', 'Sigiriya Linen Midi Dress',
           'M', 'Cinnamon', 6
    UNION ALL SELECT 'DEMO-ORD-004', 'Colombo Classic Polo',
           'L', 'Forest Green', 20
    UNION ALL SELECT 'DEMO-ORD-005', 'Ceylon Batik Resort Shirt',
           'L', 'Ocean Teal', 10
) seed ON seed.order_number = o.order_number
JOIN garment_products p ON p.name = seed.product_name
JOIN garment_product_variants v ON v.product_id = p.id
    AND v.size = seed.size AND v.color = seed.color
GROUP BY o.id, p.id, p.name, p.image_url, seed.quantity, seed.size, seed.color;

INSERT INTO order_status_history (order_id, from_status, to_status, changed_by_user_id, changed_at)
SELECT o.id, transitions.from_status, transitions.to_status,
       CASE WHEN transitions.to_status = 'IN_PRODUCTION' THEN production.id ELSE sales.id END,
       CURRENT_TIMESTAMP(6) - INTERVAL transitions.hours_ago HOUR
FROM orders o
JOIN (
    SELECT 'DEMO-ORD-002' order_number, 'PENDING' from_status, 'CONFIRMED' to_status, 120 hours_ago
    UNION ALL SELECT 'DEMO-ORD-002', 'CONFIRMED', 'IN_PRODUCTION', 72
    UNION ALL SELECT 'DEMO-ORD-003', 'PENDING', 'CONFIRMED', 240
    UNION ALL SELECT 'DEMO-ORD-003', 'CONFIRMED', 'IN_PRODUCTION', 168
    UNION ALL SELECT 'DEMO-ORD-003', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 24
    UNION ALL SELECT 'DEMO-ORD-004', 'PENDING', 'CONFIRMED', 432
    UNION ALL SELECT 'DEMO-ORD-004', 'CONFIRMED', 'IN_PRODUCTION', 360
    UNION ALL SELECT 'DEMO-ORD-004', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 96
    UNION ALL SELECT 'DEMO-ORD-004', 'READY_FOR_DELIVERY', 'COMPLETED', 24
    UNION ALL SELECT 'DEMO-ORD-005', 'PENDING', 'CONFIRMED', 200
    UNION ALL SELECT 'DEMO-ORD-005', 'CONFIRMED', 'IN_PRODUCTION', 150
    UNION ALL SELECT 'DEMO-ORD-005', 'IN_PRODUCTION', 'READY_FOR_DELIVERY', 8
) transitions ON transitions.order_number = o.order_number
JOIN users sales ON sales.email = 'demo.sales@tgms.example'
JOIN users production ON production.email = 'demo.production@tgms.example';

-- Invoice and payment records across settlement states.
INSERT INTO order_invoices (order_id, invoice_number, total_amount, issued_by_user_id, issued_at)
SELECT o.id, REPLACE(o.order_number, 'ORD', 'INV'),
       SUM(oi.quantity * oi.unit_price_snapshot), sales.id,
       CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN users sales ON sales.email = 'demo.sales@tgms.example'
WHERE o.order_number LIKE 'DEMO-ORD-%'
GROUP BY o.id, o.order_number, sales.id;

INSERT INTO order_payment_records (
    order_id, invoice_id, payment_status, amount_paid, payment_method,
    payment_reference, note, recorded_by_user_id, recorded_at, updated_at
)
SELECT i.order_id, i.id,
       CASE o.order_number
           WHEN 'DEMO-ORD-001' THEN 'UNPAID'
           WHEN 'DEMO-ORD-002' THEN 'PARTIALLY_PAID'
           ELSE 'PAID'
       END,
       CASE o.order_number
           WHEN 'DEMO-ORD-001' THEN 0.00
           WHEN 'DEMO-ORD-002' THEN i.total_amount / 2
           ELSE i.total_amount
       END,
       CASE WHEN o.order_number = 'DEMO-ORD-001' THEN NULL ELSE 'BANK_TRANSFER' END,
       CASE WHEN o.order_number = 'DEMO-ORD-001' THEN NULL
            ELSE CONCAT('CEFT-DEMO-', RIGHT(o.order_number, 3)) END,
       CASE WHEN o.order_number = 'DEMO-ORD-001' THEN 'Awaiting customer payment.'
            ELSE 'Verified against the LankaWear demo bank statement.' END,
       sales.id, CURRENT_TIMESTAMP(6) - INTERVAL 12 HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL 12 HOUR
FROM order_invoices i
JOIN orders o ON o.id = i.order_id
JOIN users sales ON sales.email = 'demo.sales@tgms.example'
WHERE o.order_number LIKE 'DEMO-ORD-%';

-- Production records with material traceability and quality-control results.
INSERT INTO production_tasks (
    task_number, order_id, status, started_at, completed_at,
    quality_control_result, quality_checked_by_user_id, quality_checked_at,
    created_at, updated_at
)
SELECT seed.task_number, o.id, seed.status,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.started_hours HOUR,
       CASE WHEN seed.status = 'COMPLETED'
            THEN CURRENT_TIMESTAMP(6) - INTERVAL seed.completed_hours HOUR ELSE NULL END,
       CASE WHEN seed.status = 'COMPLETED' THEN 'PASSED' ELSE 'PENDING' END,
       CASE WHEN seed.status = 'COMPLETED' THEN production.id ELSE NULL END,
       CASE WHEN seed.status = 'COMPLETED'
            THEN CURRENT_TIMESTAMP(6) - INTERVAL seed.completed_hours HOUR ELSE NULL END,
       CURRENT_TIMESTAMP(6) - INTERVAL (seed.started_hours + 24) HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.completed_hours HOUR
FROM orders o
JOIN (
    SELECT 'DEMO-ORD-002' order_number, 'DEMO-PRD-002' task_number,
           'IN_PROGRESS' status, 70 started_hours, 8 completed_hours
    UNION ALL SELECT 'DEMO-ORD-003', 'DEMO-PRD-003', 'COMPLETED', 160, 30
    UNION ALL SELECT 'DEMO-ORD-004', 'DEMO-PRD-004', 'COMPLETED', 350, 100
    UNION ALL SELECT 'DEMO-ORD-005', 'DEMO-PRD-005', 'COMPLETED', 140, 10
) seed ON seed.order_number = o.order_number
JOIN users production ON production.email = 'demo.production@tgms.example';

INSERT INTO production_task_details (
    production_task_id, work_details, work_assignment, work_notes
)
SELECT pt.id,
       CASE pt.task_number
           WHEN 'DEMO-PRD-002' THEN 'Cut, align woven borders, stitch, press, and perform finishing checks.'
           WHEN 'DEMO-PRD-003' THEN 'Cut linen panels, assemble bodice and skirt, attach buttons, and finish hems.'
           WHEN 'DEMO-PRD-005' THEN 'Cut batik shirt panels, match print direction, stitch collars and sleeves, press, and inspect.'
           ELSE 'Cut piqué panels, assemble collar and placket, stitch, press, and inspect.'
       END,
       CASE pt.task_number
           WHEN 'DEMO-PRD-002' THEN 'Sewing Line B · Supervisor Anjali Wickramasinghe'
           WHEN 'DEMO-PRD-003' THEN 'Sewing Line C · Supervisor Malini Rathnayake'
           WHEN 'DEMO-PRD-005' THEN 'Sewing Line D · Supervisor Chamari Peiris'
           ELSE 'Sewing Line A · Supervisor Sahan Weerakoon'
       END,
       'Follow the approved LankaWear measurement sheet and record quality exceptions.'
FROM production_tasks pt WHERE pt.task_number LIKE 'DEMO-PRD-%';

INSERT INTO production_task_material_requirements (
    production_task_id, inventory_material_id, required_quantity
)
SELECT pt.id, im.id, seed.required_quantity
FROM production_tasks pt
JOIN (
    SELECT 'DEMO-PRD-002' task_number, 'DEMO-INV-FAB-HANDLOOM-002' material_code, 48.000 required_quantity
    UNION ALL SELECT 'DEMO-PRD-002', 'DEMO-INV-RAW-THREAD-005', 3.000
    UNION ALL SELECT 'DEMO-PRD-003', 'DEMO-INV-FAB-LINEN-003', 30.000
    UNION ALL SELECT 'DEMO-PRD-003', 'DEMO-INV-RAW-THREAD-005', 2.000
    UNION ALL SELECT 'DEMO-PRD-003', 'DEMO-INV-RAW-BUTTON-006', 48.000
    UNION ALL SELECT 'DEMO-PRD-004', 'DEMO-INV-FAB-PIQUE-004', 80.000
    UNION ALL SELECT 'DEMO-PRD-004', 'DEMO-INV-RAW-THREAD-005', 4.000
    UNION ALL SELECT 'DEMO-PRD-004', 'DEMO-INV-RAW-BUTTON-006', 60.000
    UNION ALL SELECT 'DEMO-PRD-005', 'DEMO-INV-FAB-BATIK-001', 42.000
    UNION ALL SELECT 'DEMO-PRD-005', 'DEMO-INV-RAW-THREAD-005', 2.000
    UNION ALL SELECT 'DEMO-PRD-005', 'DEMO-INV-RAW-BUTTON-006', 40.000
) seed ON seed.task_number = pt.task_number
JOIN inventory_materials im ON im.material_code = seed.material_code;

INSERT INTO production_task_material_usage (
    production_task_id, inventory_material_id, quantity_used, recorded_by_user_id, recorded_at
)
SELECT requirement.production_task_id, requirement.inventory_material_id,
       requirement.required_quantity, production.id,
       COALESCE(pt.completed_at, CURRENT_TIMESTAMP(6) - INTERVAL 1 HOUR)
FROM production_task_material_requirements requirement
JOIN production_tasks pt ON pt.id = requirement.production_task_id
JOIN users production ON production.email = 'demo.production@tgms.example'
WHERE pt.status = 'COMPLETED';

-- Delivery records across lifecycle states.
INSERT INTO deliveries (
    delivery_number, order_id, scheduled_at, delivery_address,
    delivery_notes, status, created_at, updated_at
)
SELECT seed.delivery_number, o.id, seed.scheduled_at, seed.delivery_address,
       seed.delivery_notes, seed.status,
       CURRENT_TIMESTAMP(6) - INTERVAL 2 DAY,
       CURRENT_TIMESTAMP(6) - INTERVAL seed.updated_hours HOUR
FROM orders o
JOIN (
    SELECT 'DEMO-ORD-003' order_number, 'DEMO-DEL-003' delivery_number,
           CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY scheduled_at,
           '42 Temple Road, Maharagama, Sri Lanka' delivery_address,
           'Call Kavindu before arrival.' delivery_notes,
           'SCHEDULED' status, 3 updated_hours
    UNION ALL SELECT 'DEMO-ORD-004', 'DEMO-DEL-004',
           CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY,
           '18 Lake Drive, Kandy, Sri Lanka',
           'Delivered to the reception desk.', 'DELIVERED', 24
) seed ON seed.order_number = o.order_number;

-- Role-scoped operational notifications.
INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT inventory.id, 'LOW_STOCK', 'Tropical linen stock is low',
       'Tropical Linen Blend is below its 25 metre reorder threshold.',
       'INVENTORY', material.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 2 HOUR
FROM users inventory
JOIN inventory_materials material ON material.material_code = 'DEMO-INV-FAB-LINEN-003'
WHERE inventory.email = 'demo.inventory@tgms.example';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT customer.id, 'ORDER_STATUS', 'Order ready for delivery',
       'DEMO-ORD-003 has completed production and is ready for delivery.',
       'ORDER', o.id, NULL, CURRENT_TIMESTAMP(6) - INTERVAL 3 HOUR
FROM users customer JOIN orders o ON o.order_number = 'DEMO-ORD-003'
WHERE customer.email = 'demo.customer@tgms.example';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT production.id, 'PRODUCTION_STATUS', 'Handloom saree production underway',
       'DEMO-PRD-002 is active on Sewing Line B.',
       'PRODUCTION', pt.id, CURRENT_TIMESTAMP(6) - INTERVAL 6 HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY
FROM users production JOIN production_tasks pt ON pt.task_number = 'DEMO-PRD-002'
WHERE production.email = 'demo.production@tgms.example';

INSERT INTO notifications (
    recipient_user_id, kind, title, message, source_module,
    source_record_id, read_at, created_at
)
SELECT customer.id, 'DELIVERY_STATUS', 'Delivery completed',
       'DEMO-DEL-004 was delivered successfully in Kandy.',
       'DELIVERY', delivery.id, CURRENT_TIMESTAMP(6) - INTERVAL 20 HOUR,
       CURRENT_TIMESTAMP(6) - INTERVAL 24 HOUR
FROM users customer JOIN deliveries delivery ON delivery.delivery_number = 'DEMO-DEL-004'
WHERE customer.email = 'demo.customer@tgms.example';

COMMIT;

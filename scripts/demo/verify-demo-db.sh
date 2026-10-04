#!/usr/bin/env bash
set -euo pipefail
command -v mysql >/dev/null || { echo "Missing required command: mysql (MySQL client)." >&2; exit 1; }
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
url=${DB_URL#jdbc:mysql://}; authority=${url%%/*}; rest=${url#*/}; database=${rest%%\?*}; host=${authority%%:*}
if [[ "$authority" == *:* ]]; then port=${authority##*:}; else port=3306; fi
export MYSQL_PWD="$DB_PASSWORD"; trap 'unset MYSQL_PWD' EXIT
query="SELECT
 (SELECT COUNT(*) FROM users WHERE email LIKE 'demo.%@tgms.example' AND is_active = TRUE) AS demo_users,
 (SELECT COUNT(DISTINCT role) FROM users WHERE email LIKE 'demo.%@tgms.example') AS demo_roles,
 (SELECT COUNT(*) FROM material_supplies WHERE material_code LIKE 'DEMO-%') AS supplies,
 (SELECT COUNT(*) FROM inventory_materials WHERE material_code LIKE 'DEMO-INV-%') AS inventory,
 (SELECT COUNT(*) FROM garment_products WHERE image_url LIKE '/products/demo/%') AS products,
 (SELECT COUNT(*) FROM orders WHERE order_number LIKE 'DEMO-ORD-%') AS orders,
 (SELECT COUNT(*) FROM orders o
     WHERE o.order_number LIKE 'DEMO-ORD-%'
       AND o.status = 'READY_FOR_DELIVERY'
       AND NOT EXISTS (
           SELECT 1 FROM deliveries d WHERE d.active_order_lock_id = o.id
       )
 ) AS delivery_ready_unscheduled,
 (SELECT COUNT(*) FROM quotations WHERE quotation_number LIKE 'DEMO-QUO-%') AS quotations,
 (SELECT COUNT(*) FROM production_tasks WHERE task_number LIKE 'DEMO-PRD-%') AS production_tasks,
 (SELECT COUNT(*) FROM deliveries WHERE delivery_number LIKE 'DEMO-DEL-%') AS deliveries,
 (SELECT COUNT(*) FROM order_invoices WHERE invoice_number LIKE 'DEMO-INV-%') AS invoices,
 (SELECT COUNT(*) FROM notifications WHERE recipient_user_id IN (
     SELECT id FROM users WHERE email LIKE 'demo.%@tgms.example'
 )) AS notifications;"
row=$(mysql --batch --skip-column-names --host="$host" --port="$port" --user="$DB_USERNAME" --database="$database" -e "$query")
read -r users roles supplies inventory products orders delivery_ready_unscheduled quotations production_tasks deliveries invoices notifications <<<"$row"
[[ "$users" == 6 && "$roles" == 6 && "$supplies" == 6 && "$inventory" == 6 \
   && "$products" == 4 && "$orders" == 5 && "$delivery_ready_unscheduled" -ge 1 && "$quotations" == 1 \
   && "$production_tasks" == 4 && "$deliveries" == 2 && "$invoices" == 5 \
   && "$notifications" == 4 ]] || {
  echo "[demo] verification FAILED: users=$users roles=$roles supplies=$supplies inventory=$inventory products=$products orders=$orders delivery_ready_unscheduled=$delivery_ready_unscheduled quotations=$quotations production=$production_tasks deliveries=$deliveries invoices=$invoices notifications=$notifications" >&2
  exit 1
}
echo "[demo] verification PASS: Sri Lankan users and linked catalog, supply, inventory, quotation, order, billing, production, delivery, and notification records are ready. At least one order is ready for delivery scheduling."

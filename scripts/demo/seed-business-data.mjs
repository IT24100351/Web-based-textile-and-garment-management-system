import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import os from "node:os";

// The existing demo:business:seed command calls this file. All business changes are
// submitted in one MySQL transaction; neither users nor Liquibase tables are written.
const urlText = process.env.DB_URL;
if (process.env.DEMO_DATA_ALLOWED !== "true" || !urlText?.startsWith("jdbc:mysql://")) {
  throw new Error("Local demo opt-in and a MySQL DB_URL are required.");
}
const url = new URL(urlText.slice("jdbc:".length));
const database = url.pathname.slice(1);
if (!["localhost", "127.0.0.1", "[::1]"].includes(url.hostname) || database !== "tgms") {
  throw new Error("Business reset is limited to the local tgms demo database.");
}
const mysqlArgs = [
  "--protocol=tcp", `--host=${url.hostname}`, `--port=${url.port || "3306"}`,
  `--user=${process.env.DB_USERNAME}`, `--database=${database}`,
  "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names",
];
const mysqlEnv = { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD };

function mysql(statement, input = false) {
  const result = spawnSync("mysql", input ? mysqlArgs : [...mysqlArgs, "-e", statement], {
    env: mysqlEnv,
    input: input ? statement : undefined,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(`MySQL rejected the demo reset: ${result.stderr?.trim().split("\n").at(-1) || result.error?.message}`);
  }
  return result.stdout.trim();
}

const server = mysql("SELECT @@hostname, @@port, DATABASE(), @@read_only").split("\t");
if (server[0] !== os.hostname() || server[2] !== "tgms" || server[3] !== "0") {
  throw new Error("The connected MySQL server is not this Mac's writable local tgms database.");
}

const expectedTables = new Set([
  "databasechangelog", "databasechangeloglock", "users", "email_verification_codes",
  "password_reset_tokens", "supplier_profiles", "material_supplies", "inventory_materials",
  "garment_categories", "garment_products", "garment_product_variants", "quotations",
  "quotation_items", "orders", "order_items", "order_status_history", "order_invoices",
  "order_payment_records", "production_tasks", "production_task_details",
  "production_task_material_requirements", "production_task_material_usage", "deliveries",
  "notifications",
]);
const actualTables = mysql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
  .split("\n");
if (actualTables.length !== expectedTables.size || actualTables.some((name) => !expectedTables.has(name))) {
  throw new Error("The live schema differs from the reviewed TGMS schema; reset was not started.");
}

const actors = mysql("SELECT id, role FROM users WHERE is_active = TRUE ORDER BY id")
  .split("\n").map((line) => line.split("\t"));
const roleIds = (role) => actors.filter(([, value]) => value === role).map(([id]) => Number(id));
for (const role of ["SUPPLIER", "INVENTORY_MANAGER", "PRODUCTION_MANAGER", "SALES_OFFICER", "CUSTOMER"]) {
  if (roleIds(role).length === 0) throw new Error(`An existing active ${role} user is required.`);
}
const supplier = roleIds("SUPPLIER")[0];
const inventoryManager = roleIds("INVENTORY_MANAGER")[0];
const productionManager = roleIds("PRODUCTION_MANAGER")[0];
const salesOfficers = roleIds("SALES_OFFICER");
const customers = roleIds("CUSTOMER");
const sales = (n) => salesOfficers[(n - 1) % salesOfficers.length];
const customer = (n) => customers[(n - 1) % customers.length];

function usersFingerprint() {
  const rows = mysql(`SELECT id, SHA2(CAST(JSON_ARRAY(id,email,password_hash,full_name,role,
    is_active,created_at,updated_at,email_verified_at,session_version) AS CHAR),256)
    FROM users ORDER BY id`);
  return { count: rows.split("\n").length, digest: createHash("sha256").update(rows).digest("hex") };
}
const usersBefore = usersFingerprint();

const raw = (sql) => ({ sql });
const literal = (value) => {
  if (value === null) return "NULL";
  if (typeof value === "object" && value.sql) return value.sql;
  if (typeof value === "number") return String(value);
  return `'${String(value).replaceAll("\\", "\\\\").replaceAll("'", "''")}'`;
};
const sql = [];
const push = (statement) => sql.push(`${statement};`);
function insert(table, columns, rows) {
  if (rows.length === 0) return;
  push(`INSERT INTO ${table} (${columns.join(", ")}) VALUES\n${rows
    .map((row) => `(${row.map(literal).join(", ")})`).join(",\n")}`);
}
const lookup = (table, column, key) => raw(`(SELECT id FROM ${table} WHERE ${column} = ${literal(key)})`);
const hoursAgo = (hours) => raw(`DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL ${Math.round(hours)} HOUR)`);
const hoursAhead = (hours) => raw(`DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ${Math.round(hours)} HOUR)`);
const number = (prefix, key) => `${prefix}-${createHash("md5").update(`LANKAWEAR-DEMO-${key}`).digest("hex").slice(0, 20).toUpperCase()}`;

const categories = [
  ["Men's T-Shirts", "Everyday knitted cotton tops", "ACTIVE"],
  ["Polo Shirts", "Smart casual pique and interlock polos", "ACTIVE"],
  ["Formal Shirts", "Office and occasion woven shirts", "ACTIVE"],
  ["Casual Shirts", "Relaxed woven shirts for warm weather", "ACTIVE"],
  ["Women's Tops", "Blouses and versatile everyday tops", "ACTIVE"],
  ["Dresses", "Contemporary dresses and occasionwear", "ACTIVE"],
  ["Trousers", "Chinos and office trousers", "ACTIVE"],
  ["Denim", "Durable denim garments", "ACTIVE"],
  ["Activewear", "Performance training apparel", "ACTIVE"],
  ["Hoodies and Sweatshirts", "French terry and fleece layers", "ACTIVE"],
];
const products = [
  ["Classic Cotton Crew Neck T-Shirt", 0, 2490], ["Essential V-Neck Tee", 0, 2690],
  ["Heavyweight Cotton Tee", 0, 3190], ["Premium Pique Polo Shirt", 1, 4290],
  ["Contrast Collar Polo", 1, 4590], ["Cotton Interlock Polo", 1, 4890],
  ["Oxford Long Sleeve Shirt", 2, 5790], ["Corporate Executive Shirt", 2, 6490],
  ["Easy-Care Poplin Shirt", 2, 5290], ["Linen Blend Resort Shirt", 3, 5490],
  ["Short Sleeve Utility Shirt", 3, 4990], ["Batik Accent Casual Shirt", 3, 5990],
  ["Women's Linen Blend Blouse", 4, 5190], ["Relaxed Cotton Tunic", 4, 4790],
  ["Tailored Office Blouse", 4, 5590], ["Summer Linen Midi Dress", 5, 7890],
  ["Cotton Poplin Shirt Dress", 5, 7390], ["Printed Rayon Wrap Dress", 5, 8190],
  ["Slim Fit Chino Trouser", 6, 6690], ["Ladies Office Trouser", 6, 6890],
  ["Stretch Twill Cargo Pant", 6, 7290], ["Straight Fit Denim Jeans", 7, 7590],
  ["Relaxed Denim Jacket", 7, 9290], ["Dark Wash Denim Trouser", 7, 7890],
  ["Performance Training T-Shirt", 8, 3690], ["Lightweight Running Shorts", 8, 3890],
  ["Training Track Pant", 8, 5690], ["Essential Zip Hoodie", 9, 7290],
  ["French Terry Sweatshirt", 9, 6390], ["Fleece Pullover Hoodie", 9, 7690],
];
const colors = ["Navy Blue", "Charcoal", "White", "Maroon", "Olive", "Sky Blue"];
const imageFor = (name) => `/products/demo/${name.toLowerCase().replaceAll("'", "")
  .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")}.webp`;

// code, name, type, unit, supplier quantity, price, warehouse quantity, low-stock level
const materials = [
  ["MAT-COT-001", "100% Cotton Single Jersey", "FABRIC", "metres", 9000, 890, 6500, 900],
  ["MAT-PIQ-002", "Cotton Pique Knit", "FABRIC", "metres", 4000, 1190, 2650, 500],
  ["MAT-INT-003", "Polyester Interlock", "FABRIC", "metres", 3200, 990, 1920, 400],
  ["MAT-TWL-004", "Cotton Twill", "FABRIC", "metres", 3600, 1390, 2100, 450],
  ["MAT-DEN-005", "Stretch Denim", "FABRIC", "metres", 2800, 1690, 1740, 350],
  ["MAT-VIS-006", "Viscose Dress Fabric", "FABRIC", "metres", 1900, 1270, 880, 250],
  ["MAT-LIN-007", "Linen Blend", "FABRIC", "metres", 2500, 1590, 1350, 300],
  ["MAT-FRT-008", "French Terry", "FABRIC", "metres", 3100, 1510, 1880, 390],
  ["MAT-RIB-009", "Cotton Collar Rib", "FABRIC", "metres", 1200, 840, 690, 200],
  ["MAT-POP-010", "Poly-Cotton Poplin", "FABRIC", "metres", 2600, 1180, 1490, 330],
  ["MAT-FLC-011", "Brushed Fleece Fabric", "FABRIC", "metres", 1700, 1460, 760, 240],
  ["MAT-RAY-012", "Printed Rayon Challis", "FABRIC", "metres", 1300, 1310, 85, 180],
  ["MAT-EL-013", "Knitted Elastic Tape", "RAW_MATERIAL", "metres", 5000, 115, 3200, 500],
  ["MAT-ZIP-014", "Polyester Coil Zipper", "RAW_MATERIAL", "pieces", 6800, 68, 410, 700],
  ["MAT-BTN-015", "Four-Hole Shirt Buttons", "RAW_MATERIAL", "pieces", 18000, 18, 9400, 1600],
  ["MAT-SNP-016", "Metal Snap Buttons", "RAW_MATERIAL", "pieces", 4700, 32, 2600, 500],
  ["MAT-THR-017", "Core-Spun Sewing Thread", "RAW_MATERIAL", "metres", 30000, 4, 20000, 3000],
  ["MAT-FUS-018", "Fusing Interlining", "FABRIC", "metres", 1400, 460, 95, 170],
  ["MAT-LBL-019", "Woven Brand Labels", "RAW_MATERIAL", "pieces", 12000, 16, 6800, 1200],
  ["MAT-CAR-020", "Printed Care Labels", "RAW_MATERIAL", "pieces", 16000, 11, 780, 1100],
  ["MAT-DRW-021", "Braided Cotton Drawcord", "RAW_MATERIAL", "metres", 2400, 92, 1190, 300],
  ["MAT-PKG-022", "Garment Packaging Polybags", "RAW_MATERIAL", "pieces", 14000, 25, 9000, 1400],
  ["MAT-BOX-023", "Five-Ply Shipping Cartons", "RAW_MATERIAL", "pieces", 2200, 170, 920, 280],
  ["MAT-HNG-024", "Recycled Plastic Hangers", "RAW_MATERIAL", "pieces", 4200, 44, 0, 250],
];

push("CREATE TEMPORARY TABLE demo_seed_assertion (ok TINYINT NOT NULL CHECK (ok = 1))");
push("START TRANSACTION");
// Delete dependents before parents. The schema, users and Liquibase history stay intact.
for (const table of [
  "notifications", "email_verification_codes", "password_reset_tokens", "deliveries",
  "production_task_material_usage", "production_task_material_requirements",
  "production_task_details", "production_tasks", "order_payment_records", "order_invoices",
  "order_status_history", "order_items", "orders", "quotation_items", "quotations",
  "inventory_materials", "material_supplies", "supplier_profiles",
  "garment_product_variants", "garment_products", "garment_categories",
]) push(`DELETE FROM ${table}`);

insert("supplier_profiles", ["user_id", "business_name", "contact_phone", "address", "created_at"], [
  [supplier, "Serendib Textile Sources (Pvt) Ltd", "+94 11 274 6182",
    "No. 145, Export Processing Zone Road, Biyagama, Sri Lanka", hoursAgo(3200)],
]);
insert("material_supplies", [
  "supplier_id", "material_code", "material_name", "material_description", "quantity",
  "unit_of_measure", "unit_price", "delivery_lead_time_days", "delivery_notes", "status", "created_at",
], materials.map(([code, name, type, unit, quantity, price], index) => [
  raw(`(SELECT id FROM supplier_profiles WHERE user_id = ${supplier})`), code, name,
  `${type === "FABRIC" ? "Apparel fabric" : "Garment trim"} supplied in shade-controlled commercial batches.`,
  quantity, unit, price, 2 + index % 7,
  "Check batch shade and quantity against the receiving note.", index >= 22 ? "INACTIVE" : "ACTIVE",
  hoursAgo(2600 - index * 14),
]));
insert("inventory_materials", [
  "source_material_supply_id", "material_code", "material_name", "material_description",
  "material_type", "unit_of_measure", "current_quantity", "low_stock_threshold", "status", "created_at",
], materials.map(([code, name, type, unit, , , stock, threshold], index) => [
  lookup("material_supplies", "material_code", code), `INV-${code}`, name,
  `Warehouse stock linked to supplier lot ${code}.`, type, unit, stock, threshold,
  index === 23 ? "INACTIVE" : "ACTIVE", hoursAgo(2450 - index * 12),
]));

insert("garment_categories", ["name", "description", "status", "created_at"],
  categories.map(([name, description, status], index) => [name, description, status, hoursAgo(4200 - index * 20)]));
insert("garment_products", [
  "category_id", "name", "description", "image_url", "status", "created_at",
], products.map(([name, category, price], index) => [
  raw(`(SELECT id FROM garment_categories WHERE name = ${literal(categories[category][0])})`),
  name, `LankaWear ${name.toLowerCase()} made for reliable fit, fabric comfort and careful finishing.`,
  imageFor(name), index < 27 ? "ACTIVE" : "INACTIVE", hoursAgo(3900 - index * 22),
]));
const variantRows = products.flatMap(([name, category, price], index) => {
  const sizes = [6, 7].includes(category) ? ["30", "32", "34"] : ["S", "M", "L"];
  return sizes.map((size, variant) => [
    lookup("garment_products", "name", name), size,
    colors[(index + variant) % colors.length], price + variant * 125 + (index % 3) * 35,
    variant === 2 && index % 6 === 0 ? "UNAVAILABLE" : "AVAILABLE",
    hoursAgo(3800 - index * 20),
  ]);
});
insert("garment_product_variants", ["product_id", "size", "color", "price", "status", "created_at"], variantRows);

const productVariant = (index, variant = 0) => {
  const [name, category, price] = products[index];
  const size = [6, 7].includes(category) ? ["30", "32", "34"][variant] : ["S", "M", "L"][variant];
  const color = colors[(index + variant) % colors.length];
  return { name, size, color, price: price + variant * 125 + (index % 3) * 35, image: imageFor(name) };
};
const variantId = (product) => raw(`(SELECT v.id FROM garment_product_variants v
  JOIN garment_products p ON p.id = v.product_id WHERE p.name = ${literal(product.name)}
  AND v.size = ${literal(product.size)} AND v.color = ${literal(product.color)})`);

const quotations = Array.from({ length: 16 }, (_, offset) => {
  const n = offset + 1;
  return { n, code: number("QUO", `Q${n}`), customerId: customer(n), salesId: sales(n), ageHours: 190 + n * 165 };
});
insert("quotations", ["quotation_number", "customer_id", "issued_by_user_id", "issued_at"],
  quotations.map((q) => [q.code, q.customerId, q.salesId, hoursAgo(q.ageHours)]));
const quotationItems = quotations.flatMap((q) => [0, 1].map((line) => {
  const product = productVariant((q.n * 5 + line * 9) % 26, line % 2);
  return [lookup("quotations", "quotation_number", q.code), lookup("garment_products", "name", product.name),
    variantId(product), product.name, q.n % 3 === 0 ? 80 + q.n * 4 : 12 + q.n * 3,
    product.size, product.color, product.price];
}));
insert("quotation_items", [
  "quotation_id", "product_id", "variant_id", "product_name_snapshot", "quantity",
  "selected_size", "selected_color", "unit_price_snapshot",
], quotationItems);

function orderStatus(n) {
  if (n <= 4) return "PENDING";
  if (n <= 8) return "CONFIRMED";
  if (n <= 13) return "IN_PRODUCTION";
  if (n <= 24 || n === 35) return "READY_FOR_DELIVERY";
  if (n <= 31) return "COMPLETED";
  return "CANCELLED";
}
function orderAge(n) {
  if (n <= 4) return 24 + n * 13;
  if (n <= 8) return 110 + n * 14;
  if (n <= 13) return 210 + (n - 9) * 18;
  if (n <= 21) return 270 + (n - 14) * 20;
  if (n <= 24) return 390 + (n - 22) * 20;
  if (n <= 31) return 900 + (n - 25) * 330;
  if (n <= 34) return 180 + (n - 32) * 150;
  return 320;
}
function lastEventAge(order) {
  if (order.status === "PENDING") return order.ageHours;
  if (order.status === "CANCELLED") return order.ageHours - 12;
  if (order.status === "CONFIRMED") return order.ageHours - 24;
  if (order.status === "IN_PRODUCTION") return order.ageHours - 48;
  if (order.status === "COMPLETED") return order.ageHours - 180;
  return order.ageHours - 120;
}
const orders = Array.from({ length: 35 }, (_, offset) => {
  const n = offset + 1;
  return { n, code: number("ORD", `O${n}`), status: orderStatus(n), customerId: customer(n), ageHours: orderAge(n) };
});
insert("orders", ["customer_id", "order_number", "status", "created_at", "updated_at"],
  orders.map((o) => [o.customerId, o.code, o.status, hoursAgo(o.ageHours), hoursAgo(lastEventAge(o))]));
const orderItems = [];
const orderUnits = new Map();
for (const order of orders) {
  const lineCount = order.n % 4 === 0 ? 3 : order.n % 3 === 0 ? 2 : 1;
  let units = 0;
  for (let line = 0; line < lineCount; line += 1) {
    const product = productVariant((order.n * 7 + line * 5) % 26, line % 2);
    const quantity = order.n % 7 === 0 ? 90 + order.n + line * 15
      : order.n % 3 === 0 ? 24 + order.n + line * 8 : 4 + order.n % 12 + line * 3;
    units += quantity;
    orderItems.push([lookup("orders", "order_number", order.code), lookup("garment_products", "name", product.name),
      variantId(product), product.name, product.image, quantity, product.size, product.color, product.price,
      hoursAgo(order.ageHours)]);
  }
  orderUnits.set(order.n, units);
}
insert("order_items", [
  "order_id", "product_id", "variant_id", "product_name_snapshot", "product_image_url_snapshot",
  "quantity", "selected_size", "selected_color", "unit_price_snapshot", "created_at",
], orderItems);

const history = [];
for (const order of orders) {
  const add = (from, to, age, actor) => history.push([
    lookup("orders", "order_number", order.code), from, to, actor, hoursAgo(age),
  ]);
  if (order.status === "PENDING") continue;
  if (order.status === "CANCELLED") {
    add("PENDING", "CANCELLED", order.ageHours - 12, sales(order.n));
    continue;
  }
  add("PENDING", "CONFIRMED", order.ageHours - 24, sales(order.n));
  if (order.status === "CONFIRMED") continue;
  add("CONFIRMED", "IN_PRODUCTION", order.ageHours - 48, productionManager);
  if (order.status === "IN_PRODUCTION") continue;
  add("IN_PRODUCTION", "READY_FOR_DELIVERY", order.ageHours - 120, productionManager);
  if (order.status === "COMPLETED") {
    add("READY_FOR_DELIVERY", "COMPLETED", order.ageHours - 180, sales(order.n));
  }
}
insert("order_status_history", ["order_id", "from_status", "to_status", "changed_by_user_id", "changed_at"], history);

push(`INSERT INTO order_invoices (order_id, invoice_number, total_amount, issued_by_user_id, issued_at)
  SELECT o.id, REPLACE(o.order_number, 'ORD-', 'INV-'), SUM(oi.quantity * oi.unit_price_snapshot),
    ${sales(1)}, DATE_ADD(o.created_at, INTERVAL 8 HOUR)
  FROM orders o JOIN order_items oi ON oi.order_id = o.id
  WHERE o.status <> 'CANCELLED' GROUP BY o.id, o.order_number, o.created_at`);
push(`INSERT INTO order_payment_records (order_id, invoice_id, payment_status, amount_paid,
    payment_method, payment_reference, note, recorded_by_user_id, recorded_at, updated_at)
  SELECT o.id, i.id,
    CASE WHEN o.status = 'PENDING' OR MOD(o.id, 11) = 0 THEN 'UNPAID'
         WHEN o.status = 'COMPLETED' OR MOD(o.id, 3) = 0 THEN 'PAID'
         ELSE 'PARTIALLY_PAID' END,
    CASE WHEN o.status = 'PENDING' OR MOD(o.id, 11) = 0 THEN 0.00
         WHEN o.status = 'COMPLETED' OR MOD(o.id, 3) = 0 THEN i.total_amount
         ELSE ROUND(i.total_amount * 0.40, 2) END,
    CASE WHEN o.status = 'PENDING' OR MOD(o.id, 11) = 0 THEN NULL
         WHEN MOD(o.id, 4) = 0 THEN 'CASH' ELSE 'BANK_TRANSFER' END,
    CASE WHEN o.status = 'PENDING' OR MOD(o.id, 11) = 0 THEN NULL
         ELSE CONCAT('CEFT-', RIGHT(o.order_number, 8)) END,
    'Demo payment record against the issued LKR invoice.', ${sales(1)},
    DATE_ADD(i.issued_at, INTERVAL 2 HOUR), DATE_ADD(i.issued_at, INTERVAL 2 HOUR)
  FROM order_invoices i JOIN orders o ON o.id = i.order_id`);

const tasks = [];
function addTask(order, suffix, status, unitShare, qc = "PENDING") {
  tasks.push({ order, code: number("PRD", `${order.n}-${suffix}`), status, unitShare, qc });
}
for (const order of orders) {
  const n = order.n;
  if (n === 5 || n === 6) addTask(order, "A", "PENDING", 1);
  if (n >= 9 && n <= 13) addTask(order, "A", "IN_PROGRESS", n === 10 ? 0.6 : 1, n === 11 ? "FAILED" : "PENDING");
  if (n === 10) addTask(order, "B", "COMPLETED", 0.4, "PASSED");
  if (n === 13) addTask(order, "B", "PENDING", 0.3);
  if ((n >= 14 && n <= 31) || n === 35) addTask(order, "A", "COMPLETED", n === 17 || n === 27 ? 0.6 : 1, "PASSED");
  if (n === 17 || n === 27) addTask(order, "B", "COMPLETED", 0.4, "PASSED");
}
const taskId = (task) => lookup("production_tasks", "task_number", task.code);
insert("production_tasks", [
  "task_number", "order_id", "status", "started_at", "completed_at", "created_at", "updated_at",
  "quality_control_result", "quality_checked_by_user_id", "quality_checked_at",
], tasks.map((task) => {
  const age = task.order.ageHours;
  const started = task.status === "PENDING" ? null : hoursAgo(age - 55);
  const completed = task.status === "COMPLETED" ? hoursAgo(age - 100) : null;
  const checked = task.qc === "PENDING" ? null : hoursAgo(age - 90);
  return [task.code, lookup("orders", "order_number", task.order.code), task.status,
    started, completed, hoursAgo(age - 52),
    completed || checked || started || hoursAgo(age - 52), task.qc,
    checked ? productionManager : null, checked];
}));
insert("production_task_details", [
  "production_task_id", "work_details", "work_assignment", "work_notes", "created_at", "updated_at",
], tasks.map((task) => [taskId(task),
  `Cut, assemble, stitch and finish order ${task.order.code}; inspect seams, dimensions and packing.`,
  task.order.n % 2 ? "Cutting and stitching line A" : "Finishing and quality line B",
  task.qc === "FAILED" ? "Quality check failed; inspect stitching before the next review."
    : "Follow approved size and color selections on the order items.",
  hoursAgo(task.order.ageHours - 52), hoursAgo(task.order.ageHours - 48),
]));
const coreMaterials = ["MAT-COT-001", "MAT-THR-017", "MAT-PKG-022"];
const requirementRows = [];
const usageRows = [];
for (const task of tasks) {
  const units = Math.max(1, Math.round(orderUnits.get(task.order.n) * task.unitShare));
  const required = [Number((units * 1.6).toFixed(3)), units * 3, units];
  coreMaterials.forEach((code, index) => {
    const inventory = lookup("inventory_materials", "material_code", `INV-${code}`);
    requirementRows.push([taskId(task), inventory, required[index], hoursAgo(task.order.ageHours - 52)]);
    if (task.status === "COMPLETED" || (task.status === "IN_PROGRESS" && task.order.n % 2 === 1)) {
      usageRows.push([taskId(task), inventory,
        task.status === "COMPLETED" ? required[index] : Number((required[index] * 0.35).toFixed(3)),
        productionManager, hoursAgo(task.order.ageHours - 75)]);
    }
  });
}
insert("production_task_material_requirements", [
  "production_task_id", "inventory_material_id", "required_quantity", "created_at",
], requirementRows);
insert("production_task_material_usage", [
  "production_task_id", "inventory_material_id", "quantity_used", "recorded_by_user_id", "recorded_at",
], usageRows);
push(`UPDATE inventory_materials i JOIN
  (SELECT inventory_material_id, SUM(quantity_used) AS used_quantity
   FROM production_task_material_usage GROUP BY inventory_material_id) u
  ON u.inventory_material_id = i.id
  SET i.current_quantity = i.current_quantity - u.used_quantity`);

const deliveryOrders = orders.filter((o) => (o.n >= 22 && o.n <= 31) || o.n === 35);
const deliveryStatus = (n) => n === 35 ? "CANCELLED" : n >= 25 ? "DELIVERED"
  : n === 23 ? "OUT_FOR_DELIVERY" : "SCHEDULED";
const addresses = [
  "No. 42, Galle Road, Colombo 03, Sri Lanka", "18 Peradeniya Road, Kandy, Sri Lanka",
  "76 Main Street, Galle, Sri Lanka", "12 Negombo Road, Wattala, Sri Lanka",
  "33 Temple Road, Nugegoda, Sri Lanka", "45 Hospital Road, Jaffna, Sri Lanka",
  "14 Lake Road, Kurunegala, Sri Lanka", "28 Main Street, Matara, Sri Lanka",
  "51 Station Road, Ratnapura, Sri Lanka", "9 Beach Road, Trincomalee, Sri Lanka",
  "67 New Town Road, Anuradhapura, Sri Lanka",
];
insert("deliveries", [
  "delivery_number", "order_id", "active_order_lock_id", "scheduled_at", "delivery_address",
  "delivery_notes", "status", "created_at", "updated_at",
], deliveryOrders.map((order, index) => {
  const status = deliveryStatus(order.n);
  return [number("DEL", `D${order.n}`), lookup("orders", "order_number", order.code),
    status === "CANCELLED" ? null : lookup("orders", "order_number", order.code),
    status === "SCHEDULED" ? hoursAhead(24 + index * 12) : hoursAgo(order.ageHours - 150),
    addresses[index], status === "CANCELLED" ? "Customer requested rescheduling after dispatch planning."
      : "Verify garment count against the invoice and obtain proof of handover.", status,
    hoursAgo(order.ageHours - 135), status === "DELIVERED" ? hoursAgo(order.ageHours - 180)
      : status === "CANCELLED" ? hoursAgo(order.ageHours - 160) : hoursAgo(2),
  ];
}));

const notifications = [];
const notify = (recipient, kind, title, message, module, id, age, read) => notifications.push([
  recipient, kind, title, message, module, id,
  read ? hoursAgo(Math.max(1, age - 4)) : null, hoursAgo(age),
]);
for (const order of orders) {
  notify(order.customerId, "ORDER_STATUS", `Order ${order.code}: ${order.status.replaceAll("_", " ")}`,
    `Your garment order ${order.code} is ${order.status.toLowerCase().replaceAll("_", " ")}.`,
    "ORDER", lookup("orders", "order_number", order.code), lastEventAge(order), order.n % 3 === 0);
}
for (const code of ["MAT-RAY-012", "MAT-ZIP-014", "MAT-FUS-018", "MAT-CAR-020", "MAT-HNG-024"]) {
  notify(inventoryManager, "LOW_STOCK", `Low stock: ${code}`,
    `Warehouse material ${code} needs replenishment or review.`, "INVENTORY",
    lookup("inventory_materials", "material_code", `INV-${code}`), 12, false);
}
for (const task of tasks.filter((t) => [9, 10, 11, 17, 27].includes(t.order.n))) {
  notify(productionManager, "PRODUCTION_STATUS", `Production task ${task.code}`,
    `Production task ${task.code} is ${task.status.toLowerCase().replaceAll("_", " ")}.`,
    "PRODUCTION", taskId(task), task.order.ageHours - 90, task.status === "COMPLETED");
}
for (const order of deliveryOrders.filter((o) => [22, 23, 25, 35].includes(o.n))) {
  notify(order.customerId, "DELIVERY_STATUS", `Delivery update for ${order.code}`,
    `Delivery for order ${order.code} is ${deliveryStatus(order.n).toLowerCase().replaceAll("_", " ")}.`,
    "DELIVERY", lookup("deliveries", "delivery_number", number("DEL", `D${order.n}`)),
    order.ageHours - 150, order.n === 25);
}
insert("notifications", [
  "recipient_user_id", "kind", "title", "message", "source_module",
  "source_record_id", "read_at", "created_at",
], notifications);

function assertSql(condition) {
  push(`INSERT INTO demo_seed_assertion (ok) SELECT IF(${condition}, 1, 0)`);
}
assertSql("(SELECT COUNT(*) FROM users) = " + usersBefore.count);
assertSql("(SELECT COUNT(*) FROM orders) = 35 AND (SELECT COUNT(*) FROM garment_products) = 30");
assertSql("(SELECT COUNT(*) FROM production_tasks) = 30 AND (SELECT COUNT(*) FROM deliveries) = 11");
assertSql(`(SELECT COUNT(*) FROM orders o LEFT JOIN deliveries d ON d.order_id = o.id
  WHERE o.status = 'READY_FOR_DELIVERY' AND d.id IS NULL
  AND EXISTS (SELECT 1 FROM production_tasks t WHERE t.order_id = o.id AND t.status = 'COMPLETED')) = 8`);
assertSql(`(SELECT COUNT(*) FROM inventory_materials WHERE current_quantity < 0) = 0`);
push("COMMIT");

mysql(sql.join("\n"), true);
const usersAfter = usersFingerprint();
if (usersBefore.count !== usersAfter.count || usersBefore.digest !== usersAfter.digest) {
  throw new Error("User fingerprint changed; inspect the database immediately.");
}
const summary = mysql(`SELECT
  (SELECT COUNT(*) FROM users), (SELECT COUNT(*) FROM garment_categories),
  (SELECT COUNT(*) FROM garment_products), (SELECT COUNT(*) FROM garment_product_variants),
  (SELECT COUNT(*) FROM material_supplies), (SELECT COUNT(*) FROM inventory_materials),
  (SELECT COUNT(*) FROM quotations), (SELECT COUNT(*) FROM orders),
  (SELECT COUNT(*) FROM production_tasks), (SELECT COUNT(*) FROM deliveries),
  (SELECT COUNT(*) FROM order_invoices), (SELECT COUNT(*) FROM order_payment_records),
  (SELECT COUNT(*) FROM notifications),
  (SELECT COUNT(*) FROM orders o LEFT JOIN deliveries d ON d.order_id = o.id
    WHERE o.status = 'READY_FOR_DELIVERY' AND d.id IS NULL)`);
console.log(`[TGMS] Business demo reset complete. Counts (users unchanged, categories, products, variants, supplies, inventory, quotations, orders, tasks, deliveries, invoices, payments, notifications, unscheduled ready): ${summary.replaceAll("\t", ", ")}.`);

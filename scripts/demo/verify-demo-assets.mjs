import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const required = [
  'database/demo/reset-and-seed.sql',
  'database/demo/README.md',
  'frontend/public/products/demo/ceylon-batik-resort-shirt.jpg',
  'frontend/public/products/demo/lotus-handloom-saree.jpg',
  'frontend/public/products/demo/sigiriya-linen-dress.jpg',
  'frontend/public/products/demo/colombo-classic-polo.jpg',
  'scripts/demo/reset-demo.sh',
  'scripts/demo/verify-demo-db.sh',
  'docs/demo/TGMS-85-demo-presentation-runbook.md',
  'docs/handover/TGMS-85-final-handover.md',
  'docs/handover/TGMS-85-api-database-module-handoffs.md',
]
for (const rel of required) {
  if (!fs.existsSync(path.join(root, rel))) throw new Error(`Missing TGMS-85 asset: ${rel}`)
}
const sql = fs.readFileSync(path.join(root, 'database/demo/reset-and-seed.sql'), 'utf8')
const resetScript = fs.readFileSync(path.join(root, 'scripts/demo/reset-demo.sh'), 'utf8')
const roles = ['ADMINISTRATOR','SUPPLIER','INVENTORY_MANAGER','PRODUCTION_MANAGER','SALES_OFFICER','CUSTOMER']
const emails = ['admin','supplier','inventory','production','sales','customer'].map(x => `demo.${x}@tgms.example`)
for (const role of roles) if (!sql.includes(`'${role}'`)) throw new Error(`Demo SQL missing role ${role}`)
for (const email of emails) if (!sql.includes(email)) throw new Error(`Demo SQL missing ${email}`)
for (const marker of ['DEMO-FAB-BATIK-001','DEMO-INV-FAB-LINEN-003','Ceylon Batik Resort Shirt','DEMO-ORD-001']) {
  if (!sql.includes(marker)) throw new Error(`Demo SQL missing marker ${marker}`)
}
if (!sql.includes('START TRANSACTION;') || !sql.includes('COMMIT;')) throw new Error('Demo reset must be transactional')
if (/\b(TRUNCATE|DROP\s+TABLE|FOREIGN_KEY_CHECKS)\b/i.test(sql)) throw new Error('Demo reset must not truncate/drop tables or disable foreign keys')
if (/\$2[aby]\$\d{2}\$/.test(sql)) throw new Error('Demo SQL must not contain a reusable bcrypt password hash')
if (!sql.includes('@demo_password_hash')) throw new Error('Demo SQL must consume the runtime-generated password hash')
for (const phrase of ['DEMO_USER_PASSWORD', 'htpasswd', '@demo_password_hash']) {
  if (!resetScript.includes(phrase)) throw new Error(`Demo reset missing runtime credential control: ${phrase}`)
}
const migrations = [...fs.readdirSync(path.join(root, 'database/migrations/changes'))].map(name => fs.readFileSync(path.join(root, 'database/migrations/changes', name), 'utf8')).join('\n')
for (const table of ['users','supplier_profiles','material_supplies','inventory_materials','garment_categories','garment_products','garment_product_variants','orders','order_items','production_tasks','production_task_details','production_task_material_requirements','production_task_material_usage','deliveries','notifications','password_reset_tokens','quotations','quotation_items','order_invoices','order_payment_records','order_status_history']) {
  if (!migrations.includes(`CREATE TABLE ${table}`) && !migrations.includes(`tableName="${table}"`)) throw new Error(`Demo SQL references unknown final schema table: ${table}`)
}
if (fs.readFileSync(path.join(root, 'database/migrations/db.changelog-master.xml'), 'utf8').includes('database/demo')) {
  throw new Error('Demo data must not be included by Liquibase')
}
const runbook = fs.readFileSync(path.join(root, 'docs/demo/TGMS-85-demo-presentation-runbook.md'), 'utf8')
for (const phrase of ['Product → Order','Supplier → Inventory','READY_FOR_DELIVERY','no third-party payment gateway','no AI forecasting','no automated production optimization']) {
  if (!runbook.includes(phrase)) throw new Error(`Runbook missing required presentation statement: ${phrase}`)
}
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'))
for (const script of ['demo:reset','demo:verify','demo:assets:check','handover:verify']) {
  if (!pkg.scripts?.[script]) throw new Error(`package.json missing ${script}`)
}
for (const imagePath of [
  '/products/demo/ceylon-batik-resort-shirt.jpg',
  '/products/demo/lotus-handloom-saree.jpg',
  '/products/demo/sigiriya-linen-dress.jpg',
  '/products/demo/colombo-classic-polo.jpg',
]) {
  if (!sql.includes(imagePath)) throw new Error(`Demo product SQL missing image path ${imagePath}`)
}
console.log('[demo] asset verification PASS: six roles, Sri Lankan source records, product media, reset/runbook/handover contracts present.')

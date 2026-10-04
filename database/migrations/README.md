# Database Migration Workflow and Conventions

This directory is the source of truth for TGMS database changes. The Spring Boot
application packages these files and Liquibase applies pending changesets in order.
Never make an application schema change with undocumented manual SQL.

## Technology and Current Schema

- Database: MySQL.
- Migration engine: Liquibase 5, managed by Spring Boot's dependency management.
- Runtime connection pool: Spring JDBC with HikariCP.
- Development commands: Liquibase Maven plugin through the repository's Maven Wrapper.
- Automated verification: H2 in MySQL compatibility mode.

The migration chain covers the shared account, catalog, supplier, inventory, order,
quotation, billing, production, delivery, password-recovery, and notification schemas.
Liquibase also creates its own `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`
infrastructure tables; these are not application tables.

```text
database/migrations/
├── changes/
│   ├── baseline.sql
│   ├── create-users.sql
│   ├── create-garment-product-catalog.sql
│   ├── create-supplier-profiles.sql
│   ├── create-material-supplies.sql
│   ├── create-inventory-materials.sql
│   ├── create-customer-orders.sql
│   ├── create-order-items.sql
│   ├── create-order-status-history.sql
│   ├── create-customer-quotations.sql
│   ├── create-order-billing.sql
│   ├── create-production-tasks.sql
│   ├── create-production-task-details.sql
│   ├── create-production-task-material-requirements.sql
│   ├── create-production-task-material-usage.sql
│   ├── add-production-quality-control.sql
│   ├── create-deliveries.sql
│   ├── enable-safe-delivery-cancellation.xml
│   ├── create-password-reset-tokens.sql
│   └── create-notifications.sql
├── db.changelog-master.xml
└── README.md
```

The files above are shown in dependency order. Their number-free names do not control
execution; the explicit include sequence in `db.changelog-master.xml` does.

## Local Configuration

Create the ignored environment file from the example:

```bash
cp backend/.env.example backend/.env
```

Create an empty MySQL database and a least-privilege development account. Replace the
placeholder password and keep it consistent with `backend/.env`:

```sql
CREATE DATABASE tgms CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'tgms_user'@'localhost' IDENTIFIED BY '<local-password>';
GRANT ALL PRIVILEGES ON tgms.* TO 'tgms_user'@'localhost';
```

Database configuration uses `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `DB_DRIVER`.
The committed JDBC URL sets the MySQL session time zone to UTC. Credentials and local
`.env` files must never be committed.

## Apply, Inspect, and Roll Back

Run all commands from the repository root:

| Command | Purpose |
|---|---|
| `npm run db:status` | Show applied and pending changesets |
| `npm run db:migrate` | Apply every pending changeset in order |
| `npm run db:rollback:preview` | Preview rollback SQL only when the latest changeset has a genuine rollback |
| `npm run db:rollback` | Roll back a genuinely reversible latest changeset in a disposable development database |
| `npm run db:verify` | Apply the full migration chain and exercise schema plus legacy-data migration tests |

Rollback is deliberately guarded. Preview the SQL first and only run rollback when the
changeset has a **genuine** rollback that can restore both schema and data. The Inventory,
Material Supply, and Product `DISCONTINUED` cleanup changesets are intentionally
**irreversible**: they can permanently delete unreferenced legacy rows and convert retained
legacy statuses. They therefore do not define fake rollback SQL. Recovering pre-cleanup data
requires restoring a database backup, not merely widening a CHECK constraint.

For a reversible changeset, confirm the target is a disposable local database, set
`DB_ALLOW_ROLLBACK=true`, perform the rollback, then restore the setting to `false`. Never
use rollback as a substitute for a backup on shared, staging, or production data.

Spring Boot also applies pending changesets during application startup. A failed
migration prevents the API from starting instead of allowing the code and schema to
drift apart.

## Add a Migration

1. Update the branch and run `npm run db:migrate`.
2. Add one Liquibase formatted SQL file under `changes/` with a descriptive,
   number-free kebab-case name, such as `create-example-table.sql`.
3. Give the changeset a unique immutable ID and author, for example
   `--changeset initials:create-example-table`.
4. Include the file once, after every migration that provides a table, column, index,
   or constraint it depends on, from `db.changelog-master.xml`.
5. Add explicit rollback SQL only when it genuinely restores the affected schema and data.
   For destructive/irreversible data cleanup, omit rollback and document the backup/restore
   requirement instead of pretending deleted data can be reconstructed.
6. Run `npm run db:verify`, then verify against MySQL with `db:status` and `db:migrate`.
   Use `db:rollback:preview` / `db:rollback` only for a genuinely reversible latest changeset.
7. Run the complete application regression suite.

Never edit, rename, reorder, or reuse the ID of a changeset that has been shared or
applied outside a disposable local database. Correct it with a new forward migration.
Keep one cohesive schema change per changeset and separate large data backfills from
schema changes. Existing files that were renamed retain their original database
identity through Liquibase `logicalFilePath`; do not remove or change those compatibility
paths, because deployed databases still record them in `DATABASECHANGELOG`.

## Naming Conventions

- Use lowercase `snake_case` for schemas, tables, columns, indexes, and constraints.
- Use plural nouns for tables, such as `users`.
- Use singular descriptive column names. Boolean columns read as predicates, such as
  `is_active`.
- Avoid SQL reserved words and quoted, case-sensitive identifiers.
- Use deterministic names: `pk_<table>`, `fk_<table>_<column>`,
  `uq_<table>_<columns>`, `ck_<table>_<rule>`, and `idx_<table>_<columns>`.

## Keys and Relationships

- Persisted entity tables use `id BIGINT AUTO_INCREMENT` as their primary key. Java
  code represents these IDs with `Long`.
- IDs are non-null, unique, stable, never reused, and never changed for business
  reasons.
- Foreign-key columns use `<referenced_entity>_id` and the same `BIGINT` type.
- Enforce relationships with named database foreign keys and index foreign-key
  columns used for joins or filtering.
- Define `ON DELETE` and `ON UPDATE` explicitly. Default to `RESTRICT` where history or
  dependent records exist; use cascading only for a clearly owned aggregate.
- Reference another module's source-of-truth row by ID. Do not copy its authoritative
  names, prices, ownership, roles, or statuses into another module as master data.

## Timestamps and Record History

- Persist timestamps as UTC with MySQL `TIMESTAMP(6)` and convert only at application
  boundaries.
- Mutable tables use non-null `created_at` and `updated_at`; use
  `CURRENT_TIMESTAMP(6)` defaults and update `updated_at` on modification.
- Other timestamp names describe their business event, such as `scheduled_at`.
- Add nullable `deleted_at` only when an approved requirement introduces soft delete.
- Preserve referenced business history by restricting destructive deletes. Use current business
  statuses for operational lifecycle state; unreferenced CRUD-owned records may be hard-deleted
  when the owning module explicitly supports it.

## Irreversible Legacy Status Cleanup

Three forward-only cleanup migrations remove obsolete fake-delete states from current CRUD:

- Inventory Material: unreferenced `DISCONTINUED` rows are deleted; Production-referenced
  rows become `INACTIVE`.
- Material Supply: unreferenced `DISCONTINUED` rows are deleted; Inventory-referenced rows
  become `INACTIVE`.
- Product: unreferenced `DISCONTINUED` products and owned variants are deleted; products
  referenced by Orders or Quotations become `INACTIVE`, and retained `DISCONTINUED` variants
  become `UNAVAILABLE`.

These migrations preserve all referenced Production, Inventory, Order, and Quotation history.
They are intentionally irreversible because widening the old status constraint cannot restore
rows that were deleted or determine which current `INACTIVE` / `UNAVAILABLE` rows originally
held legacy values. Restore a pre-migration backup if the pre-cleanup dataset is ever required.

## Safety Rules

- Add `NOT NULL`, unique, check, and foreign-key constraints for real invariants.
- Validate IDs, ownership, statuses, and numeric ranges at the API boundary and again
  in domain/service logic; database constraints provide the final integrity layer.
- Use Spring transactions when one operation must update multiple records atomically.
- Never trust client-supplied prices, totals, roles, ownership IDs, or status changes.
- Never put credentials, environment-specific hostnames, or business seed data in a
  migration.
- Review rollback SQL before execution and back up data before any destructive schema
  operation.

## Current Shared Account Table

`users` is the authentication source of truth. It uses the standard `BIGINT` primary
key and timestamps, a case-normalized unique email, a bcrypt `password_hash`, full
name, active flag, and one controlled role. Supported stored roles are
`ADMINISTRATOR`, `SUPPLIER`, `INVENTORY_MANAGER`, `PRODUCTION_MANAGER`,
`SALES_OFFICER`, and `CUSTOMER`.

Public registration is an application rule that always inserts `CUSTOMER`. TGMS-70
uses this same table as the account source of truth for Administrator-controlled
internal account provisioning, role changes, and active/inactive access; it needs no
new schema because `role` and `is_active` are already authoritative columns. Never add
plaintext passwords, tokens, development users, or staff seed credentials to a
migration.

## Supplier Profile Contract

Supplier Management owns `supplier_profiles`. Its generated `id` is the stable
supplier identifier that later Supply and Inventory records must reference as
`supplier_id`. Each profile contains required `business_name`, `contact_phone`, and
`address` values plus the standard timestamps.

`supplier_profiles.user_id` is a required, unique foreign key to `users.id`, giving one
authenticated account at most one supplier profile. The relationship uses
`ON UPDATE RESTRICT ON DELETE RESTRICT` to preserve supplier history. Account-holder
name, login email, password hash, role, and active state remain in `users` and are not
duplicated in the supplier table.

The database guarantees account existence and one-to-one profile ownership. The
TGMS-21 Supplier service additionally verifies that the current linked account remains
active and has the controlled `SUPPLIER` role before reading or writing a profile;
SQL `CHECK` constraints cannot reference a different table. It derives the linked user
only from the authenticated session and never accepts ownership IDs from the client.
Deactivating an account does not delete its supplier identity or historical references.
No supplier seed data is included.

TGMS-21 adds no changeset: it uses the TGMS-20 table through the singular
`GET/PUT /api/supplier-profile` application contract. PUT creates at most one profile
for the authenticated supplier or updates that row without changing its stable `id`,
`user_id`, or `created_at`.

## Material Supply Contract

Supplier Management owns `material_supplies`. Its generated `id` is the stable supply
identifier that later Inventory records may reference as `material_supply_id`.
Every row has a required `supplier_id` foreign key to `supplier_profiles.id`; restrictive
update/delete behavior preserves the supplier relationship and future history.

| Field | Contract |
|---|---|
| `material_code` | Required supplier-scoped material identifier, maximum 64 characters |
| `material_name` / `material_description` | Required name and optional description, with 160/500-character limits |
| `quantity` / `unit_of_measure` | Non-negative `DECIMAL(14,3)` quantity and required unit label |
| `unit_price` | Non-negative exact `DECIMAL(12,2)` current unit price |
| `delivery_lead_time_days` / `delivery_notes` | Non-negative lead time and optional delivery information |
| `status` | Controlled current `ACTIVE` or `INACTIVE` availability value |
| `created_at` / `updated_at` | Required UTC-compatible `TIMESTAMP(6)` audit timestamps |

`(supplier_id, material_code)` is unique, allowing different suppliers to use the same
material code while preventing duplicate identifiers within one supplier catalog.
Quantity, price, and delivery lead time allow zero to represent an inactive or currently
unavailable offer, but reject all negative values. TGMS-23 applies the stronger rule
that newly created active records require positive quantity and unit price.

Inventory Management must reference the stable supply `id`; it must not duplicate
Supplier Management's material offer, supplier ownership, current price, delivery lead
time, or lifecycle status as competing master data. No Inventory table, API, seed data,
or supply CRUD behavior is added by TGMS-22.

## Garment Product Catalog Contract

Garment Product Management owns the catalog tables and their stable IDs:

- `garment_categories` owns category name, description, and active/inactive status.
- `garment_products` owns the product name, description, category relationship, and
  current `ACTIVE` or `INACTIVE` lifecycle status.
- `garment_product_variants` owns each sellable size/color combination, its exact
  `DECIMAL(12,2)` price, and `AVAILABLE` or `UNAVAILABLE` status.

The variant unique constraint prevents duplicate size/color combinations within one
product. Foreign keys use `RESTRICT` so referenced catalog history cannot be deleted.
Later modules must reference these records by ID and must not copy them as competing
master data. An Order item may reference a variant ID, but it must also snapshot the
accepted selling price so later catalog price changes cannot rewrite order history.

Catalog selection for a new order requires an active category, active product, and
available variant. Products already referenced by Orders or Quotations are protected from
hard deletion; `INACTIVE` products and `UNAVAILABLE` variants remain usable as retained
operational/history records.

TGMS-11 creates a product and its first variant in one Spring transaction. Its category
name is normalized and matched case-insensitively against this source of truth. A
matching active category is reused by ID; a missing category is created in the same
transaction; an inactive category is rejected rather than silently reactivated. The
server fixes new product status to `ACTIVE` and only accepts `AVAILABLE` or
`UNAVAILABLE` as initial variant availability.

Current Product deletion is real CRUD deletion. An unreferenced product and its owned
variants are physically deleted. Products referenced by `order_items` or `quotation_items`
are protected by restrictive foreign keys and the API returns `409 PRODUCT_IN_USE`; Orders
and Quotations remain intact. The legacy cleanup removes unreferenced `DISCONTINUED`
products, converts referenced legacy products to `INACTIVE`, and converts retained legacy
`DISCONTINUED` variants to `UNAVAILABLE` before narrowing both status constraints.

TGMS-12 exposes the public catalog through an application query over these existing
tables; it adds no schema or seed data. Public visibility requires an `ACTIVE` category,
an `ACTIVE` product, and an `AVAILABLE` variant. Other lifecycle rows remain stored for
staff workflows and historical references but are not returned by the public catalog.

TGMS-13 adds parameterized application queries for product-name search and category,
size, color, and availability filters. It adds no schema change: the existing product
name and relationship/status indexes support the foundation-stage catalog. Filters
always narrow the TGMS-12 public row set and never bypass its lifecycle visibility rule.

TGMS-14 reads a single product by its stable `garment_products.id` through the same
public visibility predicate. It adds no table or index. The public detail response
includes only active category/product rows with at least one available variant, while
the existing staff retrieval contract remains available for authorized lifecycle work.

## TGMS-2 Verification

| Acceptance check | Result |
|---|---|
| Repeatable workflow | Implemented with versioned Liquibase changesets and root apply/status/rollback/verify commands |
| Business-module schema | None added; the baseline changeset creates no application tables |
| Conventions | Keys, relationships, timestamps, naming, history, and safety rules documented above |

## TGMS-3 Verification

| Acceptance check | Result |
|---|---|
| Minimum shared schema | Only `users` added; no business-module table added |
| Repeatable migration | Automated H2/MySQL-mode tests apply the migration chain and verify resulting schema constraints |
| Account integrity | Unique email, controlled role check, non-null bcrypt hash/name/active state, and standard timestamps enforced |

## TGMS-10 Verification

| Acceptance check | Result |
|---|---|
| Schema applies successfully | Catalog changeset is covered by automated migration-chain and schema verification |
| Required fields represented | Category, product name, size, color, exact price, controlled status/availability, and timestamps are represented |
| Stable IDs | Every catalog table has an auto-generated `BIGINT` primary key and later modules reference parent records through foreign keys |

## TGMS-20 Verification

| Acceptance check | Result |
|---|---|
| Stable supplier ID | `supplier_profiles.id` is an auto-generated `BIGINT` primary key |
| Authenticated relationship | Required unique `user_id` references `users.id` with restrictive update/delete behavior |
| Contact/profile integrity | Business name, contact phone, address, and both timestamps are required; blank-value checks are enforced |
| Credential ownership | Email, password hash, role, active state, and account-holder name remain exclusively in `users` |
| Repeatable migration | Automated migration tests apply the relevant chain and verify the resulting schema |

## TGMS-21 Verification

| Acceptance check | Result |
|---|---|
| Own profile save | Supplier-only service creates or updates by authenticated `users.id` |
| Ownership | Request contract contains no owner ID; unique `user_id` and server-side lookup prevent cross-account edits |
| Invalid values | Required and maximum-length rules are enforced by API validation and service normalization |
| Schema impact | No migration added; automated schema verification continues against the migration chain |

## TGMS-22 Verification

| Acceptance check | Result |
|---|---|
| Stable supply and supplier IDs | Generated `material_supplies.id` and required restrictive `supplier_id` foreign key |
| Numeric integrity | Database checks reject negative quantity, unit price, and delivery lead time |
| Inventory readiness | Stable supply ID and documented `material_supply_id` reference contract |
| Repeatable migration | Automated migration tests apply the relevant chain and verify the resulting schema |

## TGMS-23 Application Contract

TGMS-23 adds no migration. `POST /api/material-supplies` inserts into the TGMS-22 table
only after resolving `supplier_profiles.id` from the authenticated Supplier account.
The request contains no `supplier_id` or status, and every new row is database-defaulted
to `ACTIVE`. The service returns the saved stable ID and complete record, rejects
duplicate supplier-scoped material codes, and revalidates required text, decimal
precision, positive quantity/price, and non-negative delivery lead time.

| Acceptance check | Result |
|---|---|
| Valid record save | Transactional Supplier service inserts and reads back the complete active supply |
| Ownership | JWT user ID resolves the unique Supplier profile; client ownership fields are ignored |
| Invalid values | Jakarta request validation, service validation, and TGMS-22 database checks provide layered rejection |
| Schema impact | No changeset added by this application-contract update |

## TGMS-24 Read Contract

TGMS-24 adds no migration. `GET /api/material-supplies` queries the TGMS-22 table and
joins `supplier_profiles` only to resolve the current supplier business name. Supplier
accounts are always constrained by the server-resolved `supplier_profiles.id`.
Inventory Manager and Administrator accounts may read company-wide supply rows.
Customer and other internal roles are denied by reusable server-side authorization.

The optional case-insensitive `search` filter covers material code, name, description,
unit of measure, and delivery notes. The optional `status` filter accepts only `ACTIVE`
or `INACTIVE`; combined filters use AND semantics.

| Acceptance check | Result |
|---|---|
| Supplier visibility | Repository query receives only the authenticated Supplier's stable profile ID |
| Company read access | Inventory Managers and Administrators receive required supply and supplier display data |
| Search | Parameterized text search and controlled status filter are applied in the repository query layer |
| Schema impact | No changeset was added by this update; later legacy cleanup is explicitly forward-only |

## TGMS-25 Update Contract

TGMS-25 adds no migration. Supplier-owned updates use the existing quantity, unit
price, delivery lead time, delivery notes, and `updated_at` columns. The update SQL
matches both stable supply ID and the Supplier profile ID resolved from the active
authenticated user, then explicitly sets `updated_at = CURRENT_TIMESTAMP(6)`.

Material code/name/description, unit of measure, supplier ownership, lifecycle status,
stable ID, and creation timestamp are not mutable through this contract. Quantity and
unit price must remain positive for a current offer, while delivery lead time remains a
non-negative integer. Inventory Manager and Administrator list reads use the same row
and therefore observe committed updates without duplicated master data.

| Acceptance check | Result |
|---|---|
| Own update | Supplier identity is server-resolved and included in the SQL update predicate |
| Numeric validation | Create and update share positive decimal and non-negative delivery validation |
| Company visibility | Existing authorized list query reads the updated source row immediately |
| Audit metadata | Successful update explicitly advances `updated_at` |
| Schema impact | No changeset was added by this update; later legacy cleanup is explicitly forward-only |

## TGMS-26 Delete and Inventory Handoff Contract

Supplier-owned `DELETE /api/material-supplies/{supplyId}` is a real CRUD delete. An
unreferenced Material Supply is removed physically. If `inventory_materials.source_material_supply_id`
references the supply, deletion is rejected with `409 SUPPLY_IN_USE`; Inventory and Production
history are never cascaded or destroyed. Supplier ownership remains server-enforced.

The current Material Supply runtime statuses are `ACTIVE` and `INACTIVE`. A follow-up
Liquibase cleanup removes legacy unreferenced `DISCONTINUED` supplies and converts legacy
Inventory-referenced `DISCONTINUED` supplies to `INACTIVE` before narrowing the status check.

Inventory references `material_supplies.id` with restrictive update/delete behavior. Current
offer quantity, price, delivery information, availability state, and Supplier ownership remain
Supplier Management master data; Inventory keeps only its own transaction/stock facts.

| Acceptance check | Result |
|---|---|
| Unused supply delete | Physical delete |
| Inventory-referenced supply delete | `409 SUPPLY_IN_USE`; history retained |
| Current lifecycle statuses | `ACTIVE`, `INACTIVE` |
| Legacy cleanup | Unreferenced `DISCONTINUED` deleted; referenced rows become `INACTIVE` |

## TGMS-40 Customer Order Header Contract

Order Management owns `orders` as the customer-order header source of truth. Each row
has a generated stable `BIGINT id`, a required unique `order_number`, a restrictive
`customer_id` foreign key to the shared `users.id`, controlled lifecycle `status`, and
the standard `created_at` / `updated_at` timestamps.

Supported stored statuses are `PENDING`, `CONFIRMED`, `IN_PRODUCTION`,
`READY_FOR_DELIVERY`, `COMPLETED`, and `CANCELLED`. TGMS-40 defines the controlled
values only; status transition rules belong to later approved Order Management tickets.

The database guarantees that `customer_id` references an existing account and prevents
a referenced account from being deleted or re-keyed. It deliberately does not copy
customer email, name, role, or active state into `orders`. As with the Supplier profile
contract, the later order-creation service must resolve the authenticated/customer
account and verify that it is active with role `CUSTOMER` before inserting the header;
a SQL `CHECK` constraint cannot reference the `users` table without creating a second
account source of truth.

TGMS-41 order-item rows reference catalog IDs supplied by the TGMS-16 Product handoff
and keep order-time selection/price snapshots without changing the header contract.
Future Production and Delivery records must reference `orders.id` from their own module
tables rather than adding competing production/delivery master data to the order header.
TGMS-40 itself intentionally added no order items, totals, invoices, payment records,
production jobs, delivery rows, APIs, or UI.

| Acceptance check | Result |
|---|---|
| Stable order identity | Generated `orders.id` plus unique nonblank `order_number` |
| Customer/account link | Required restrictive `customer_id -> users.id` foreign key |
| Controlled lifecycle | Database check accepts only the six documented order statuses |
| Audit timestamps | Required microsecond `created_at` and `updated_at` defaults |
| Production/Delivery readiness | Future modules reference stable `orders.id`; no premature cross-module columns added |
| Repeatable migration | Current migration tests apply the full chain and verify schema plus legacy-data behavior |

## TGMS-41 Order Item Contract

Order Management owns `order_items` as the transactional line structure under
`orders`. Each row has a stable generated `id`, required `order_id`, stable Product
Management `product_id` and `variant_id` references, positive integer `quantity`, and
order-time snapshots of `selected_size`, `selected_color`, and `unit_price_snapshot`.

The Product catalog remains the source of truth. TGMS-41 adds only a backward-compatible
unique constraint on `garment_product_variants(product_id, id)` so the order-item composite
foreign key can prove that the selected variant actually belongs to the referenced
product. Product names, category data, current availability, and current catalog price
are not copied as competing master data.

Size, color, and unit price are deliberately stored as transaction snapshots. Order
creation must call `ProductService.requireOrderSelectableVariant(productId, variantId)`
and persist the returned variant size, color, and current price; clients must not be
trusted to choose their own snapshot price. Later edits to the Product variant therefore
do not rewrite historical order details or historical prices.

The schema rejects missing orders, broken/mismatched Product references, zero or negative
quantities, blank size/color snapshots, and non-positive price snapshots. Restrictive
foreign keys preserve order and catalog history while order items reference those rows.
TGMS-41 intentionally adds no order-creation API, totals, discounts, quotations, invoices,
payments, production jobs, delivery records, or UI.

## TGMS-46 Order Status History Contract

`create-order-status-history.sql` adds the application-managed transition audit table used by
Order Management. It references the existing `orders.id` and authenticated `users.id` with
restrictive foreign keys, stores controlled `from_status` / `to_status` values, rejects same-state
history rows, and records `changed_at` using the project timestamp convention.

`orders.status` remains the current lifecycle source of truth; `order_status_history` is an audit
trail, not a competing current-status column. Application updates must use the Order service's
controlled transition policy and transaction so the current row and history entry commit together.
The status-history changeset itself has an explicit schema rollback; later destructive cleanup changesets remain forward-only.

## TGMS-48 Customer Quotation Contract

`create-customer-quotations.sql` adds immutable issued quotation records without
reusing the `orders` lifecycle. `quotations.customer_id` and `quotations.issued_by_user_id` reference
the shared `users.id` source of truth with restrictive foreign keys. `quotation_items` references the
existing Product and variant IDs, including the existing `(product_id, id)` Product-variant contract,
and stores issued-time product-name, size, color, quantity, and unit-price snapshots.

The schema rejects blank quotation numbers/product names/selections, non-positive quantities/prices,
and broken or mismatched Product references. Totals are derived from the immutable line snapshots.
This quotation changeset itself can be reversed by dropping `quotation_items` before `quotations`; that does not make the later full migration chain reversible.

## TGMS-49 Order Billing Contract

`create-order-billing.sql` adds `order_invoices` and
`order_payment_records` without introducing a payment gateway. `order_invoices.order_id` is unique,
so one issued invoice is retained per customer order, and the invoice stores the total calculated
from TGMS-41 order-item price snapshots. The invoice/customer order relationship is restrictive so
historical billing cannot outlive or silently detach from its order.

`order_payment_records` stores the current manual payment state (`UNPAID`, `PARTIALLY_PAID`, or
`PAID`), non-negative amount, optional controlled manual method (`CASH`, `BANK_TRANSFER`, `OTHER`),
reference/note, authenticated recorder ID, and timestamps. A composite foreign key on
`(order_id, invoice_id)` guarantees that the payment record cannot be paired with an invoice from a
different order.

Cancellation continues to use `orders.status = CANCELLED`; TGMS-49 adds no delete cascade and no
physical-order removal. Billing/history foreign keys stay restrictive. This billing changeset itself can be reversed by dropping `order_payment_records` before `order_invoices`; later destructive cleanup changesets remain forward-only.

## Production Task Contract (TGMS-50)

Production Management owns `production_tasks`. Its generated `id` is the stable production-task
identifier; `task_number` is a separate unique human-facing identifier. Every task references the
existing Order Management source of truth through `order_id -> orders.id` with restrictive
update/delete behavior so later Production records cannot orphan or rewrite Order history.

The controlled Production lifecycle follows the approved project design exactly:
`PENDING -> IN_PROGRESS -> COMPLETED`. `started_at` is required once a task is in progress and
`completed_at` is required only when it is completed; a completion time cannot precede the start
time. The table also retains the normal `created_at` and `updated_at` timestamps.

TGMS-50 intentionally does not add a due date, assignee, material allocation, progress percentage,
production line, or scheduling fields because those belong to later Production Management tickets.
The database guarantees that the referenced Order exists, but SQL does not duplicate or inspect
`orders.status`. The future task-creation service must use the TGMS-49 Order handoff contract and
require `readyForProduction = true` before inserting a task rather than reading/updating Order data
directly.

The Production-task changeset itself has schema rollback; later destructive cleanup changesets make the complete chain forward-only across those cleanup points.


## TGMS-52 production task details

`create-production-task-details.sql` adds a one-to-one `production_task_details`
record keyed by the stable Production task ID. Required `work_details` and simple text
`work_assignment` plus optional `work_notes` persist Production Manager organization data without
inventing worker/team master records. The task foreign key is restrictive so saved detail/history
cannot be orphaned.

## TGMS-53 production task material requirements

`create-production-task-material-requirements.sql` adds the Production-owned relation
between stable `production_tasks.id` and stable `inventory_materials.id`. The composite primary key
prevents duplicate material assignment within one task, `required_quantity` must be positive, and
both foreign keys use restrictive update/delete behavior so Production/Inventory history cannot be
orphaned. No Inventory master fields or stock quantity are copied into this relation.

This requirements changeset itself can be reversed by dropping `production_task_material_requirements`; later destructive cleanup changesets remain forward-only.

## TGMS-55 production material usage history

TGMS-55 adds `production_task_material_usage` as the audit record for material quantities actually
deducted for a Production task under the current workflow. Each usage row must reference an existing
`(production_task_id, inventory_material_id)` pair from `production_task_material_requirements`; this
prevents Production from recording usage for an unassigned Inventory material. The unique
`(production_task_id, inventory_material_id)` constraint makes each approved task/material requirement
consumable once, while `recorded_by_user_id` preserves who recorded the action.

The stored `quantity_used` is the approved TGMS-53 `required_quantity` used by the current workflow.
Production never updates `inventory_materials.current_quantity` directly. The application calls the
Inventory-owned atomic `consumeStock(...)` service inside the same Spring transaction that records
usage. A Production task row lock serializes duplicate submissions; any Inventory failure rolls back
all deductions and usage rows together. Material requirements are locked after the task leaves
`PENDING` so the usage record remains explainable. No reservation, alternate actual-quantity model,
Production completion, or automated optimization is introduced by this changeset.

## TGMS-57 simple production quality control

`add-production-quality-control.sql` adds a deliberately small quality-control state to
`production_tasks`: `PENDING`, `PASSED`, or `FAILED`, plus the authenticated checker ID and check
timestamp. PENDING has no checker/time; PASSED and FAILED require both. The checker references the
shared `users.id` source of truth with restrictive history-preserving foreign keys. No separate quality
system, inspection-item table, defect catalogue, or Delivery data is created. This QC changeset has schema rollback, while the complete migration chain still contains intentionally irreversible legacy cleanup.

## TGMS-60 delivery record contract

`create-deliveries.sql` adds `deliveries` as the Delivery Management record source
of truth. Each row has a generated stable `id`, unique nonblank human-facing `delivery_number`, and
one restrictive `order_id -> orders.id` relationship. The current approved simple scope models one
Delivery record per Order, so `order_id` is unique; a later split-shipment requirement would need an
explicit forward migration rather than silently weakening this contract.

The scheduled date and time are represented by the required UTC `scheduled_at TIMESTAMP(6)`, which stores the
scheduled delivery date and time in one business-event timestamp. Delivery-owned destination data is
kept in required `delivery_address` plus optional `delivery_notes`; customer identity and Order data
remain owned by `users`/Order Management and are not duplicated as master data.

The controlled Delivery lifecycle is `SCHEDULED`, `OUT_FOR_DELIVERY`, `DELIVERED`, or `CANCELLED`,
with `SCHEDULED` as the database default. TGMS-60 intentionally does not implement the later status
transition rules or API/UI operations. The foreign key guarantees that the Order exists, but Order
readiness is an application rule: the later Delivery creation service must call the TGMS-57/TGMS-49
Order handoff and require `readyForDelivery = true` instead of copying or directly inspecting
`orders.status` as Delivery-owned state.

This Delivery changeset itself can be reversed with `DROP TABLE deliveries`; that does not restore data removed or converted by earlier legacy cleanup changesets.

## TGMS-66 safe Delivery cancellation and replacement lock

`enable-safe-delivery-cancellation.xml` evolves the TGMS-60 Delivery relationship so
incorrect scheduled records can be cancelled without deleting history and the Order can then receive
a corrected replacement Delivery.

The migration removes the original `UNIQUE(deliveries.order_id)` constraint and adds nullable
`active_order_lock_id`. Existing non-cancelled rows are backfilled with their stable `order_id`, while
cancelled history keeps a null lock. `active_order_lock_id` has a restrictive foreign key to
`orders.id`, a unique constraint, and a consistency check requiring every non-cancelled Delivery to
hold its own Order ID as the active lock while every `CANCELLED` row has a null lock.

Application cancellation updates `status = CANCELLED` and clears the active lock in the same SQL
statement. A replacement Delivery keeps the same historical `order_id` relationship but receives a
new generated Delivery ID/number and claims the unique active lock. This allows multiple cancelled
history rows over time while guaranteeing at most one non-cancelled Delivery per Order. The full
migration workflow now contains nineteen changesets.

## TGMS-71 password reset tokens

`create-password-reset-tokens.sql` adds the security-support table used by account
recovery. Each row references the stable `users.id`, stores only a unique 64-character SHA-256 token
hash, and records an expiry plus nullable `used_at` marker. Raw reset tokens and passwords are never
stored in this table. The user/open-token index supports invalidating every remaining recovery token
after one reset succeeds. The migration is reversible by dropping the token table; the full
migration workflow now contains twenty changesets.

## Customer email verification

`add-email-verification.sql` adds the nullable `users.email_verified_at` security
state and the `email_verification_codes` table. Existing accounts are marked verified
during migration so deployment does not lock out established users. Application-created
Customer accounts explicitly start unverified, while internally provisioned staff are
verified immediately.

Only a per-code random salt and SHA-256 hash are stored; the six-digit code exists in
memory only long enough to schedule its email. Codes expire, are single-use, have a
failed-attempt limit, and are invalidated when a replacement code is issued or the
account is verified. The foreign key cascades code cleanup only when its owning user is
deleted. The changeset is included immediately after `create-users.sql`, before modules
that depend on the shared identity table.

`enforce-customer-email-verification.sql` immediately follows that changeset and
removes the temporary `CURRENT_TIMESTAMP` column default. This makes direct and
legacy Customer inserts fail closed as unverified. It also repairs Customer accounts
created by an older running application during rollout when their verification
timestamp was generated automatically and no verification code was ever issued.

## TGMS-73 operational notifications

`create-notifications.sql` adds `notifications` as a user-facing supporting/audit
record, not a replacement for Inventory, Order, Production, or Delivery state. Each notification
references the stable recipient `users.id`, records a controlled kind, concise title/message,
`source_module`, positive `source_record_id`, creation timestamp, and nullable `read_at` timestamp.
The source pair intentionally remains polymorphic rather than copying source-module business data
into the notification table.

Recipient/read and source indexes support the authenticated inbox and operational traceability.
Foreign-key deletion is restrictive so user/notification history is not silently orphaned. The
migration is reversible by dropping `notifications`; the full migration workflow now contains twenty-one
changesets.

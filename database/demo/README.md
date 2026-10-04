# TGMS Demo Data

`reset-and-seed.sql` is **presentation-only data**, not a Liquibase migration and not production seed data. It writes into the real TGMS source-of-truth tables so the demo exercises the same contracts as normal users.

Run it only through `npm run demo:reset`, which requires an explicit disposable-database opt-in and confirmation. The reset removes only records identified by reserved `demo.*@tgms.example`, `DEMO-*`, and `[DEMO_SEED]` markers, then recreates the deterministic presentation state.

All six authenticated roles use the runtime password supplied as `DEMO_USER_PASSWORD`. The reset wrapper validates the application password limits and generates a bcrypt hash without storing the plaintext or hash in the repository. The `.example` email domain is reserved for documentation and will not route real email. Never reuse demo credentials in production.

## Sri Lankan demo identities

| Role | Name | Email |
| --- | --- | --- |
| Administrator | Nadeesha Perera | `demo.admin@tgms.example` |
| Supplier | Chathura Bandara | `demo.supplier@tgms.example` |
| Inventory manager | Tharushi Senanayake | `demo.inventory@tgms.example` |
| Production manager | Kasun Jayawardena | `demo.production@tgms.example` |
| Sales officer | Dinithi Fernando | `demo.sales@tgms.example` |
| Customer | Kavindu Dissanayake | `demo.customer@tgms.example` |

The seed also creates six supplies, six linked inventory materials, four photographed products with variants, five orders across lifecycle states, quotations, invoices and payments, production work and material usage, deliveries, and notifications. One `READY_FOR_DELIVERY` order is intentionally left without an active Delivery record so the Sales Officer's Prepare delivery page has an eligible order to schedule.

## Add business data without creating users

Use `npm run demo:business:seed` to replace all non-user demo/business data in the local `tgms` database while preserving every existing user row and its login credentials. The command selects existing active users in the required roles (`SUPPLIER`, `INVENTORY_MANAGER`, `PRODUCTION_MANAGER`, `SALES_OFFICER`, and `CUSTOMER`) and links new records through those accounts. It requires the explicit `DEMO_DATA_ALLOWED=true` opt-in, validates that the target is the local writable `tgms` database, and executes the reset in one transaction. Do not run it on a database containing business data you need to keep.

```bash
export DEMO_DATA_ALLOWED=true
npm run demo:business:seed
unset DEMO_DATA_ALLOWED
```

The current generator is `scripts/demo/seed-business-data.mjs`, invoked by the existing wrapper. It creates 24 supplies, 24 linked inventory materials, 10 categories, 30 products, 90 variants, 16 quotations, 35 orders, 30 production tasks, 11 deliveries, invoices, payment records, and notifications. Eight production-complete `READY_FOR_DELIVERY` orders have no delivery record and are ready for the Prepare delivery workflow. The older `seed-business-data.sql` is retained as a legacy reference and is not used by this command.

## Run the seed without hard-coding a password

From the repository root:

```bash
export DEMO_DATA_ALLOWED=true
read -s "DEMO_USER_PASSWORD?Demo login password: "
export DEMO_USER_PASSWORD
npm run demo:reset
npm run demo:verify
unset DEMO_USER_PASSWORD DEMO_DATA_ALLOWED
```

Use the password entered at the hidden prompt for all six accounts. Product image paths are stored in `garment_products.image_url`; the matching local catalog assets live under `frontend/public/products/demo/`. Product managers can set or remove an HTTPS URL or managed `/products/` path from the Add/Edit Product forms.

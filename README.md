# TGMS — Garment Product + Order + Fabric Inventory + Supplier Management Only

This archive was extracted from the supplied **Web-Based Textile & Garment Management System (TGMS)** project and contains implementation artifacts for these four major functions only:

1. **Garment Product Management** — assigned to **Gunathilaka N.T.D.S (IT25102427)**
2. **Order Management** — assigned to **Nimsara V.G.P (IT25101310)**
3. **Fabric Inventory Management** — assigned to **Basnagoda B.L.K (IT25102148)**
4. **Supplier Management** — assigned to **Ketipearachchi R.P (IT25100244)**

## Included scope

### Garment Product Management
- Product catalog and product details UI
- Add/edit garment product UI
- Product form/types/image handling
- Product frontend API client
- Product backend controller/service/repository/domain models
- Product image storage code and sample/demo product images
- Product tests
- Product catalog and image database migrations

### Order Management
- Create-order UI
- Customer place-order UI
- Customer order tracking UI
- Order read/details pages
- Order receipt PDF helper
- Order frontend API client
- Order backend controller/service/repository/domain models
- Order validation, lifecycle, billing, invoice/payment records
- Order tests
- Customer-order, order-item, status-history, and billing migrations

### Fabric Inventory Management
- Inventory material listing/search/filter UI
- Add inventory material UI
- Edit/update inventory material UI
- Inventory material form logic
- Inventory frontend API client
- Inventory backend controller/service/repository/domain models
- Stock status, availability, validation and insufficient-stock logic
- Inventory schema/API integration tests
- Inventory materials database migration

### Supplier Management
- Supplier profile UI and profile API client
- Material supply listing/search/filter UI
- Add/edit material supply UI
- Material supply form logic and API client
- Supplier profile backend controller/service/repository/domain models
- Material supply backend controller/service/repository/domain models
- Supplier and material-supply validation/status/error handling
- Supplier profile and material supply API/schema tests
- Supplier profile and material supply database migrations

## Important
This is a **module-only extraction**, not a standalone copy of the entire TGMS application. Shared application infrastructure such as authentication, authorization, global routing/layout, notification services, and other common configuration remains in the full project and may be required to run these modules independently.

The **Production Management** and **Delivery Management** major modules are intentionally excluded.

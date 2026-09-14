# TGMS — Garment Product + Order + Fabric Inventory Management Only

This archive was extracted from the supplied **Web-Based Textile & Garment Management System (TGMS)** project and contains implementation artifacts for these three major functions only:

1. **Garment Product Management** — assigned to **Gunathilaka N.T.D.S (IT25102427)**
2. **Order Management** — assigned to **Nimsara V.G.P (IT25101310)**
3. **Fabric Inventory Management** — assigned to **Basnagoda B.L.K (IT25102148)**

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

## Important
This is a **module-only extraction**, not a standalone copy of the entire TGMS application. Shared application infrastructure such as authentication, authorization, global routing/layout, notification services, and other common configuration remains in the full project and may be required to run these modules independently.

The Supplier Management, Production Management, and Delivery Management major modules are intentionally excluded.

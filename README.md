# TGMS — Garment Product + Order + Fabric Inventory + Supplier + Production Management Only

This archive was extracted from the supplied **Web-Based Textile & Garment Management System (TGMS)** project and contains implementation artifacts for these five major functions only:

1. **Garment Product Management** — Gunathilaka N.T.D.S (IT25102427)
2. **Order Management** — Nimsara V.G.P (IT25101310)
3. **Fabric Inventory Management** — Basnagoda B.L.K (IT25102148)
4. **Supplier Management** — Ketipearachchi R.P (IT25100244)
5. **Production Management** — Dilwan A.W.W.A.S (IT24100351)

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
- Add/edit inventory material UI
- Inventory material form logic and frontend API client
- Inventory backend controller/service/repository/domain models
- Stock status, availability, validation and insufficient-stock logic
- Inventory schema/API integration tests
- Inventory materials database migration

### Supplier Management
- Supplier profile UI and API client
- Material supply listing/search/filter UI
- Add/edit material supply UI
- Material supply form logic and API client
- Supplier profile and material supply backend controller/service/repository/domain models
- Validation/status/error handling
- Supplier/material-supply API and schema tests
- Supplier profile and material supply database migrations

### Production Management
- Production task creation UI
- Production task records/list UI
- Production task detail/progress UI
- Production frontend API client
- Production backend controller/service/repository/domain models
- Production task status lifecycle and validation
- Production material requirements, availability and usage tracking
- Production work/task details and quality-control logic
- Production security configuration and its test
- Production API/schema/status lifecycle tests
- Production task, task-detail, material-requirement, material-usage and quality-control database migrations

## Important
This is a **module-only extraction**, not a standalone copy of the entire TGMS application. Shared infrastructure such as authentication, authorization, global routing/layout, notification services, application bootstrap/configuration, and other common files remain in the full project and may be required to run these modules independently.

The **Delivery Management** major module is intentionally excluded.

# TGMS — Garment Product Management + Order Management Only

This archive was extracted from the supplied **Web-Based Textile & Garment Management System (TGMS)** project and contains only the implementation artifacts for these two major functions:

1. **Garment Product Management** — assigned to **Gunathilaka N.T.D.S (IT25102427)**
2. **Order Management** — assigned to **Nimsara V.G.P (IT25101310)**

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

## Important
This is a **module-only extraction**, not a standalone copy of the entire TGMS application. Shared application infrastructure (authentication, global routing/layout, common configuration, customer module, etc.) remains in the full project and may be required to run these modules independently.

Other major TGMS modules such as Supplier Management, Fabric Inventory Management, Production Management, and Delivery Management are intentionally excluded.

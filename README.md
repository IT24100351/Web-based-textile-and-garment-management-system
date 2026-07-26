# Delivery Management Only

This package contains only the Delivery Management implementation extracted from the Web Based Textile & Garment Management System project.

## Assigned module
- Major function: Delivery Management
- Assigned member: Gunarathna P.G.S.C
- Registration No: IT25103068

## Included scope
- Delivery order selection
- Delivery scheduling
- Delivery records/listing
- Delivery detail view
- Delivery status lifecycle and status updates
- Customer delivery tracking
- Delivery validation/conflict handling
- Safe delivery cancellation migration
- Delivery database schema migration
- Delivery frontend tests
- Delivery backend/integration tests

## Important integration note
This is a module-only extraction, not a standalone runnable copy of the whole TGMS application. The Delivery module references shared project infrastructure and Order Management contracts from the main project, including authentication/authorization, order handoff/details, notifications, common frontend components/navigation, and the shared API client.

No Supplier Management, Fabric Inventory Management, Garment Product Management, Production Management, or full Order Management module source is included in this ZIP.

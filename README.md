# Garment Product Management — Extracted Module Only

This ZIP contains only the Garment Product Management implementation extracted from the Web Based Textile & Garment Management System project.

## Assigned member
- Gunathilaka N.T.D.S
- Registration No: IT25102427
- Major Function: Garment Product Management

## Functional scope included
- Garment product catalog browsing and product detail display
- Product creation
- Product update/editing
- Product category, size, color and price handling
- Product variants and availability/status handling
- Product image handling
- Product persistence/repository/service/controller code
- Product-specific database migrations
- Product-specific backend tests
- Demo product images used by the product catalog

## Main folders
- `frontend/src/products/` — product pages/components/forms/types
- `frontend/src/api/products.ts` — product API client
- `frontend/public/products/` — product catalog assets
- `backend/src/main/java/lk/ac/sliit/tgms/product/` — product backend module
- `backend/src/test/java/lk/ac/sliit/tgms/product/` — product backend tests
- `database/migrations/changes/` — product catalog/image migrations

## Important
This is a **module-only extraction**, not a standalone copy of the complete TGMS application. Shared project infrastructure (authentication, authorization, common UI components, routing, API client setup, Spring Boot configuration, Maven/Vite configuration, and the master Liquibase changelog) is intentionally not duplicated here because the request was to include only the Garment Product Management part.

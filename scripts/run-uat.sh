#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[uat] Verifying TGMS-83 evidence catalog..."
node scripts/verify-uat-catalog.mjs

BACKEND_TESTS="ProductApiIntegrationTests,SupplierProfileApiIntegrationTests,MaterialSupplyApiIntegrationTests,InventoryMaterialApiIntegrationTests,OrderApiIntegrationTests,ProductionTaskApiIntegrationTests,DeliverySelectionApiIntegrationTests,DeliveryScheduleApiIntegrationTests,DeliveryRecordsApiIntegrationTests,CustomerDeliveryTrackingApiIntegrationTests,CrossModuleEndToEndIntegrationTests,SecurityBoundaryIntegrationTests"

FRONTEND_TESTS=(
  src/App.test.tsx
  src/orders/CreateOrderPage.test.tsx
  src/orders/CustomerOrderTrackingPage.test.tsx
  src/production/CreateProductionTaskPage.test.tsx
  src/production/ProductionTaskDetailPage.test.tsx
  src/delivery/DeliveryOrderSelectionPage.test.tsx
  src/delivery/DeliverySchedulePage.test.tsx
  src/delivery/DeliveryRecordsPage.test.tsx
  src/delivery/DeliveryDetailPage.test.tsx
)

echo "[uat] Running TGMS-83 backend acceptance evidence..."
(
  cd backend
  ./mvnw -Dtest="$BACKEND_TESTS" test
)

echo "[uat] Running TGMS-83 frontend acceptance evidence..."
npm run test --workspace frontend -- "${FRONTEND_TESTS[@]}"

echo "[uat] TGMS-83 UAT release gate passed."
echo "[uat] Backend evidence: backend/target/surefire-reports/"
echo "[uat] Frontend evidence: Vitest console/JUnit output configured by the execution environment."

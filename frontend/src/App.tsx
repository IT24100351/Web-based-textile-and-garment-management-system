import { Navigate, Route, Routes } from "react-router-dom";

import type { UserRole } from "./api/auth";
import { AdminUsersPage } from "./admin/AdminUsersPage";
import {
  ForgotPasswordPage,
  LoginPage,
  RegistrationPage,
  ResetPasswordPage,
  EmailVerificationPage,
} from "./auth/AuthPages";
import { AuthProvider } from "./auth/AuthProvider";
import {
  ForbiddenPage,
  ProtectedRoute,
  RoleAccessPage,
} from "./auth/AuthorizationPages";
import {
  roleAccessPagePaths,
  type InternalRole,
} from "./api/roleAccess";
import { NotFoundPage } from "./components/AppStates";
import { DashboardPage } from "./dashboard/DashboardPage";
import { AppLayout } from "./layout/AppLayout";
import {
  adminUserManagementPagePath,
  customerPlaceOrderPagePath,
  customerOrderDetailRoutePath,
  customerOrderHistoryPagePath,
  customerOrderTrackingPagePath,
  customerOrderTrackingRoutePath,
  dashboardPagePaths,
  deliveryDetailRoutePath,
  deliveryOrderSelectionPagePath,
  deliveryRecordsPagePath,
  deliveryScheduleRoutePath,
  emailVerificationPagePath,
  editMaterialSupplyRoutePath,
  editInventoryMaterialRoutePath,
  inventoryMaterialListPagePath,
  editProductRoutePath,
  materialSupplyListPagePath,
  newInventoryMaterialPagePath,
  newOrderPagePath,
  newQuotationPagePath,
  quotationDetailRoutePath,
  quotationListPagePath,
  newProductPagePath,
  newMaterialSupplyPagePath,
  productCatalogPagePath,
  productManagementPagePath,
  productDetailRoutePath,
  productionTaskCreatePagePath,
  productionTaskRecordsPagePath,
  productionTaskDetailRoutePath,
  profileSettingsPagePath,
  notificationsPagePath,
  sharedSearchPagePath,
  staffOrderDetailRoutePath,
  staffOrderListPagePath,
  supplierProfilePagePath,
} from "./navigation/navigation";
import { PublicHomePage } from "./pages/PublicHomePage";
import { ProfileSettingsPage } from "./profile/ProfileSettingsPage";
import { NotificationsPage } from "./notifications/NotificationsPage";
import { SharedSearchPage } from "./search/SharedSearchPage";
import { AddProductPage } from "./products/AddProductPage";
import { ProductCatalogPage } from "./products/ProductCatalogPage";
import { ProductManagementPage } from "./products/ProductManagementPage";
import { ProductDetailPage } from "./products/ProductDetailPage";
import { EditProductPage } from "./products/EditProductPage";
import { SupplierProfilePage } from "./suppliers/SupplierProfilePage";
import { AddMaterialSupplyPage } from "./suppliers/AddMaterialSupplyPage";
import { MaterialSupplyListPage } from "./suppliers/MaterialSupplyListPage";
import { EditMaterialSupplyPage } from "./suppliers/EditMaterialSupplyPage";
import { AddInventoryMaterialPage } from "./inventory/AddInventoryMaterialPage";
import { InventoryMaterialListPage } from "./inventory/InventoryMaterialListPage";
import { EditInventoryMaterialPage } from "./inventory/EditInventoryMaterialPage";
import { CreateProductionTaskPage } from "./production/CreateProductionTaskPage";
import { ProductionTaskDetailPage } from "./production/ProductionTaskDetailPage";
import { ProductionTaskRecordsPage } from "./production/ProductionTaskRecordsPage";
import { DeliveryDetailPage } from "./delivery/DeliveryDetailPage";
import { DeliveryOrderSelectionPage } from "./delivery/DeliveryOrderSelectionPage";
import { DeliveryRecordsPage } from "./delivery/DeliveryRecordsPage";
import { DeliverySchedulePage } from "./delivery/DeliverySchedulePage";
import { CreateOrderPage } from "./orders/CreateOrderPage";
import { CreateQuotationPage } from "./quotations/CreateQuotationPage";
import { QuotationDetailPage, QuotationListPage } from "./quotations/QuotationReadPages";
import { CustomerPlaceOrderPage } from "./orders/CustomerPlaceOrderPage";
import { CustomerOrderTrackingPage } from "./orders/CustomerOrderTrackingPage";
import {
  CustomerOrderDetailPage,
  CustomerOrderHistoryPage,
  StaffOrderDetailPage,
  StaffOrderListPage,
} from "./orders/OrderReadPages";
import { ThemeProvider } from "./theme/ThemeProvider";

const protectedRoleRoutes: Array<{ role: InternalRole; path: string }> = [
  { role: "ADMINISTRATOR", path: roleAccessPagePaths.ADMINISTRATOR },
  { role: "SUPPLIER", path: roleAccessPagePaths.SUPPLIER },
  { role: "INVENTORY_MANAGER", path: roleAccessPagePaths.INVENTORY_MANAGER },
  { role: "PRODUCTION_MANAGER", path: roleAccessPagePaths.PRODUCTION_MANAGER },
  { role: "SALES_OFFICER", path: roleAccessPagePaths.SALES_OFFICER },
];

const dashboardRoutes: Array<{ role: UserRole; path: string }> = [
  { role: "ADMINISTRATOR", path: dashboardPagePaths.ADMINISTRATOR },
  { role: "SUPPLIER", path: dashboardPagePaths.SUPPLIER },
  { role: "INVENTORY_MANAGER", path: dashboardPagePaths.INVENTORY_MANAGER },
  { role: "PRODUCTION_MANAGER", path: dashboardPagePaths.PRODUCTION_MANAGER },
  { role: "SALES_OFFICER", path: dashboardPagePaths.SALES_OFFICER },
  { role: "CUSTOMER", path: dashboardPagePaths.CUSTOMER },
];

export function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegistrationPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path={emailVerificationPagePath} element={<EmailVerificationPage />} />
        <Route path="/unauthorized" element={<Navigate replace to="/" />} />
        <Route path="/forbidden" element={<ForbiddenPage />} />
        <Route element={<AppLayout />}>
          <Route index element={<PublicHomePage />} />
          <Route path={productCatalogPagePath} element={<ProductCatalogPage />} />
          <Route path={productDetailRoutePath} element={<ProductDetailPage />} />
          {dashboardRoutes.map(({ role, path }) => (
            <Route
              element={
                <ProtectedRoute allowedRoles={[role]}>
                  <DashboardPage role={role} />
                </ProtectedRoute>
              }
              key={role}
              path={path}
            />
          ))}
          {protectedRoleRoutes.map(({ role, path }) => (
            <Route
              element={
                <ProtectedRoute allowedRoles={[role]}>
                  <RoleAccessPage role={role} />
                </ProtectedRoute>
              }
              key={role}
              path={path}
            />
          ))}
          <Route
            element={
              <ProtectedRoute allowedRoles={[
                "ADMINISTRATOR",
                "SUPPLIER",
                "INVENTORY_MANAGER",
                "PRODUCTION_MANAGER",
                "SALES_OFFICER",
                "CUSTOMER",
              ]}>
                <ProfileSettingsPage />
              </ProtectedRoute>
            }
            path={profileSettingsPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={[
                "ADMINISTRATOR",
                "SUPPLIER",
                "INVENTORY_MANAGER",
                "PRODUCTION_MANAGER",
                "SALES_OFFICER",
                "CUSTOMER",
              ]}>
                <NotificationsPage />
              </ProtectedRoute>
            }
            path={notificationsPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={[
                "ADMINISTRATOR",
                "SUPPLIER",
                "INVENTORY_MANAGER",
                "PRODUCTION_MANAGER",
                "SALES_OFFICER",
                "CUSTOMER",
              ]}>
                <SharedSearchPage />
              </ProtectedRoute>
            }
            path={sharedSearchPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["ADMINISTRATOR"]}>
                <AdminUsersPage />
              </ProtectedRoute>
            }
            path={adminUserManagementPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <DeliveryRecordsPage />
              </ProtectedRoute>
            }
            path={deliveryRecordsPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <DeliveryDetailPage />
              </ProtectedRoute>
            }
            path={deliveryDetailRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <DeliveryOrderSelectionPage />
              </ProtectedRoute>
            }
            path={deliveryOrderSelectionPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <DeliverySchedulePage />
              </ProtectedRoute>
            }
            path={deliveryScheduleRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <QuotationListPage />
              </ProtectedRoute>
            }
            path={quotationListPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <CreateQuotationPage />
              </ProtectedRoute>
            }
            path={newQuotationPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <QuotationDetailPage />
              </ProtectedRoute>
            }
            path={quotationDetailRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <CreateOrderPage />
              </ProtectedRoute>
            }
            path={newOrderPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                <CustomerPlaceOrderPage />
              </ProtectedRoute>
            }
            path={customerPlaceOrderPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER", "ADMINISTRATOR"]}>
                <StaffOrderListPage />
              </ProtectedRoute>
            }
            path={staffOrderListPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER", "ADMINISTRATOR"]}>
                <StaffOrderDetailPage />
              </ProtectedRoute>
            }
            path={staffOrderDetailRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                <CustomerOrderHistoryPage />
              </ProtectedRoute>
            }
            path={customerOrderHistoryPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                <CustomerOrderDetailPage />
              </ProtectedRoute>
            }
            path={customerOrderDetailRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                <CustomerOrderTrackingPage />
              </ProtectedRoute>
            }
            path={customerOrderTrackingPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["CUSTOMER"]}>
                <CustomerOrderTrackingPage />
              </ProtectedRoute>
            }
            path={customerOrderTrackingRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <ProductManagementPage />
              </ProtectedRoute>
            }
            path={productManagementPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <AddProductPage />
              </ProtectedRoute>
            }
            path={newProductPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SALES_OFFICER"]}>
                <EditProductPage />
              </ProtectedRoute>
            }
            path={editProductRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SUPPLIER"]}>
                <SupplierProfilePage />
              </ProtectedRoute>
            }
            path={supplierProfilePagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SUPPLIER"]}>
                <AddMaterialSupplyPage />
              </ProtectedRoute>
            }
            path={newMaterialSupplyPagePath}
          />
          <Route
            element={
              <ProtectedRoute
                allowedRoles={["SUPPLIER", "INVENTORY_MANAGER", "ADMINISTRATOR"]}
              >
                <MaterialSupplyListPage />
              </ProtectedRoute>
            }
            path={materialSupplyListPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["SUPPLIER"]}>
                <EditMaterialSupplyPage />
              </ProtectedRoute>
            }
            path={editMaterialSupplyRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["INVENTORY_MANAGER"]}>
                <InventoryMaterialListPage />
              </ProtectedRoute>
            }
            path={inventoryMaterialListPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["INVENTORY_MANAGER"]}>
                <AddInventoryMaterialPage />
              </ProtectedRoute>
            }
            path={newInventoryMaterialPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["INVENTORY_MANAGER"]}>
                <EditInventoryMaterialPage />
              </ProtectedRoute>
            }
            path={editInventoryMaterialRoutePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["PRODUCTION_MANAGER", "ADMINISTRATOR"]}>
                <ProductionTaskRecordsPage />
              </ProtectedRoute>
            }
            path={productionTaskRecordsPagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["PRODUCTION_MANAGER"]}>
                <CreateProductionTaskPage />
              </ProtectedRoute>
            }
            path={productionTaskCreatePagePath}
          />
          <Route
            element={
              <ProtectedRoute allowedRoles={["PRODUCTION_MANAGER", "ADMINISTRATOR"]}>
                <ProductionTaskDetailPage />
              </ProtectedRoute>
            }
            path={productionTaskDetailRoutePath}
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        </Routes>
      </AuthProvider>
    </ThemeProvider>
  );
}

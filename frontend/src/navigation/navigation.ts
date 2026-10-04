import type { AuthUser, UserRole } from "../api/auth";
import {
  isInternalRole,
  roleAccessPagePaths,
} from "../api/roleAccess";

export interface NavigationItem {
  label: string;
  description: string;
  path: string;
}

export const roleLabels: Record<UserRole, string> = {
  ADMINISTRATOR: "Administrator",
  SUPPLIER: "Supplier",
  INVENTORY_MANAGER: "Inventory manager",
  PRODUCTION_MANAGER: "Production manager",
  SALES_OFFICER: "Sales officer",
  CUSTOMER: "Customer",
};

export const dashboardPagePaths: Record<UserRole, string> = {
  ADMINISTRATOR: "/dashboard/administrator",
  SUPPLIER: "/dashboard/supplier",
  INVENTORY_MANAGER: "/dashboard/inventory-manager",
  PRODUCTION_MANAGER: "/dashboard/production-manager",
  SALES_OFFICER: "/dashboard/sales-officer",
  CUSTOMER: "/dashboard/customer",
};

export const adminUserManagementPagePath = "/admin/users";
export const emailVerificationPagePath = "/verify-email";
export const profileSettingsPagePath = "/profile";
export const notificationsPagePath = "/notifications";
export const sharedSearchPagePath = "/search";
export const newProductPagePath = "/products/new";
export const newOrderPagePath = "/orders/new";
export const customerPlaceOrderPagePath = "/orders/place";
export const staffOrderListPagePath = "/orders";
export const staffOrderDetailRoutePath = "/orders/:orderId";
export const customerOrderHistoryPagePath = "/orders/history";
export const customerOrderDetailRoutePath = "/orders/history/:orderId";
export const customerOrderTrackingPagePath = "/orders/track";
export const customerOrderTrackingRoutePath = "/orders/track/:orderId";
export const quotationListPagePath = "/quotations";
export const newQuotationPagePath = "/quotations/new";
export const quotationDetailRoutePath = "/quotations/:quotationId";
export const productCatalogPagePath = "/products";
export const productManagementPagePath = "/products/manage";
export const productDetailRoutePath = "/products/:productId";
export const editProductRoutePath = "/products/:productId/edit";
export const supplierProfilePagePath = "/supplier/profile";
export const newMaterialSupplyPagePath = "/supplier/supplies/new";
export const materialSupplyListPagePath = "/supplies";
export const editMaterialSupplyRoutePath = "/supplier/supplies/:supplyId/edit";
export const inventoryMaterialListPagePath = "/inventory/materials";
export const newInventoryMaterialPagePath = "/inventory/materials/new";
export const editInventoryMaterialRoutePath = "/inventory/materials/:materialId/edit";
export const productionTaskRecordsPagePath = "/production/tasks";
export const productionTaskCreatePagePath = "/production/tasks/new";
export const productionTaskDetailRoutePath = "/production/tasks/:taskId";
export const deliveryRecordsPagePath = "/deliveries";
export const deliveryDetailRoutePath = "/deliveries/:deliveryId";
export const deliveryOrderSelectionPagePath = "/deliveries/select-order";
export const deliveryScheduleRoutePath = "/deliveries/schedule/:orderId";

const positiveIntegerPattern = /^[1-9]\d*$/;

export function getDeliveryDetailPagePath(deliveryId: number | string) {
  return `/deliveries/${deliveryId}`;
}

export function getDeliverySchedulePagePath(orderId: number | string) {
  return `/deliveries/schedule/${orderId}`;
}

export function parseProductPageId(value: string | undefined) {
  if (!value || !positiveIntegerPattern.test(value)) {
    return null;
  }
  const productId = Number(value);
  return Number.isSafeInteger(productId) ? productId : null;
}

export function getProductDetailPagePath(productId: number) {
  return `/products/${productId}`;
}

export function getEditProductPagePath(productId: number) {
  return `/products/${productId}/edit`;
}

export function parseProductionTaskPageId(value: string | undefined) {
  if (!value || !positiveIntegerPattern.test(value)) {
    return null;
  }
  const taskId = Number(value);
  return Number.isSafeInteger(taskId) ? taskId : null;
}

export function getProductionTaskDetailPagePath(taskId: number) {
  return `/production/tasks/${taskId}`;
}

export function parseMaterialSupplyPageId(value: string | undefined) {
  if (!value || !positiveIntegerPattern.test(value)) {
    return null;
  }
  const supplyId = Number(value);
  return Number.isSafeInteger(supplyId) ? supplyId : null;
}

export function getEditMaterialSupplyPagePath(supplyId: number) {
  return `/supplier/supplies/${supplyId}/edit`;
}

export function parseInventoryMaterialPageId(value: string | undefined) {
  if (!value || !positiveIntegerPattern.test(value)) {
    return null;
  }
  const materialId = Number(value);
  return Number.isSafeInteger(materialId) ? materialId : null;
}

export function getEditInventoryMaterialPagePath(materialId: number) {
  return `/inventory/materials/${materialId}/edit`;
}

export function parseOrderPageId(value: string | undefined) {
  if (!value || !positiveIntegerPattern.test(value)) {
    return null;
  }
  const orderId = Number(value);
  return Number.isSafeInteger(orderId) ? orderId : null;
}

export function getStaffOrderDetailPagePath(orderId: number) {
  return `/orders/${orderId}`;
}

export function getCustomerOrderDetailPagePath(orderId: number) {
  return `/orders/history/${orderId}`;
}

export function getCustomerOrderTrackingPagePath(orderId: number) {
  return `/orders/track/${orderId}`;
}

export function getCustomerPlaceOrderWithItemPath(productId: number, variantId: number) {
  const parameters = new URLSearchParams({
    productId: String(productId),
    variantId: String(variantId),
  });
  return `${customerPlaceOrderPagePath}?${parameters.toString()}`;
}

export function parseQuotationPageId(value: string | undefined) {
  if (!value || !positiveIntegerPattern.test(value)) {
    return null;
  }
  const quotationId = Number(value);
  return Number.isSafeInteger(quotationId) ? quotationId : null;
}

export function getQuotationDetailPagePath(quotationId: number) {
  return `/quotations/${quotationId}`;
}

const publicHome: NavigationItem = {
  label: "Overview",
  description: "System introduction and availability",
  path: "/",
};

const productCatalog: NavigationItem = {
  label: "Garment catalog",
  description: "Browse currently available garments",
  path: productCatalogPagePath,
};

export function getNavigationItems(user: AuthUser | null): NavigationItem[] {
  if (!user) {
    return [
      publicHome,
      productCatalog,
      {
        label: "Sign in",
        description: "Access an existing account",
        path: "/login",
      },
      {
        label: "Customer registration",
        description: "Create a registered customer account",
        path: "/register",
      },
    ];
  }

  const roleLabel = roleLabels[user.role];
  const items: NavigationItem[] = [
    publicHome,
    productCatalog,
    {
      label: `${roleLabel} dashboard`,
      description: `Open the ${roleLabel.toLowerCase()} workspace`,
      path: dashboardPagePaths[user.role],
    },
    {
      label: "Profile settings",
      description: "Maintain your own permitted account information",
      path: profileSettingsPagePath,
    },
    {
      label: "Notifications",
      description: "Review operational alerts and status updates for your account",
      path: notificationsPagePath,
    },
    {
      label: "Search",
      description: "Search core records permitted for your role",
      path: sharedSearchPagePath,
    },
  ];

  if (isInternalRole(user.role)) {
    items.push({
      label: "Authorization check",
      description: "Confirm this role with the protected API",
      path: roleAccessPagePaths[user.role],
    });
  }

  if (user.role === "SALES_OFFICER") {
    items.push({
      label: "Product management",
      description: "Manage active and inactive products and all variant availability states",
      path: productManagementPagePath,
    });
    items.push({
      label: "Delivery records",
      description: "Search deliveries, review progress, and safely cancel incorrect schedules",
      path: deliveryRecordsPagePath,
    });
    items.push({
      label: "Prepare delivery",
      description: "Find and select an Order Management record ready for delivery",
      path: deliveryOrderSelectionPagePath,
    });
    items.push({
      label: "Customer quotations",
      description: "Prepare and review issued quotations",
      path: quotationListPagePath,
    });
    items.push({
      label: "Customer orders",
      description: "Search and inspect customer orders",
      path: staffOrderListPagePath,
    });
    items.push({
      label: "Create customer order",
      description: "Create an order for a registered customer",
      path: newOrderPagePath,
    });
    items.push({
      label: "Add garment product",
      description: "Create a product and its first variant",
      path: newProductPagePath,
    });
  }

  if (user.role === "PRODUCTION_MANAGER") {
    items.push({
      label: "Production records",
      description: "Review active and completed production tasks",
      path: productionTaskRecordsPagePath,
    });
    items.push({
      label: "Create production task",
      description: "Create a task from an Order Management production-ready order",
      path: productionTaskCreatePagePath,
    });
  }

  if (user.role === "CUSTOMER") {
    items.push({
      label: "Track an order",
      description: "Check the latest progress for one of your orders",
      path: customerOrderTrackingPagePath,
    });
    items.push({
      label: "Order history",
      description: "Review orders placed by your account",
      path: customerOrderHistoryPagePath,
    });
    items.push({
      label: "Place an order",
      description: "Select available garments and place your own order",
      path: customerPlaceOrderPagePath,
    });
  }

  if (user.role === "SUPPLIER") {
    items.push({
      label: "Supplier profile",
      description: "Maintain business and contact information",
      path: supplierProfilePagePath,
    });
    items.push({
      label: "My material supplies",
      description: "Review and search your recorded materials",
      path: materialSupplyListPagePath,
    });
    items.push({
      label: "Add material supply",
      description: "Record a material your business can provide",
      path: newMaterialSupplyPagePath,
    });
  }

  if (user.role === "ADMINISTRATOR") {
    items.push({
      label: "User accounts",
      description: "Manage system accounts, roles, and active access",
      path: adminUserManagementPagePath,
    });
    items.push({
      label: "Production records",
      description: "Review active and completed production tasks",
      path: productionTaskRecordsPagePath,
    });
    items.push({
      label: "Customer orders",
      description: "Search and inspect customer orders",
      path: staffOrderListPagePath,
    });
  }

  if (user.role === "INVENTORY_MANAGER" || user.role === "ADMINISTRATOR") {
    items.push({
      label: "Material supplies",
      description: "Find supplier material and delivery information",
      path: materialSupplyListPagePath,
    });
  }

  if (user.role === "INVENTORY_MANAGER") {
    items.push({
      label: "Inventory materials",
      description: "Review fabric and raw-material stock",
      path: inventoryMaterialListPagePath,
    });
    items.push({
      label: "Add inventory material",
      description: "Create a fabric or raw-material stock record",
      path: newInventoryMaterialPagePath,
    });
  }

  return items;
}

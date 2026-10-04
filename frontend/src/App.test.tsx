import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import axios from "axios";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { App } from "./App";
import {
  getCurrentUser,
  loginUser,
  logoutUser,
  registerCustomer,
  resendEmailVerification,
  requestPasswordReset,
  resetPassword,
  verifyCustomerEmail,
  type AuthUser,
  type UserRole,
} from "./api/auth";
import { getRoleAccess, type InternalRole } from "./api/roleAccess";
import { getDashboardReport } from "./api/reports";
import {
  createProduct,
  deleteProduct,
  getCatalogProduct,
  getCatalogProducts,
  getManagedProducts,
  getProduct,
  uploadProductImage,
  updateProduct,
  type CreateProductResponse,
  type DeleteProductResponse,
  type UpdateProductResponse,
} from "./api/products";
import {
  createMaterialSupply,
  deleteMaterialSupply,
  getMaterialSupply,
  getMaterialSupplies,
  updateMaterialSupply,
  type CreateMaterialSupplyResponse,
  type DeleteMaterialSupplyResponse,
  type MaterialSupply,
  type MaterialSupplyListItem,
  type UpdateMaterialSupplyResponse,
} from "./api/materialSupplies";
import {
  consumeInventoryMaterial,
  createInventoryMaterial,
  deleteInventoryMaterial,
  getInventoryMaterial,
  getInventoryMaterials,
  getLowStockInventoryMaterials,
  receiveInventoryMaterial,
  updateInventoryMaterial,
  type ConsumeInventoryMaterialResponse,
  type CreateInventoryMaterialResponse,
  type DeleteInventoryMaterialResponse,
  type InventoryMaterial,
  type ReceiveInventoryMaterialResponse,
  type UpdateInventoryMaterialResponse,
} from "./api/inventoryMaterials";
import {
  getSupplierProfile,
  saveSupplierProfile,
  type SaveSupplierProfileResponse,
  type SupplierProfile,
} from "./api/supplierProfile";
import {
  customerPlaceOrderPagePath,
  customerOrderHistoryPagePath,
  customerOrderTrackingPagePath,
  dashboardPagePaths,
  deliveryOrderSelectionPagePath,
  getDeliverySchedulePagePath,
  getCustomerPlaceOrderWithItemPath,
  getEditProductPagePath,
  getEditMaterialSupplyPagePath,
  inventoryMaterialListPagePath,
  getEditInventoryMaterialPagePath,
  newProductPagePath,
  newOrderPagePath,
  newQuotationPagePath,
  quotationListPagePath,
  newInventoryMaterialPagePath,
  productCatalogPagePath,
  productManagementPagePath,
  productionTaskCreatePagePath,
  getProductionTaskDetailPagePath,
  getProductDetailPagePath,
  materialSupplyListPagePath,
  newMaterialSupplyPagePath,
  roleLabels,
  staffOrderListPagePath,
  supplierProfilePagePath,
} from "./navigation/navigation";

vi.mock("axios");
vi.mock("./api/auth", () => ({
  getCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  logoutUser: vi.fn(),
  registerCustomer: vi.fn(),
  resendEmailVerification: vi.fn(),
  requestPasswordReset: vi.fn(),
  resetPassword: vi.fn(),
  verifyCustomerEmail: vi.fn(),
  getApiErrorMessage: (error: unknown, fallback: string) =>
    error instanceof Error ? error.message : fallback,
}));
vi.mock("./api/roleAccess", () => ({
  getRoleAccess: vi.fn(),
  isCanceledRequest: () => false,
  isInternalRole: (role: string) => role !== "CUSTOMER",
  roleAccessPagePaths: {
    ADMINISTRATOR: "/access/administrator",
    SUPPLIER: "/access/supplier",
    INVENTORY_MANAGER: "/access/inventory-manager",
    PRODUCTION_MANAGER: "/access/production-manager",
    SALES_OFFICER: "/access/sales-officer",
  },
}));
vi.mock("./api/reports", () => ({
  getDashboardReport: vi.fn(),
}));
vi.mock("./api/products", () => ({
  createProduct: vi.fn(),
  deleteProduct: vi.fn(),
  getCatalogProduct: vi.fn(),
  getCatalogProducts: vi.fn(),
  getManagedProducts: vi.fn(),
  getProduct: vi.fn(),
  uploadProductImage: vi.fn(),
  updateProduct: vi.fn(),
  getProductApiError: (error: unknown, fallback: string) => {
    const details = typeof error === "object" && error !== null
      ? error as { code?: string; status?: number }
      : {};
    return {
      code: details.code,
      message: error instanceof Error ? error.message : fallback,
      fields: {},
      status: details.status,
    };
  },
}));
vi.mock("./api/materialSupplies", () => ({
  deleteMaterialSupply: vi.fn(),
  createMaterialSupply: vi.fn(),
  getMaterialSupply: vi.fn(),
  getMaterialSupplies: vi.fn(),
  updateMaterialSupply: vi.fn(),
  getMaterialSupplyApiError: (error: unknown, fallback: string) => {
    const details = typeof error === "object" && error !== null
      ? error as { code?: string; fields?: Record<string, string>; status?: number }
      : {};
    return {
      code: details.code,
      fields: details.fields ?? {},
      message: error instanceof Error ? error.message : fallback,
      status: details.status,
    };
  },
}));
vi.mock("./api/inventoryMaterials", () => ({
  consumeInventoryMaterial: vi.fn(),
  createInventoryMaterial: vi.fn(),
  deleteInventoryMaterial: vi.fn(),
  getInventoryMaterial: vi.fn(),
  getInventoryMaterials: vi.fn(),
  getLowStockInventoryMaterials: vi.fn(),
  receiveInventoryMaterial: vi.fn(),
  updateInventoryMaterial: vi.fn(),
  getInventoryMaterialApiError: (error: unknown, fallback: string) => {
    const details = typeof error === "object" && error !== null
      ? error as { code?: string; fields?: Record<string, string>; status?: number }
      : {};
    return {
      code: details.code,
      fields: details.fields ?? {},
      message: error instanceof Error ? error.message : fallback,
      status: details.status,
    };
  },
}));
vi.mock("./api/supplierProfile", () => ({
  getSupplierProfile: vi.fn(),
  saveSupplierProfile: vi.fn(),
  getSupplierProfileApiError: (error: unknown, fallback: string) => {
    const details = typeof error === "object" && error !== null
      ? error as { code?: string; fields?: Record<string, string>; status?: number }
      : {};
    return {
      code: details.code,
      fields: details.fields ?? {},
      message: error instanceof Error ? error.message : fallback,
      status: details.status,
    };
  },
}));

const mockedAxios = vi.mocked(axios, true);
const mockedGetCurrentUser = vi.mocked(getCurrentUser);
const mockedLoginUser = vi.mocked(loginUser);
const mockedLogoutUser = vi.mocked(logoutUser);
const mockedRegisterCustomer = vi.mocked(registerCustomer);
const mockedRequestPasswordReset = vi.mocked(requestPasswordReset);
const mockedResendEmailVerification = vi.mocked(resendEmailVerification);
const mockedResetPassword = vi.mocked(resetPassword);
const mockedVerifyCustomerEmail = vi.mocked(verifyCustomerEmail);
const mockedGetRoleAccess = vi.mocked(getRoleAccess);
const mockedGetDashboardReport = vi.mocked(getDashboardReport);
const mockedCreateProduct = vi.mocked(createProduct);
const mockedUploadProductImage = vi.mocked(uploadProductImage);
const mockedDeleteProduct = vi.mocked(deleteProduct);
const mockedGetCatalogProduct = vi.mocked(getCatalogProduct);
const mockedGetCatalogProducts = vi.mocked(getCatalogProducts);
const mockedGetManagedProducts = vi.mocked(getManagedProducts);
const mockedGetProduct = vi.mocked(getProduct);
const mockedUpdateProduct = vi.mocked(updateProduct);
const mockedCreateMaterialSupply = vi.mocked(createMaterialSupply);
const mockedDeleteMaterialSupply = vi.mocked(deleteMaterialSupply);
const mockedGetMaterialSupply = vi.mocked(getMaterialSupply);
const mockedGetMaterialSupplies = vi.mocked(getMaterialSupplies);
const mockedUpdateMaterialSupply = vi.mocked(updateMaterialSupply);
const mockedConsumeInventoryMaterial = vi.mocked(consumeInventoryMaterial);
const mockedCreateInventoryMaterial = vi.mocked(createInventoryMaterial);
const mockedDeleteInventoryMaterial = vi.mocked(deleteInventoryMaterial);
const mockedGetInventoryMaterial = vi.mocked(getInventoryMaterial);
const mockedGetInventoryMaterials = vi.mocked(getInventoryMaterials);
const mockedGetLowStockInventoryMaterials = vi.mocked(getLowStockInventoryMaterials);
const mockedReceiveInventoryMaterial = vi.mocked(receiveInventoryMaterial);
const mockedUpdateInventoryMaterial = vi.mocked(updateInventoryMaterial);
const mockedGetSupplierProfile = vi.mocked(getSupplierProfile);
const mockedSaveSupplierProfile = vi.mocked(saveSupplierProfile);

const customer: AuthUser = {
  id: 1,
  fullName: "Registered Customer",
  email: "customer@example.com",
  role: "CUSTOMER",
};

const savedProductResponse: CreateProductResponse = {
  message: "Garment product created successfully.",
  product: {
    id: 41,
    categoryId: 12,
    name: "Oxford Shirt",
    description: null,
    status: "ACTIVE",
    createdAt: "2026-08-23T05:30:00Z",
    updatedAt: "2026-08-23T05:30:00Z",
    category: {
      id: 12,
      name: "Formal Wear",
      description: null,
      status: "ACTIVE",
      createdAt: "2026-08-23T05:30:00Z",
      updatedAt: "2026-08-23T05:30:00Z",
    },
    variants: [
      {
        id: 73,
        productId: 41,
        size: "L",
        color: "White",
        price: "3990.00",
        status: "UNAVAILABLE",
        createdAt: "2026-08-23T05:30:00Z",
        updatedAt: "2026-08-23T05:30:00Z",
      },
    ],
  },
};

const catalogProduct = {
  ...savedProductResponse.product,
  name: "Classic Crew Neck",
  description: "Soft cotton crew neck for everyday wear.",
  category: {
    ...savedProductResponse.product.category,
    description: "Smart and casual garments.",
  },
  variants: [
    {
      ...savedProductResponse.product.variants[0],
      size: "M",
      color: "Navy Blue",
      price: "2499.90",
      status: "AVAILABLE" as const,
    },
  ],
};

const updatedProductResponse: UpdateProductResponse = {
  message: "Garment product updated successfully.",
  product: {
    ...catalogProduct,
    name: "Updated Crew Neck",
    status: "INACTIVE",
    variants: [
      {
        ...catalogProduct.variants[0],
        size: "L",
        color: "Forest Green",
        price: "2799.50",
        status: "UNAVAILABLE",
      },
    ],
  },
};

const deletedProductResponse: DeleteProductResponse = {
  message: "Garment product deleted successfully.",
  productId: catalogProduct.id,
};

const supplierProfile: SupplierProfile = {
  id: 91,
  userId: 1,
  businessName: "Lanka Textiles",
  contactPhone: "+94 77 123 4567",
  address: "12 Main Street, Colombo",
  createdAt: "2026-08-23T05:30:00Z",
  updatedAt: "2026-08-23T05:30:00Z",
};

const materialSupply: MaterialSupply = {
  id: 101,
  supplierId: supplierProfile.id,
  materialCode: "FAB-COT-001",
  materialName: "Cotton twill fabric",
  materialDescription: "Durable 240 GSM cotton twill",
  quantity: "1250.750",
  unitOfMeasure: "metre",
  unitPrice: "845.50",
  deliveryLeadTimeDays: 7,
  deliveryNotes: "Deliver to the main receiving bay",
  status: "ACTIVE",
  createdAt: "2026-08-23T07:00:00Z",
  updatedAt: "2026-08-23T07:00:00Z",
};

const listedMaterialSupply: MaterialSupplyListItem = {
  ...materialSupply,
  supplierBusinessName: supplierProfile.businessName,
};

const inventoryMaterial: InventoryMaterial = {
  id: 301,
  sourceMaterialSupplyId: null,
  materialCode: "INV-FAB-001",
  materialName: "Cotton twill fabric",
  materialDescription: "Main store 240 GSM cotton twill",
  materialType: "FABRIC",
  unitOfMeasure: "metre",
  currentQuantity: "840.250",
  lowStockThreshold: "100.000",
  status: "ACTIVE",
  stockState: "SUFFICIENT",
  createdAt: "2026-08-23T08:00:00Z",
  updatedAt: "2026-08-23T08:00:00Z",
};

const linkedInventoryMaterial: InventoryMaterial = {
  ...inventoryMaterial,
  sourceMaterialSupplyId: listedMaterialSupply.id,
};

const lowStockInventoryMaterial: InventoryMaterial = {
  ...linkedInventoryMaterial,
  id: 302,
  materialCode: "INV-FAB-LOW",
  materialName: "Low stock cotton",
  currentQuantity: "100.000",
  lowStockThreshold: "100.000",
  stockState: "LOW_STOCK",
};

const createdInventoryMaterialResponse: CreateInventoryMaterialResponse = {
  message: "Inventory material created successfully.",
  material: linkedInventoryMaterial,
};

const consumedInventoryMaterial: InventoryMaterial = {
  ...linkedInventoryMaterial,
  currentQuantity: "800.250",
  updatedAt: "2026-08-23T09:00:00Z",
};

const consumedInventoryMaterialResponse: ConsumeInventoryMaterialResponse = {
  message: "Inventory stock consumed successfully.",
  material: consumedInventoryMaterial,
};

const receivedInventoryMaterial: InventoryMaterial = {
  ...linkedInventoryMaterial,
  currentQuantity: "970.250",
  updatedAt: "2026-08-23T09:05:00Z",
};

const receivedInventoryMaterialResponse: ReceiveInventoryMaterialResponse = {
  message: "Supplier material received into inventory successfully.",
  material: receivedInventoryMaterial,
};

const updatedInventoryMaterial: InventoryMaterial = {
  ...linkedInventoryMaterial,
  materialName: "Updated cotton twill",
  lowStockThreshold: "125.000",
  status: "INACTIVE",
  updatedAt: "2026-08-23T10:00:00Z",
};

const updatedInventoryMaterialResponse: UpdateInventoryMaterialResponse = {
  message: "Inventory material updated successfully.",
  material: updatedInventoryMaterial,
};

const deletedInventoryMaterialResponse: DeleteInventoryMaterialResponse = {
  message: "Inventory material deleted successfully.",
  materialId: linkedInventoryMaterial.id,
};

const roleRoutes: Array<[InternalRole, string]> = [
  ["ADMINISTRATOR", "/access/administrator"],
  ["SUPPLIER", "/access/supplier"],
  ["INVENTORY_MANAGER", "/access/inventory-manager"],
  ["PRODUCTION_MANAGER", "/access/production-manager"],
  ["SALES_OFFICER", "/access/sales-officer"],
];

const dashboardRoutes: Array<[UserRole, string]> = [
  ["ADMINISTRATOR", dashboardPagePaths.ADMINISTRATOR],
  ["SUPPLIER", dashboardPagePaths.SUPPLIER],
  ["INVENTORY_MANAGER", dashboardPagePaths.INVENTORY_MANAGER],
  ["PRODUCTION_MANAGER", dashboardPagePaths.PRODUCTION_MANAGER],
  ["SALES_OFFICER", dashboardPagePaths.SALES_OFFICER],
  ["CUSTOMER", dashboardPagePaths.CUSTOMER],
];

function renderApp(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe("App authentication", () => {
  beforeEach(() => {
    mockedGetCurrentUser.mockResolvedValue(null);
    mockedGetDashboardReport.mockResolvedValue({
      role: "CUSTOMER",
      from: "2026-08-01",
      to: "2026-08-31",
      timeZone: "UTC",
      generatedAt: "2026-08-26T12:00:00Z",
      sections: [],
      notes: ["No permitted operational report sections for this test role."],
    });
    mockedGetCatalogProduct.mockResolvedValue(catalogProduct);
    mockedGetCatalogProducts.mockResolvedValue([]);
    mockedGetManagedProducts.mockResolvedValue([]);
    mockedGetProduct.mockResolvedValue(catalogProduct);
    mockedGetSupplierProfile.mockRejectedValue(Object.assign(
      new Error("No supplier profile exists for this account."),
      { code: "SUPPLIER_PROFILE_NOT_FOUND", status: 404 },
    ));
    mockedSaveSupplierProfile.mockRejectedValue(new Error("Unexpected supplier save."));
    mockedCreateMaterialSupply.mockRejectedValue(new Error("Unexpected material supply save."));
    mockedDeleteMaterialSupply.mockRejectedValue(new Error("Unexpected material supply deletion."));
    mockedGetMaterialSupply.mockRejectedValue(new Error("Unexpected material supply load."));
    mockedGetMaterialSupplies.mockResolvedValue([]);
    mockedUpdateMaterialSupply.mockRejectedValue(new Error("Unexpected material supply update."));
    mockedDeleteInventoryMaterial.mockRejectedValue(
      new Error("Unexpected inventory material deletion."),
    );
    mockedConsumeInventoryMaterial.mockRejectedValue(
      new Error("Unexpected inventory stock usage."),
    );
    mockedCreateInventoryMaterial.mockRejectedValue(
      new Error("Unexpected inventory material save."),
    );
    mockedGetInventoryMaterial.mockRejectedValue(new Error("Unexpected inventory material load."));
    mockedGetInventoryMaterials.mockResolvedValue([]);
    mockedGetLowStockInventoryMaterials.mockResolvedValue({ count: 0, materials: [] });
    mockedReceiveInventoryMaterial.mockRejectedValue(
      new Error("Unexpected inventory stock receipt."),
    );
    mockedUpdateInventoryMaterial.mockRejectedValue(new Error("Unexpected inventory material update."));
    mockedAxios.get.mockResolvedValue({ data: { status: "ok" } });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("keeps the public home page available to guests", async () => {
    renderApp();

    expect(
      screen.getByRole("heading", { name: /manage every stage of garment production/i }),
    ).toBeInTheDocument();
    expect(await screen.findByText("API connection available")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Sign in" })).toBeInTheDocument();
  });

  it("shows a clear unavailable state when the API cannot be reached", async () => {
    mockedAxios.get.mockRejectedValue(new Error("network unavailable"));
    mockedAxios.isCancel.mockReturnValue(false);
    renderApp();

    expect(await screen.findByText("API connection unavailable")).toBeInTheDocument();
  });

  it("explains the integrated role-aware workspace instead of stale foundation guidance", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp();

    expect(await screen.findByText("Role-aware workspace")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Navigation shows only the modules and supporting tools available to your current role.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(
        "Module links will be introduced only by their approved implementation tickets.",
      ),
    ).not.toBeInTheDocument();
  });

  it("registers a customer without sending a selectable role", async () => {
    mockedRegisterCustomer.mockResolvedValue({
      message: "Account created. Check your email for the verification code.",
      user: customer,
    });
    const user = userEvent.setup();
    renderApp("/register");

    await user.type(screen.getByLabelText("Full name"), customer.fullName);
    await user.type(screen.getByLabelText("Email address"), customer.email);
    await user.type(
      screen.getByLabelText(/^Password/),
      "secure-pass-123",
    );
    await user.type(screen.getByLabelText("Confirm password"), "secure-pass-123");
    await user.click(screen.getByRole("button", { name: "Create customer account" }));

    expect(
      await screen.findByRole("heading", { name: "Verify your email" }),
    ).toBeInTheDocument();
    expect(mockedRegisterCustomer).toHaveBeenCalledWith({
      fullName: customer.fullName,
      email: customer.email,
      password: "secure-pass-123",
    });
    expect(screen.getByLabelText("Email address")).toHaveValue(customer.email);
  });

  it("verifies a customer email and offers a throttled resend action", async () => {
    mockedVerifyCustomerEmail.mockResolvedValue({
      message: "Email verified successfully. You can now sign in.",
    });
    mockedResendEmailVerification.mockResolvedValue({
      message: "If the account is eligible, a new verification code will be sent.",
    });
    const user = userEvent.setup();
    renderApp("/verify-email?email=customer%40example.com");

    await user.click(screen.getByRole("button", { name: "Send a new code" }));
    expect(await screen.findByText(
      /If the account is eligible, a new verification code will be sent\. Check customer@example\.com's Inbox and Spam folder\./,
    )).toBeInTheDocument();
    expect(mockedResendEmailVerification).toHaveBeenCalledWith({
      email: "customer@example.com",
    });

    await user.type(screen.getByLabelText("Verification code"), "428196");
    await user.click(screen.getByRole("button", { name: "Verify email" }));

    expect(await screen.findByRole("heading", { name: "Email verified" }))
      .toBeInTheDocument();
    expect(mockedVerifyCustomerEmail).toHaveBeenCalledWith({
      email: "customer@example.com",
      code: "428196",
    });
  });

  it("logs in a valid user and restores the public landing page", async () => {
    mockedLoginUser.mockResolvedValue({ message: "Login successful.", user: customer });
    const user = userEvent.setup();
    renderApp("/login");

    await user.type(screen.getByLabelText("Email address"), customer.email);
    await user.type(screen.getByLabelText("Password"), "secure-pass-123");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    const navigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(navigation).getByRole("link", { name: /customer dashboard/i }))
      .toBeInTheDocument();
    expect(mockedLoginUser).toHaveBeenCalledWith({
      email: customer.email,
      password: "secure-pass-123",
    });
  });

  it("shows the server login error without exposing account details", async () => {
    mockedLoginUser.mockRejectedValue(new Error("Invalid email or password."));
    const user = userEvent.setup();
    renderApp("/login");

    await user.type(screen.getByLabelText("Email address"), "missing@example.com");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password.");
  });

  it("offers account recovery from sign in and keeps the request response enumeration-safe", async () => {
    mockedRequestPasswordReset.mockResolvedValue({
      message: "If an active account matches that email, a password reset link will be sent.",
    });
    const user = userEvent.setup();
    renderApp("/login");

    await user.click(screen.getByRole("link", { name: "Forgot password?" }));
    await user.type(screen.getByLabelText("Email address"), "missing@example.com");
    await user.click(screen.getByRole("button", { name: "Send reset instructions" }));

    expect(await screen.findByRole("heading", { name: "Check your email" })).toBeInTheDocument();
    expect(screen.getByText(
      "If an active account matches that email, a password reset link will be sent.",
    )).toBeInTheDocument();
    expect(mockedRequestPasswordReset).toHaveBeenCalledWith({ email: "missing@example.com" });
  });

  it("validates reset password confirmation before calling the API", async () => {
    const user = userEvent.setup();
    renderApp("/reset-password?token=test-reset-token");

    await user.type(screen.getByLabelText("New password"), "new-secure-password-456");
    await user.type(screen.getByLabelText("Confirm new password"), "different-password-789");
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Passwords do not match.");
    expect(mockedResetPassword).not.toHaveBeenCalled();
  });

  it("completes a valid password reset and does not expose the token in page copy", async () => {
    mockedResetPassword.mockResolvedValue({
      message: "Password updated successfully. You can now sign in with your new password.",
    });
    const user = userEvent.setup();
    renderApp("/reset-password?token=test-reset-token");

    await user.type(screen.getByLabelText("New password"), "new-secure-password-456");
    await user.type(screen.getByLabelText("Confirm new password"), "new-secure-password-456");
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(await screen.findByRole("heading", { name: "Password updated" })).toBeInTheDocument();
    expect(mockedResetPassword).toHaveBeenCalledWith({
      token: "test-reset-token",
      password: "new-secure-password-456",
    });
    expect(screen.queryByText("test-reset-token")).not.toBeInTheDocument();
  });

  it("handles missing and rejected reset tokens safely", async () => {
    const user = userEvent.setup();
    const { unmount } = renderApp("/reset-password");
    expect(screen.getByRole("heading", { name: "Invalid reset link" })).toBeInTheDocument();
    unmount();

    mockedResetPassword.mockRejectedValue(
      new Error("This password reset link is invalid or has expired. Request a new reset link."),
    );
    renderApp("/reset-password?token=expired-reset-token");
    await user.type(screen.getByLabelText("New password"), "new-secure-password-456");
    await user.type(screen.getByLabelText("Confirm new password"), "new-secure-password-456");
    await user.click(screen.getByRole("button", { name: "Update password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This password reset link is invalid or has expired. Request a new reset link.",
    );
  });

  it("logs out an authenticated user and returns to guest actions", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    mockedLogoutUser.mockResolvedValue();
    const user = userEvent.setup();
    renderApp(dashboardPagePaths.CUSTOMER);

    await user.click(await screen.findByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(mockedLogoutUser).toHaveBeenCalledOnce());
    expect(await screen.findByRole("heading", { name: /Manage every stage of garment production/i }))
      .toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Sign in" })).toBeInTheDocument();
  });

  it.each(roleRoutes)("allows %s to open its protected page", async (role, path) => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role });
    mockedGetRoleAccess.mockResolvedValue({ role, message: "Role access confirmed." });
    renderApp(path);

    expect(await screen.findByText("Role access confirmed.")).toBeInTheDocument();
    expect(screen.getByText(`The server authorized ${role.replaceAll("_", " ")}.`))
      .toBeInTheDocument();
    expect(mockedGetRoleAccess).toHaveBeenCalledWith(role, expect.any(AbortSignal));
  });

  it.each(roleRoutes)("denies CUSTOMER access to the %s page", async (_role, path) => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(path);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetRoleAccess).not.toHaveBeenCalled();
  });

  it("returns a guest protected-route request directly to the public home page", async () => {
    renderApp("/access/administrator");

    expect(await screen.findByRole("heading", { name: /Manage every stage of garment production/i }))
      .toBeInTheDocument();
    expect(mockedGetRoleAccess).not.toHaveBeenCalled();
  });

  it.each(dashboardRoutes)(
    "shows only the %s dashboard navigation for that role",
    async (role) => {
      mockedGetCurrentUser.mockResolvedValue({ ...customer, role });
      renderApp();

      const navigation = await screen.findByRole("navigation", {
        name: "Primary navigation",
      });
      expect(
        within(navigation).getByRole("link", {
          name: new RegExp(`${roleLabels[role]} dashboard`, "i"),
        }),
      ).toBeInTheDocument();

      for (const [otherRole] of dashboardRoutes) {
        if (otherRole !== role) {
          expect(
            within(navigation).queryByRole("link", {
              name: new RegExp(`${roleLabels[otherRole]} dashboard`, "i"),
            }),
          ).not.toBeInTheDocument();
        }
      }

      if (role === "CUSTOMER") {
        expect(
          within(navigation).queryByRole("link", { name: /authorization check/i }),
        ).not.toBeInTheDocument();
      } else {
        expect(
          within(navigation).getByRole("link", { name: /authorization check/i }),
        ).toBeInTheDocument();
      }
    },
  );

  it("shows user-account management navigation only to Administrators", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "ADMINISTRATOR" });
    const administratorRender = renderApp();

    const administratorNavigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(administratorNavigation).getByRole("link", { name: "User accounts" }))
      .toHaveAttribute("href", "/admin/users");
    administratorRender.unmount();

    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp();
    const customerNavigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(customerNavigation).queryByRole("link", { name: "User accounts" }))
      .not.toBeInTheDocument();
  });

  it.each(dashboardRoutes)("allows %s to open its dashboard", async (role, path) => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role });
    renderApp(path);

    expect(
      await screen.findByRole("heading", {
        name: `${roleLabels[role]} dashboard`,
      }),
    ).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "No permitted operational report sections" }))
      .toBeInTheDocument();
  });

  it("denies direct access to another role dashboard", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    renderApp(dashboardPagePaths.ADMINISTRATOR);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("lets a Sales Officer save a valid garment product", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedCreateProduct.mockResolvedValue(savedProductResponse);
    const user = userEvent.setup();
    renderApp(newProductPagePath);

    await user.type(await screen.findByLabelText("Product name"), "Oxford Shirt");
    await user.type(screen.getByLabelText("Category"), "Formal Wear");
    await user.type(screen.getByLabelText("Size"), "L");
    await user.type(screen.getByLabelText("Color"), "White");
    await user.type(screen.getByLabelText("Price"), "3990.00");
    await user.selectOptions(screen.getByLabelText("Availability"), "UNAVAILABLE");
    await user.selectOptions(screen.getByLabelText("Product status"), "INACTIVE");
    await user.click(screen.getByRole("button", { name: "Save garment product" }));

    expect(await screen.findByText("Product saved")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Oxford Shirt" })).toBeInTheDocument();
    expect(mockedCreateProduct).toHaveBeenCalledWith({
      name: "Oxford Shirt",
      category: "Formal Wear",
      variants: [{
        size: "L",
        color: "White",
        price: "3990.00",
        availability: "UNAVAILABLE",
      }],
    });
  });

  it("uploads a local photo and saves multiple independently priced variants", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    const secondVariant = {
      ...savedProductResponse.product.variants[0],
      id: 74,
      size: "XL",
      color: "Navy",
      price: "4490.00",
      status: "AVAILABLE" as const,
    };
    mockedUploadProductImage.mockResolvedValue({
      message: "Product photo uploaded successfully.",
      imageUrl: "/api/product-images/123e4567-e89b-12d3-a456-426614174000.jpg",
      mediaType: "image/jpeg",
      size: 3,
    });
    mockedCreateProduct.mockResolvedValue({
      ...savedProductResponse,
      product: {
        ...savedProductResponse.product,
        imageUrl: "/api/product-images/123e4567-e89b-12d3-a456-426614174000.jpg",
        variants: [savedProductResponse.product.variants[0], secondVariant],
      },
    });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:product-preview"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });

    const user = userEvent.setup();
    renderApp(newProductPagePath);
    await user.type(await screen.findByLabelText("Product name"), "Oxford Shirt");
    await user.type(screen.getByLabelText("Category"), "Formal Wear");
    await user.type(screen.getByLabelText("Size"), "L");
    await user.type(screen.getByLabelText("Color"), "White");
    await user.type(screen.getByLabelText("Price"), "3990.00");
    await user.upload(
      screen.getByLabelText("Choose a local product photo"),
      new File([new Uint8Array([0xff, 0xd8, 0xff])], "shirt.jpg", { type: "image/jpeg" }),
    );
    await user.click(screen.getByRole("button", { name: "Add another option" }));
    await user.type(screen.getByLabelText("Size 2"), "XL");
    await user.type(screen.getByLabelText("Color 2"), "Navy");
    await user.type(screen.getByLabelText("Price 2"), "4490.00");
    await user.click(screen.getByRole("button", { name: "Save garment product" }));

    expect(mockedUploadProductImage).toHaveBeenCalledWith(expect.any(File));
    expect(mockedCreateProduct).toHaveBeenCalledWith({
      name: "Oxford Shirt",
      category: "Formal Wear",
      imageUrl: "/api/product-images/123e4567-e89b-12d3-a456-426614174000.jpg",
      variants: [
        { size: "L", color: "White", price: "3990.00", availability: "AVAILABLE" },
        { size: "XL", color: "Navy", price: "4490.00", availability: "AVAILABLE" },
      ],
    });
    expect(await screen.findByText(/2 variants were saved successfully/i)).toBeInTheDocument();
  });

  it("blocks incomplete and invalid product values before calling the API", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    const user = userEvent.setup();
    renderApp(newProductPagePath);

    await user.type(await screen.findByLabelText("Product name"), "   ");
    await user.type(screen.getByLabelText("Price"), "0");
    await user.click(screen.getByRole("button", { name: "Save garment product" }));

    expect(await screen.findByText("Product name is required.")).toBeInTheDocument();
    expect(screen.getByText("Category is required.")).toBeInTheDocument();
    expect(screen.getByText("Size is required.")).toBeInTheDocument();
    expect(screen.getByText("Color is required.")).toBeInTheDocument();
    expect(screen.getByText(/Price must be positive/)).toBeInTheDocument();
    expect(mockedCreateProduct).not.toHaveBeenCalled();
  });

  it("shows a clear product API error and keeps the form available", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedCreateProduct.mockRejectedValue(
      new Error("The selected category is inactive and cannot receive new products."),
    );
    const user = userEvent.setup();
    renderApp(newProductPagePath);

    await user.type(await screen.findByLabelText("Product name"), "Oxford Shirt");
    await user.type(screen.getByLabelText("Category"), "Archived Range");
    await user.type(screen.getByLabelText("Size"), "L");
    await user.type(screen.getByLabelText("Color"), "White");
    await user.type(screen.getByLabelText("Price"), "3990.00");
    await user.click(screen.getByRole("button", { name: "Save garment product" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The selected category is inactive and cannot receive new products.",
    );
    expect(screen.getByRole("button", { name: "Save garment product" })).toBeEnabled();
  });

  it("shows product creation navigation only to the Sales Officer", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    const salesOfficerView = renderApp();

    const salesNavigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(salesNavigation).getByRole("link", { name: /add garment product/i }))
      .toHaveAttribute("href", newProductPagePath);
    expect(within(salesNavigation).getByRole("link", { name: /create customer order/i }))
      .toHaveAttribute("href", newOrderPagePath);
    expect(within(salesNavigation).getByRole("link", { name: /^customer orders$/i }))
      .toHaveAttribute("href", staffOrderListPagePath);

    salesOfficerView.unmount();
    mockedGetCurrentUser.mockResolvedValue(customer);
    const customerView = renderApp();
    const customerNavigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(customerNavigation).queryByRole("link", { name: /add garment product/i }))
      .not.toBeInTheDocument();
    expect(within(customerNavigation).queryByRole("link", { name: /create customer order/i }))
      .not.toBeInTheDocument();
    expect(within(customerNavigation).getByRole("link", { name: /place an order/i }))
      .toHaveAttribute("href", customerPlaceOrderPagePath);
    expect(within(customerNavigation).getByRole("link", { name: /order history/i }))
      .toHaveAttribute("href", customerOrderHistoryPagePath);
    expect(within(customerNavigation).getByRole("link", { name: /track an order/i }))
      .toHaveAttribute("href", customerOrderTrackingPagePath);

    customerView.unmount();
    renderApp(newProductPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();

    cleanup();
    renderApp(newOrderPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("protects customer tracking from guests and non-customer roles", async () => {
    mockedGetCurrentUser.mockResolvedValue(null);
    renderApp(customerOrderTrackingPagePath);
    expect(await screen.findByRole("heading", { name: /Manage every stage of garment production/i }))
      .toBeInTheDocument();

    cleanup();
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    renderApp(customerOrderTrackingPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("protects staff order inspection from customers and customer history from staff", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(staffOrderListPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();

    cleanup();
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    renderApp(customerOrderHistoryPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("shows customer-order inspection navigation to administrators", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "ADMINISTRATOR" });
    renderApp();

    const navigation = await screen.findByRole("navigation", { name: "Primary navigation" });
    expect(within(navigation).getByRole("link", { name: /^customer orders$/i }))
      .toHaveAttribute("href", staffOrderListPagePath);
  });


  it("shows delivery-order selection only to Sales Officers and protects the route", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    renderApp();
    const salesNavigation = await screen.findByRole("navigation", { name: "Primary navigation" });
    expect(within(salesNavigation).getByRole("link", { name: /^prepare delivery$/i }))
      .toHaveAttribute("href", deliveryOrderSelectionPagePath);

    cleanup();
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(deliveryOrderSelectionPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();

    cleanup();
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(getDeliverySchedulePagePath(9101));
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });


  it("shows production task creation only to Production Managers and protects the route", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "PRODUCTION_MANAGER" });
    renderApp();
    const productionNavigation = await screen.findByRole("navigation", { name: "Primary navigation" });
    expect(within(productionNavigation).getByRole("link", { name: /^create production task$/i }))
      .toHaveAttribute("href", productionTaskCreatePagePath);

    cleanup();
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(productionTaskCreatePagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("protects production task work details from unauthorized roles", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(getProductionTaskDetailPagePath(8801));
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("shows quotation navigation only to Sales Officers and protects quotation creation", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    renderApp();
    const salesNavigation = await screen.findByRole("navigation", { name: "Primary navigation" });
    expect(within(salesNavigation).getByRole("link", { name: /^customer quotations$/i }))
      .toHaveAttribute("href", quotationListPagePath);

    cleanup();
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(newQuotationPagePath);
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("requires login for the registered-customer order page", async () => {
    mockedGetCurrentUser.mockResolvedValue(null);
    renderApp(customerPlaceOrderPagePath);

    expect(await screen.findByRole("heading", { name: /Manage every stage of garment production/i }))
      .toBeInTheDocument();
  });

  it("protects the registered-customer order page from other roles", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    renderApp(customerPlaceOrderPagePath);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
  });

  it("preloads and updates a product while preserving its stable IDs", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedUpdateProduct.mockResolvedValue(updatedProductResponse);
    const user = userEvent.setup();
    renderApp(getEditProductPagePath(catalogProduct.id));

    expect(await screen.findByRole("heading", { name: "Edit garment product" }))
      .toBeInTheDocument();
    expect(screen.getByLabelText("Product name")).toHaveValue(catalogProduct.name);
    expect(screen.getByLabelText("Category")).toHaveValue(catalogProduct.category.name);
    expect(screen.getByLabelText("Size")).toHaveValue("M");
    expect(screen.getByLabelText("Color")).toHaveValue("Navy Blue");
    expect(screen.getByLabelText("Price")).toHaveValue("2499.90");
    expect(screen.getByLabelText("Product status")).toHaveValue("ACTIVE");

    await user.clear(screen.getByLabelText("Product name"));
    await user.type(screen.getByLabelText("Product name"), " Updated Crew Neck ");
    await user.clear(screen.getByLabelText("Size"));
    await user.type(screen.getByLabelText("Size"), " L ");
    await user.clear(screen.getByLabelText("Color"));
    await user.type(screen.getByLabelText("Color"), " Forest Green ");
    await user.clear(screen.getByLabelText("Price"));
    await user.type(screen.getByLabelText("Price"), "2799.50");
    await user.selectOptions(screen.getByLabelText("Availability"), "UNAVAILABLE");
    await user.click(screen.getByRole("button", { name: "Update garment product" }));

    expect(await screen.findByText(/Garment product updated successfully/))
      .toHaveTextContent(`Product ID ${catalogProduct.id}`);
    expect(screen.getByText(/Garment product updated successfully/))
      .toHaveTextContent(`variant ID ${catalogProduct.variants[0].id}`);
    expect(mockedGetProduct).toHaveBeenCalledWith(
      catalogProduct.id,
      expect.any(AbortSignal),
    );
    expect(mockedUpdateProduct).toHaveBeenCalledWith(catalogProduct.id, {
      variantId: catalogProduct.variants[0].id,
      name: "Updated Crew Neck",
      category: catalogProduct.category.name,
      description: catalogProduct.description,
      size: "L",
      color: "Forest Green",
      price: "2799.50",
      availability: "UNAVAILABLE",
      status: "INACTIVE",
    });
    expect(screen.getByLabelText("Product name")).toHaveValue("Updated Crew Neck");
  });

  it("reuses create validation and blocks an invalid product update", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    const user = userEvent.setup();
    renderApp(getEditProductPagePath(catalogProduct.id));

    await screen.findByRole("heading", { name: "Edit garment product" });
    await user.clear(screen.getByLabelText("Product name"));
    await user.clear(screen.getByLabelText("Price"));
    await user.type(screen.getByLabelText("Price"), "0");
    await user.click(screen.getByRole("button", { name: "Update garment product" }));

    expect(screen.getByText("Product name is required.")).toBeInTheDocument();
    expect(screen.getByText(/Price must be positive/)).toBeInTheDocument();
    expect(mockedUpdateProduct).not.toHaveBeenCalled();
  });

  it("protects the product editor from non-Sales-Officer users", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(getEditProductPagePath(catalogProduct.id));

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetProduct).not.toHaveBeenCalled();
  });

  it("requires confirmation before deleting a product and removes stale discontinue actions", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedDeleteProduct.mockResolvedValue(deletedProductResponse);
    mockedGetCatalogProducts.mockResolvedValue([]);
    const user = userEvent.setup();
    renderApp(getEditProductPagePath(catalogProduct.id));

    await screen.findByRole("heading", { name: "Edit garment product" });
    expect(screen.queryByRole("button", { name: /Discontinue product/i })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete product" }));

    expect(mockedDeleteProduct).not.toHaveBeenCalled();
    expect(screen.getByRole("alertdialog", { name: "Delete product?" }))
      .toHaveTextContent("Products already used in orders or quotations cannot be deleted.");
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Delete product" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(mockedDeleteProduct).toHaveBeenCalledWith(catalogProduct.id);
    expect(await screen.findByRole("heading", { name: "Product management" }))
      .toBeInTheDocument();
  });

  it("shows the backend conflict message when a product is already in use", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedDeleteProduct.mockRejectedValue(Object.assign(
      new Error("This product cannot be deleted because it is already used by existing orders or quotations."),
      { code: "PRODUCT_IN_USE", status: 409 },
    ));
    const user = userEvent.setup();
    renderApp(getEditProductPagePath(catalogProduct.id));

    await screen.findByRole("heading", { name: "Edit garment product" });
    await user.click(screen.getByRole("button", { name: "Delete product" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This product cannot be deleted because it is already used by existing orders or quotations.",
    );
    expect(screen.getByRole("alertdialog", { name: "Delete product?" }))
      .toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled();
  });

  it("shows the reusable loading state while the public catalog is fetched", () => {
    mockedGetCatalogProducts.mockReturnValue(new Promise(() => undefined));
    renderApp(productCatalogPagePath);

    expect(screen.getByRole("status")).toHaveTextContent("Loading garment catalog");
  });

  it("lets a guest browse available product summaries", async () => {
    mockedGetCatalogProducts.mockResolvedValue([catalogProduct]);
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("heading", { name: "Garment catalog" }))
      .toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(screen.getByText("Formal Wear")).toBeInTheDocument();
    expect(screen.getByText("M · Navy Blue")).toBeInTheDocument();
    expect(screen.getByText("Price 2499.90")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View product details" }))
      .toHaveAttribute("href", getProductDetailPagePath(catalogProduct.id));
    expect(screen.queryByRole("link", { name: /to cart/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Availability")).not.toBeInTheDocument();

    const navigation = screen.getByRole("navigation", { name: "Primary navigation" });
    expect(within(navigation).getByRole("link", { name: /garment catalog/i }))
      .toHaveAttribute("href", productCatalogPagePath);
    expect(mockedGetCatalogProducts).toHaveBeenCalledWith(
      {},
      expect.any(AbortSignal),
    );
  });

  it("lets a registered customer open the same public catalog", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    mockedGetCatalogProducts.mockResolvedValue([catalogProduct]);
    const user = userEvent.setup();
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    const navigation = screen.getByRole("navigation", { name: "Primary navigation" });
    expect(within(navigation).getByRole("link", { name: /garment catalog/i }))
      .toBeInTheDocument();
    const addToCartLink = screen.getByRole("link", {
      name: `Add ${catalogProduct.name} to cart`,
    });
    expect(addToCartLink).toHaveAttribute(
      "href",
      getCustomerPlaceOrderWithItemPath(catalogProduct.id, catalogProduct.variants[0].id),
    );

    await user.click(addToCartLink);
    expect(await screen.findByRole("heading", { name: "Place an order" }))
      .toBeInTheDocument();
    expect(screen.getByLabelText("Product")).toHaveValue(String(catalogProduct.id));
    expect(screen.getByLabelText("Size / color"))
      .toHaveValue(String(catalogProduct.variants[0].id));
  });

  it("shows one cart action per product and lets the customer choose the variant", async () => {
    const secondVariant = {
      ...catalogProduct.variants[0],
      id: catalogProduct.variants[0].id + 1,
      size: "L",
      color: "Navy Blue",
      price: "2699.90",
    };
    mockedGetCurrentUser.mockResolvedValue(customer);
    mockedGetCatalogProducts.mockResolvedValue([{
      ...catalogProduct,
      variants: [catalogProduct.variants[0], secondVariant],
    }]);
    const user = userEvent.setup();
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: `Add ${catalogProduct.name} to cart` }))
      .toHaveLength(1);

    await user.selectOptions(screen.getByLabelText("Choose size and colour"), String(secondVariant.id));
    expect(screen.getByRole("link", { name: `Add ${catalogProduct.name} to cart` }))
      .toHaveAttribute(
        "href",
        getCustomerPlaceOrderWithItemPath(catalogProduct.id, secondVariant.id),
      );
  });

  it("gives Sales Officers a protected management view for inactive products and unavailable variants", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedGetManagedProducts.mockResolvedValue([{
      ...catalogProduct,
      status: "INACTIVE",
      variants: [{ ...catalogProduct.variants[0], status: "UNAVAILABLE" }],
    }]);
    renderApp(productManagementPagePath);

    expect(await screen.findByRole("heading", { name: "Product management" }))
      .toBeInTheDocument();
    expect(screen.getByText("INACTIVE")).toBeInTheDocument();
    expect(screen.getByText(/UNAVAILABLE$/, { selector: "span" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit" })).toHaveAttribute(
      "href",
      getEditProductPagePath(catalogProduct.id),
    );
    expect(screen.getByRole("button", { name: "Delete" })).toBeInTheDocument();
  });

  it("animates a successful staff product removal only after the backend confirms deletion", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedGetManagedProducts.mockResolvedValue([catalogProduct]);
    mockedDeleteProduct.mockResolvedValue(deletedProductResponse);
    const user = userEvent.setup();
    renderApp(productManagementPagePath);

    const productHeading = await screen.findByRole("heading", { name: catalogProduct.name });
    const removingProduct = productHeading.closest("li");
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog", { name: "Delete product?" });
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    expect(mockedDeleteProduct).toHaveBeenCalledWith(catalogProduct.id);
    await waitFor(() => expect(removingProduct).toHaveAttribute("data-motion-state", "closed"));
    expect(removingProduct).toHaveAttribute("inert");
    await waitFor(() => expect(screen.queryByRole("heading", { name: catalogProduct.name })).not.toBeInTheDocument());
  });

  it("keeps a staff product visible when deletion fails", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SALES_OFFICER" });
    mockedGetManagedProducts.mockResolvedValue([catalogProduct]);
    mockedDeleteProduct.mockRejectedValue(Object.assign(
      new Error("This product cannot be deleted because it is already used by existing orders or quotations."),
      { code: "PRODUCT_IN_USE", status: 409 },
    ));
    const user = userEvent.setup();
    renderApp(productManagementPagePath);

    expect(await screen.findByRole("heading", { name: catalogProduct.name })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(within(screen.getByRole("alertdialog", { name: "Delete product?" })).getByRole("button", { name: "Delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This product cannot be deleted because it is already used by existing orders or quotations.",
    );
    expect(screen.getByRole("heading", { name: catalogProduct.name })).toBeInTheDocument();
  });

  it("lets a registered customer add an available option from product details", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(getProductDetailPagePath(catalogProduct.id));

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add to cart" })).toHaveAttribute(
      "href",
      getCustomerPlaceOrderWithItemPath(catalogProduct.id, catalogProduct.variants[0].id),
    );
  });

  it("shows a clear empty state when no products are publicly available", async () => {
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("heading", { name: "No garments available" }))
      .toBeInTheDocument();
    expect(screen.getByText(/no active garments with available size and color/i))
      .toBeInTheDocument();
  });

  it("shows a catalog error and retries the query", async () => {
    mockedGetCatalogProducts
      .mockRejectedValueOnce(new Error("Catalog service unavailable."))
      .mockResolvedValueOnce([catalogProduct]);
    const user = userEvent.setup();
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Catalog service unavailable.",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(mockedGetCatalogProducts).toHaveBeenCalledTimes(2);
  });

  it("submits normalized combined catalog filters to the API", async () => {
    mockedGetCatalogProducts.mockResolvedValue([catalogProduct]);
    const user = userEvent.setup();
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    await user.type(screen.getByLabelText("Product name"), "  Classic  ");
    await user.type(screen.getByLabelText("Category"), "  Formal Wear  ");
    await user.type(screen.getByLabelText("Size"), " M ");
    await user.type(screen.getByLabelText("Color"), " Navy Blue ");
    await user.click(screen.getByRole("button", { name: "Search catalog" }));

    await waitFor(() => expect(mockedGetCatalogProducts).toHaveBeenLastCalledWith(
      {
        search: "Classic",
        category: "Formal Wear",
        size: "M",
        color: "Navy Blue",
      },
      expect.any(AbortSignal),
    ));
  });

  it("shows no results and clearing filters restores the permitted catalog", async () => {
    mockedGetCatalogProducts
      .mockResolvedValueOnce([catalogProduct])
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([catalogProduct]);
    const user = userEvent.setup();
    renderApp(productCatalogPagePath);

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    await user.type(screen.getByLabelText("Product name"), "Missing product");
    await user.click(screen.getByRole("button", { name: "Search catalog" }));

    expect(await screen.findByRole("heading", { name: "No matching garments" }))
      .toBeInTheDocument();
    expect(screen.getByText(/match all of the selected filters/i)).toBeInTheDocument();
    const clearButtons = screen.getAllByRole("button", { name: "Clear filters" });
    await user.click(clearButtons.at(-1)!);

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(screen.getByLabelText("Product name")).toHaveValue("");
    expect(mockedGetCatalogProducts).toHaveBeenLastCalledWith(
      {},
      expect.any(AbortSignal),
    );
  });

  it("shows a loading state while product details are fetched", () => {
    mockedGetCatalogProduct.mockReturnValue(new Promise(() => undefined));
    renderApp(getProductDetailPagePath(catalogProduct.id));

    expect(screen.getByRole("status")).toHaveTextContent("Loading product details");
  });

  it("opens a public product detail page with stored variant data", async () => {
    renderApp(getProductDetailPagePath(catalogProduct.id));

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(screen.getByText("Soft cotton crew neck for everyday wear."))
      .toBeInTheDocument();
    expect(screen.getByText("Smart and casual garments.")).toBeInTheDocument();
    expect(screen.getByText(String(catalogProduct.id))).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "M · Navy Blue" })).toBeInTheDocument();
    expect(screen.getByText("2499.90")).toBeInTheDocument();
    expect(screen.getAllByText("available").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "Back to garment catalog" })[0])
      .toHaveAttribute("href", productCatalogPagePath);
    expect(mockedGetCatalogProduct).toHaveBeenCalledWith(
      catalogProduct.id,
      expect.any(AbortSignal),
    );
  });

  it("handles an invalid product route without calling the API", async () => {
    renderApp("/products/not-a-valid-id");

    expect(await screen.findByRole("heading", { name: "Product not found" }))
      .toBeInTheDocument();
    expect(mockedGetCatalogProduct).not.toHaveBeenCalled();
  });

  it("shows a safe product-not-found state for a missing public product", async () => {
    mockedGetCatalogProduct.mockRejectedValue(Object.assign(
      new Error("The requested garment product was not found."),
      { code: "PRODUCT_NOT_FOUND", status: 404 },
    ));
    renderApp("/products/999999");

    expect(await screen.findByRole("heading", { name: "Product not found" }))
      .toBeInTheDocument();
    expect(screen.getByText(/does not exist or is not currently available/i))
      .toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to garment catalog" }))
      .toBeInTheDocument();
  });

  it("shows a product detail error and retries safely", async () => {
    mockedGetCatalogProduct
      .mockRejectedValueOnce(new Error("Product service unavailable."))
      .mockResolvedValueOnce(catalogProduct);
    const user = userEvent.setup();
    renderApp(getProductDetailPagePath(catalogProduct.id));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Product service unavailable.",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("heading", { name: "Classic Crew Neck" }))
      .toBeInTheDocument();
    expect(mockedGetCatalogProduct).toHaveBeenCalledTimes(2);
  });

  it("creates the logged-in supplier's profile without sending an owner ID", async () => {
    const createdResponse: SaveSupplierProfileResponse = {
      message: "Supplier profile created successfully.",
      profile: supplierProfile,
    };
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedSaveSupplierProfile.mockResolvedValue(createdResponse);
    const user = userEvent.setup();
    renderApp(supplierProfilePagePath);

    expect(await screen.findByRole("heading", { name: "Create supplier profile" }))
      .toBeInTheDocument();
    expect(screen.getByText(/no supplier profile exists yet/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Business name"), "  Lanka   Textiles  ");
    await user.type(screen.getByLabelText("Contact phone"), "  +94 77 123 4567  ");
    await user.type(screen.getByLabelText("Address"), "  12 Main Street,   Colombo  ");
    await user.click(screen.getByRole("button", { name: "Create supplier profile" }));

    expect(mockedSaveSupplierProfile).toHaveBeenCalledWith({
      businessName: "Lanka Textiles",
      contactPhone: "+94 77 123 4567",
      address: "12 Main Street, Colombo",
    });
    expect(await screen.findByText(/supplier profile created successfully/i))
      .toHaveTextContent("Supplier ID 91");
    expect(screen.getByRole("heading", { name: "Edit supplier profile" }))
      .toBeInTheDocument();
  });

  it("preloads and updates the logged-in supplier's existing profile", async () => {
    const updatedProfile = {
      ...supplierProfile,
      businessName: "Lanka Sustainable Textiles",
      updatedAt: "2026-08-23T06:30:00Z",
    };
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetSupplierProfile.mockResolvedValue(supplierProfile);
    mockedSaveSupplierProfile.mockResolvedValue({
      message: "Supplier profile updated successfully.",
      profile: updatedProfile,
    });
    const user = userEvent.setup();
    renderApp(supplierProfilePagePath);

    expect(await screen.findByRole("heading", { name: "Edit supplier profile" }))
      .toBeInTheDocument();
    const businessName = screen.getByLabelText("Business name");
    expect(businessName).toHaveValue("Lanka Textiles");
    expect(screen.getByLabelText("Contact phone")).toHaveValue(supplierProfile.contactPhone);
    expect(screen.getByLabelText("Address")).toHaveValue(supplierProfile.address);

    await user.clear(businessName);
    await user.type(businessName, updatedProfile.businessName);
    await user.click(screen.getByRole("button", { name: "Update supplier profile" }));

    expect(mockedSaveSupplierProfile).toHaveBeenCalledWith({
      businessName: updatedProfile.businessName,
      contactPhone: supplierProfile.contactPhone,
      address: supplierProfile.address,
    });
    expect(await screen.findByText(/supplier profile updated successfully/i))
      .toHaveTextContent("Supplier ID 91");
    expect(businessName).toHaveValue(updatedProfile.businessName);
  });

  it("rejects incomplete supplier profile values before calling the API", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    const user = userEvent.setup();
    renderApp(supplierProfilePagePath);

    await screen.findByRole("heading", { name: "Create supplier profile" });
    await user.click(screen.getByRole("button", { name: "Create supplier profile" }));

    expect(screen.getByText("Business name is required.")).toBeInTheDocument();
    expect(screen.getByText("Contact phone is required.")).toBeInTheDocument();
    expect(screen.getByText("Address is required.")).toBeInTheDocument();
    expect(mockedSaveSupplierProfile).not.toHaveBeenCalled();
  });

  it("protects the supplier profile route and hides its navigation from customers", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(supplierProfilePagePath);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetSupplierProfile).not.toHaveBeenCalled();

    cleanup();
    renderApp();
    const navigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(navigation).queryByRole("link", { name: "Supplier profile" }))
      .not.toBeInTheDocument();
  });

  it("shows a supplier profile load error and retries", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetSupplierProfile
      .mockRejectedValueOnce(new Error("Supplier service unavailable."))
      .mockResolvedValueOnce(supplierProfile);
    const user = userEvent.setup();
    renderApp(supplierProfilePagePath);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Supplier service unavailable.",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByRole("heading", { name: "Edit supplier profile" }))
      .toBeInTheDocument();
    expect(mockedGetSupplierProfile).toHaveBeenCalledTimes(2);
  });

  it("creates a material supply under the logged-in supplier and shows the saved record", async () => {
    const createdResponse: CreateMaterialSupplyResponse = {
      message: "Material supply created successfully.",
      supply: materialSupply,
    };
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetSupplierProfile.mockResolvedValue(supplierProfile);
    mockedCreateMaterialSupply.mockResolvedValue(createdResponse);
    const user = userEvent.setup();
    renderApp(newMaterialSupplyPagePath);

    expect(await screen.findByRole("heading", { name: "Add material supply" }))
      .toBeInTheDocument();
    expect(screen.getByText(supplierProfile.businessName)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Material code"), "  FAB-COT-001  ");
    await user.type(screen.getByLabelText("Material name"), "  Cotton   twill fabric  ");
    await user.type(
      screen.getByLabelText("Material description (optional)"),
      "  Durable  240 GSM cotton twill  ",
    );
    await user.type(screen.getByLabelText("Quantity"), "1250.750");
    await user.type(screen.getByLabelText("Unit of measure"), " metre ");
    await user.type(screen.getByLabelText("Unit price"), "845.50");
    await user.type(screen.getByLabelText("Delivery lead time (days)"), "7");
    await user.type(
      screen.getByLabelText("Delivery notes (optional)"),
      " Deliver to the main receiving bay ",
    );
    await user.click(screen.getByRole("button", { name: "Save material supply" }));

    expect(mockedCreateMaterialSupply).toHaveBeenCalledWith({
      materialCode: "FAB-COT-001",
      materialName: "Cotton twill fabric",
      materialDescription: "Durable 240 GSM cotton twill",
      quantity: "1250.750",
      unitOfMeasure: "metre",
      unitPrice: "845.50",
      deliveryLeadTimeDays: "7",
      deliveryNotes: "Deliver to the main receiving bay",
    });
    expect(await screen.findByRole("heading", { name: materialSupply.materialName }))
      .toBeInTheDocument();
    expect(screen.getByText(/material supply created successfully/i))
      .toHaveTextContent(supplierProfile.businessName);
    expect(screen.getByText(String(materialSupply.id))).toBeInTheDocument();
  });

  it("blocks invalid material supply values before calling the API", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetSupplierProfile.mockResolvedValue(supplierProfile);
    const user = userEvent.setup();
    renderApp(newMaterialSupplyPagePath);

    await screen.findByRole("heading", { name: "Add material supply" });
    await user.type(screen.getByLabelText("Quantity"), "-1");
    await user.type(screen.getByLabelText("Unit price"), "0");
    await user.type(screen.getByLabelText("Delivery lead time (days)"), "1.5");
    await user.click(screen.getByRole("button", { name: "Save material supply" }));

    expect(screen.getByText("Material code is required.")).toBeInTheDocument();
    expect(screen.getByText("Material name is required.")).toBeInTheDocument();
    expect(screen.getByText("Unit of measure is required.")).toBeInTheDocument();
    expect(screen.getByText(/quantity must be positive/i)).toBeInTheDocument();
    expect(screen.getByText(/unit price must be positive/i)).toBeInTheDocument();
    expect(screen.getByText(/delivery lead time must be a non-negative whole number/i))
      .toBeInTheDocument();
    expect(mockedCreateMaterialSupply).not.toHaveBeenCalled();
  });

  it("requires the supplier profile before showing the material supply form", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    renderApp(newMaterialSupplyPagePath);

    expect(await screen.findByRole("heading", { name: "Supplier profile required" }))
      .toBeInTheDocument();
    expect(screen.getByText(/create your supplier profile before adding/i))
      .toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create supplier profile" }))
      .toHaveAttribute("href", supplierProfilePagePath);
    expect(mockedCreateMaterialSupply).not.toHaveBeenCalled();
  });

  it("protects material supply creation and hides its navigation from customers", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(newMaterialSupplyPagePath);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetSupplierProfile).not.toHaveBeenCalled();
    expect(mockedCreateMaterialSupply).not.toHaveBeenCalled();

    cleanup();
    renderApp();
    const navigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(navigation).queryByRole("link", { name: "Add material supply" }))
      .not.toBeInTheDocument();
  });

  it("shows only the supplier-scoped supply response and applies combined filters", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetMaterialSupplies
      .mockResolvedValueOnce([listedMaterialSupply])
      .mockResolvedValueOnce([listedMaterialSupply]);
    const user = userEvent.setup();
    renderApp(materialSupplyListPagePath);

    expect(await screen.findByRole("heading", { name: "My material supplies" }))
      .toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: listedMaterialSupply.materialName }))
      .toBeInTheDocument();
    expect(screen.queryByText(/supplier id/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add material supply" }))
      .toHaveAttribute("href", newMaterialSupplyPagePath);
    expect(screen.getByRole("link", { name: "Edit supply details" }))
      .toHaveAttribute("href", getEditMaterialSupplyPagePath(listedMaterialSupply.id));

    await user.type(screen.getByLabelText("Search supply records"), " cotton ");
    await user.selectOptions(screen.getByLabelText("Status"), "ACTIVE");
    await user.click(screen.getByRole("button", { name: "Search supplies" }));

    await waitFor(() => expect(mockedGetMaterialSupplies).toHaveBeenLastCalledWith(
      { search: "cotton", status: "ACTIVE" },
      expect.any(AbortSignal),
    ));

    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    await waitFor(() => expect(mockedGetMaterialSupplies).toHaveBeenLastCalledWith(
      {},
      expect.any(AbortSignal),
    ));
    expect(screen.getByLabelText("Search supply records")).toHaveValue("");
    expect(screen.getByLabelText("Status")).toHaveValue("");
  });

  it("shows supplier identity to authorized Inventory Managers and Administrators", async () => {
    for (const role of ["INVENTORY_MANAGER", "ADMINISTRATOR"] as const) {
      mockedGetCurrentUser.mockResolvedValue({ ...customer, role });
      mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
      renderApp(materialSupplyListPagePath);

      expect(await screen.findByRole("heading", { name: "Material supplies" }))
        .toBeInTheDocument();
      expect(await screen.findByText(new RegExp(listedMaterialSupply.supplierBusinessName)))
        .toHaveTextContent(`Supplier ID ${listedMaterialSupply.supplierId}`);
      expect(screen.queryByRole("link", { name: "Add material supply" }))
        .not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Edit supply details" }))
        .not.toBeInTheDocument();

      cleanup();
    }
  });

  it("shows clear no-results and load-error states for material supplies", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    const user = userEvent.setup();
    renderApp(materialSupplyListPagePath);

    expect(await screen.findByRole("heading", { name: "No material supplies" }))
      .toBeInTheDocument();
    await user.type(screen.getByLabelText("Search supply records"), "missing");
    await user.click(screen.getByRole("button", { name: "Search supplies" }));
    expect(await screen.findByRole("heading", { name: "No matching supplies" }))
      .toBeInTheDocument();

    cleanup();
    mockedGetMaterialSupplies.mockRejectedValue(new Error("Supply query unavailable."));
    renderApp(materialSupplyListPagePath);
    expect(await screen.findByRole("alert")).toHaveTextContent("Supply query unavailable.");
    expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument();
  });

  it("protects material supply records from unapproved roles and navigation", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(materialSupplyListPagePath);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetMaterialSupplies).not.toHaveBeenCalled();

    cleanup();
    renderApp();
    const navigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(navigation).queryByRole("link", { name: "Material supplies" }))
      .not.toBeInTheDocument();
    expect(within(navigation).queryByRole("link", { name: "My material supplies" }))
      .not.toBeInTheDocument();
  });

  it("preloads and updates only the approved material supply details", async () => {
    const updatedSupply: MaterialSupply = {
      ...materialSupply,
      quantity: "900.250",
      unitPrice: "910.75",
      deliveryLeadTimeDays: 5,
      deliveryNotes: "Revised receiving window",
      status: "INACTIVE",
      updatedAt: "2026-08-23T08:30:00Z",
    };
    const updateResponse: UpdateMaterialSupplyResponse = {
      message: "Material supply updated successfully.",
      supply: updatedSupply,
    };
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetMaterialSupply.mockResolvedValue(materialSupply);
    mockedUpdateMaterialSupply.mockResolvedValue(updateResponse);
    const user = userEvent.setup();
    renderApp(getEditMaterialSupplyPagePath(materialSupply.id));

    expect(await screen.findByRole("heading", { name: "Edit material supply" }))
      .toBeInTheDocument();
    expect(screen.getByText(materialSupply.materialCode)).toBeInTheDocument();
    expect(screen.getByText(materialSupply.unitOfMeasure)).toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toHaveValue(materialSupply.quantity);
    expect(screen.getByLabelText("Unit price")).toHaveValue(materialSupply.unitPrice);
    expect(screen.getByLabelText("Delivery lead time (days)"))
      .toHaveValue(String(materialSupply.deliveryLeadTimeDays));
    expect(screen.getByLabelText("Status")).toHaveValue("ACTIVE");

    await user.clear(screen.getByLabelText("Quantity"));
    await user.type(screen.getByLabelText("Quantity"), "900.250");
    await user.clear(screen.getByLabelText("Unit price"));
    await user.type(screen.getByLabelText("Unit price"), "910.75");
    await user.clear(screen.getByLabelText("Delivery lead time (days)"));
    await user.type(screen.getByLabelText("Delivery lead time (days)"), "5");
    await user.clear(screen.getByLabelText("Delivery notes (optional)"));
    await user.type(
      screen.getByLabelText("Delivery notes (optional)"),
      " Revised   receiving window ",
    );
    await user.selectOptions(screen.getByLabelText("Status"), "INACTIVE");
    await user.click(screen.getByRole("button", { name: "Update supply details" }));

    expect(mockedUpdateMaterialSupply).toHaveBeenCalledWith(materialSupply.id, {
      quantity: "900.250",
      unitPrice: "910.75",
      deliveryLeadTimeDays: "5",
      deliveryNotes: "Revised receiving window",
      status: "INACTIVE",
    });
    expect(await screen.findByText(/material supply updated successfully/i))
      .toBeInTheDocument();
    expect(screen.getByLabelText("Quantity")).toHaveValue(updatedSupply.quantity);
    expect(screen.getByLabelText("Status")).toHaveValue("INACTIVE");
    expect(screen.getByRole("link", { name: "Back to my supplies" }))
      .toHaveAttribute("href", materialSupplyListPagePath);
  });

  it("blocks invalid material supply numeric updates before the API call", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetMaterialSupply.mockResolvedValue(materialSupply);
    const user = userEvent.setup();
    renderApp(getEditMaterialSupplyPagePath(materialSupply.id));

    await screen.findByRole("heading", { name: "Edit material supply" });
    await user.clear(screen.getByLabelText("Quantity"));
    await user.type(screen.getByLabelText("Quantity"), "-1");
    await user.clear(screen.getByLabelText("Unit price"));
    await user.type(screen.getByLabelText("Unit price"), "0");
    await user.clear(screen.getByLabelText("Delivery lead time (days)"));
    await user.type(screen.getByLabelText("Delivery lead time (days)"), "1.5");
    await user.click(screen.getByRole("button", { name: "Update supply details" }));

    expect(screen.getByText(/quantity must be positive/i)).toBeInTheDocument();
    expect(screen.getByText(/unit price must be positive/i)).toBeInTheDocument();
    expect(screen.getByText(/delivery lead time must be a non-negative whole number/i))
      .toBeInTheDocument();
    expect(mockedUpdateMaterialSupply).not.toHaveBeenCalled();
  });

  it("handles missing supply IDs safely and protects the edit route", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    renderApp("/supplier/supplies/not-a-number/edit");

    expect(await screen.findByRole("heading", { name: "Material supply not found" }))
      .toBeInTheDocument();
    expect(mockedGetMaterialSupply).not.toHaveBeenCalled();

    cleanup();
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "ADMINISTRATOR" });
    renderApp(getEditMaterialSupplyPagePath(materialSupply.id));
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetMaterialSupply).not.toHaveBeenCalled();
  });

  it("shows a safe not-found state when an owned supply cannot be loaded", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetMaterialSupply.mockRejectedValue(Object.assign(
      new Error("The requested material supply was not found."),
      { code: "MATERIAL_SUPPLY_NOT_FOUND", status: 404 },
    ));
    renderApp(getEditMaterialSupplyPagePath(materialSupply.id));

    expect(await screen.findByRole("heading", { name: "Material supply not found" }))
      .toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to my supplies" }))
      .toHaveAttribute("href", materialSupplyListPagePath);
  });

  it("requires confirmation before deleting a material supply and removes stale archive actions", async () => {
    const deleteResponse: DeleteMaterialSupplyResponse = {
      message: "Material supply deleted successfully.",
      supplyId: materialSupply.id,
    };
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetMaterialSupply.mockResolvedValue(materialSupply);
    mockedDeleteMaterialSupply.mockResolvedValue(deleteResponse);
    mockedGetMaterialSupplies.mockResolvedValue([]);
    const user = userEvent.setup();
    renderApp(getEditMaterialSupplyPagePath(materialSupply.id));

    await screen.findByRole("heading", { name: "Edit material supply" });
    expect(screen.queryByRole("button", { name: /Archive supply/i })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete material supply" }));
    expect(screen.getByRole("alertdialog", { name: "Delete material supply?" }))
      .toHaveTextContent("Are you sure you want to delete this material supply?");
    expect(mockedDeleteMaterialSupply).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Delete material supply" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(mockedDeleteMaterialSupply).toHaveBeenCalledWith(materialSupply.id);
    expect(await screen.findByRole("heading", { name: "My material supplies" }))
      .toBeInTheDocument();
  });

  it("shows the backend conflict message when a material supply is already in use", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "SUPPLIER" });
    mockedGetMaterialSupply.mockResolvedValue(materialSupply);
    mockedDeleteMaterialSupply.mockRejectedValue(Object.assign(
      new Error("This material supply cannot be deleted because it is already used by inventory records."),
      { code: "SUPPLY_IN_USE", status: 409 },
    ));
    const user = userEvent.setup();
    renderApp(getEditMaterialSupplyPagePath(materialSupply.id));

    await screen.findByRole("heading", { name: "Edit material supply" });
    await user.click(screen.getByRole("button", { name: "Delete material supply" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This material supply cannot be deleted because it is already used by inventory records.",
    );
    expect(screen.getByRole("alertdialog", { name: "Delete material supply?" }))
      .toBeInTheDocument();
  });

  it("lets an Inventory Manager link an authorized supplier supply while creating material", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedCreateInventoryMaterial.mockResolvedValue(createdInventoryMaterialResponse);
    const user = userEvent.setup();
    renderApp(newInventoryMaterialPagePath);

    expect(await screen.findByRole("heading", { name: "Add inventory material" }))
      .toBeInTheDocument();

    await user.type(screen.getByLabelText("Material code"), "  INV-FAB-001  ");
    await user.type(screen.getByLabelText("Material name"), "  Cotton   twill fabric  ");
    await user.selectOptions(screen.getByLabelText("Material type"), "FABRIC");
    await user.type(screen.getByLabelText("Unit of measure"), " metre ");
    await user.type(screen.getByLabelText("Opening quantity"), "840.250");
    await user.type(screen.getByLabelText("Low-stock threshold"), "100.000");
    const supplierSource = screen.getByLabelText("Supplier source (optional)");
    await waitFor(() => expect(supplierSource).toBeEnabled());
    await user.selectOptions(supplierSource, String(listedMaterialSupply.id));
    await user.type(
      screen.getByLabelText("Material description (optional)"),
      " Main store 240 GSM cotton twill ",
    );
    await user.click(screen.getByRole("button", { name: "Save inventory material" }));

    expect(mockedGetMaterialSupplies).toHaveBeenCalledWith(
      { status: "ACTIVE" },
      expect.any(AbortSignal),
    );
    expect(mockedCreateInventoryMaterial).toHaveBeenCalledWith({
      sourceMaterialSupplyId: String(listedMaterialSupply.id),
      materialCode: "INV-FAB-001",
      materialName: "Cotton twill fabric",
      materialDescription: "Main store 240 GSM cotton twill",
      materialType: "FABRIC",
      unitOfMeasure: "metre",
      currentQuantity: "840.250",
      lowStockThreshold: "100.000",
    });
    expect(await screen.findByText("Inventory material saved")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName }))
      .toBeInTheDocument();
    expect(screen.getByText(new RegExp(listedMaterialSupply.supplierBusinessName)))
      .toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View inventory list" }))
      .toHaveAttribute("href", inventoryMaterialListPagePath);
  });

  it("rejects invalid inventory quantities before calling the API", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    const user = userEvent.setup();
    renderApp(newInventoryMaterialPagePath);

    await screen.findByRole("heading", { name: "Add inventory material" });
    await user.type(screen.getByLabelText("Opening quantity"), "0");
    await user.type(screen.getByLabelText("Low-stock threshold"), "-1");
    await user.click(screen.getByRole("button", { name: "Save inventory material" }));

    expect(screen.getByText("Material code is required.")).toBeInTheDocument();
    expect(screen.getByText("Material name is required.")).toBeInTheDocument();
    expect(screen.getByText("Unit of measure is required.")).toBeInTheDocument();
    expect(screen.getByText(/Opening quantity must be positive/i)).toBeInTheDocument();
    expect(screen.getByText(/Low-stock threshold must be zero or greater/i))
      .toBeInTheDocument();
    expect(mockedCreateInventoryMaterial).not.toHaveBeenCalled();
  });

  it("shows linked supplier source information in the inventory list", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    renderApp(inventoryMaterialListPagePath);

    expect(await screen.findByRole("heading", { name: "Inventory materials" }))
      .toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName }))
      .toBeInTheDocument();
    expect(screen.getByText("840.250 metre")).toBeInTheDocument();
    expect(screen.getByText(listedMaterialSupply.supplierBusinessName)).toBeInTheDocument();
    expect(screen.getByText(
      `${listedMaterialSupply.materialCode} · Supply ID ${listedMaterialSupply.id}`,
    )).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add inventory material" }))
      .toHaveAttribute("href", newInventoryMaterialPagePath);
    expect(mockedGetInventoryMaterials).toHaveBeenCalledWith(
      {},
      expect.any(AbortSignal),
    );
    expect(mockedGetMaterialSupplies).toHaveBeenCalledWith({}, expect.any(AbortSignal));
  });

  it("lets an Inventory Manager search and filter inventory records", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials
      .mockResolvedValueOnce([linkedInventoryMaterial])
      .mockResolvedValueOnce([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.type(screen.getByLabelText("Search inventory"), " cotton ");
    await user.selectOptions(screen.getByLabelText("Status"), "ACTIVE");
    await user.selectOptions(screen.getByLabelText("Material type"), "FABRIC");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    await waitFor(() => {
      expect(mockedGetInventoryMaterials).toHaveBeenLastCalledWith(
        { search: "cotton", status: "ACTIVE", materialType: "FABRIC" },
        expect.any(AbortSignal),
      );
    });
    expect(screen.getByText("840.250 metre")).toBeInTheDocument();
    expect(screen.getByText(/1 material found for the current filters/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Clear" })).toBeInTheDocument();
  });

  it("shows a filtered empty state and can clear the inventory filters", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials
      .mockResolvedValueOnce([linkedInventoryMaterial])
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.type(screen.getByLabelText("Search inventory"), "not-present");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    expect(await screen.findByRole("heading", { name: "No matching inventory materials" }))
      .toBeInTheDocument();
    expect(screen.getByText(/No inventory records match/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));

    expect(await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName }))
      .toBeInTheDocument();
    expect(mockedGetInventoryMaterials).toHaveBeenLastCalledWith(
      {},
      expect.any(AbortSignal),
    );
  });

  it("shows centrally calculated low-stock alerts and exact threshold quantities", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial, lowStockInventoryMaterial]);
    mockedGetLowStockInventoryMaterials.mockResolvedValue({
      count: 1,
      materials: [lowStockInventoryMaterial],
    });
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    renderApp(inventoryMaterialListPagePath);

    expect(await screen.findByRole("heading", { name: "Low-stock alert state" }))
      .toBeInTheDocument();
    expect(screen.getByText("1 low-stock material")).toBeInTheDocument();
    expect(screen.getAllByText(lowStockInventoryMaterial.materialName)).toHaveLength(2);
    expect(screen.getByText("100.000 / 100.000 metre")).toBeInTheDocument();
    expect(screen.getByText("Low stock")).toBeInTheDocument();
    expect(mockedGetLowStockInventoryMaterials).toHaveBeenCalledWith(expect.any(AbortSignal));
  });

  it("shows a healthy monitoring state when no material is low stock", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetLowStockInventoryMaterials.mockResolvedValue({ count: 0, materials: [] });
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    renderApp(inventoryMaterialListPagePath);

    expect(await screen.findByText(/No low-stock alerts/i)).toBeInTheDocument();
    expect(screen.getByText("0 low-stock materials")).toBeInTheDocument();
    expect(screen.getByText("Sufficient stock")).toBeInTheDocument();
  });

  it("lets an Inventory Manager receive supplier stock and refreshes the displayed quantity", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedReceiveInventoryMaterial.mockResolvedValue(receivedInventoryMaterialResponse);
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.type(
      screen.getByLabelText(`Receive quantity for ${linkedInventoryMaterial.materialName}`),
      "130.000",
    );
    await user.click(screen.getByRole("button", {
      name: `Receive stock for ${linkedInventoryMaterial.materialName}`,
    }));

    expect(mockedReceiveInventoryMaterial).toHaveBeenCalledWith(
      linkedInventoryMaterial.id,
      "130.000",
    );
    expect(await screen.findByText("970.250 metre")).toBeInTheDocument();
    expect(screen.getByText("Supplier material received into inventory successfully."))
      .toBeInTheDocument();
  });

  it("lets an Inventory Manager release stock to production and refreshes the displayed quantity", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedConsumeInventoryMaterial.mockResolvedValue(consumedInventoryMaterialResponse);
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.type(
      screen.getByLabelText(`Release quantity for ${linkedInventoryMaterial.materialName}`),
      "40.000",
    );
    await user.click(screen.getByRole("button", {
      name: `Release stock to production for ${linkedInventoryMaterial.materialName}`,
    }));

    expect(mockedConsumeInventoryMaterial).toHaveBeenCalledWith(
      linkedInventoryMaterial.id,
      "40.000",
    );
    expect(await screen.findByText("800.250 metre")).toBeInTheDocument();
    expect(screen.getByText("Inventory stock consumed successfully.")).toBeInTheDocument();
  });

  it("moves a consumed material into the low-stock monitor using the backend state", async () => {
    const nearThreshold = {
      ...linkedInventoryMaterial,
      currentQuantity: "105.000",
      lowStockThreshold: "100.000",
      stockState: "SUFFICIENT" as const,
    };
    const atThreshold = {
      ...nearThreshold,
      currentQuantity: "100.000",
      stockState: "LOW_STOCK" as const,
      updatedAt: "2026-08-23T09:10:00Z",
    };
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([nearThreshold]);
    mockedGetLowStockInventoryMaterials.mockResolvedValue({ count: 0, materials: [] });
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedConsumeInventoryMaterial.mockResolvedValue({
      message: "Inventory stock consumed successfully.",
      material: atThreshold,
    });
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: nearThreshold.materialName });
    await user.type(
      screen.getByLabelText(`Release quantity for ${nearThreshold.materialName}`),
      "5.000",
    );
    await user.click(screen.getByRole("button", {
      name: `Release stock to production for ${nearThreshold.materialName}`,
    }));

    expect(await screen.findByText("1 low-stock material")).toBeInTheDocument();
    expect(screen.getByText("100.000 / 100.000 metre")).toBeInTheDocument();
  });

  it("keeps the previous quantity visible when stock usage is insufficient", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedConsumeInventoryMaterial.mockRejectedValue(Object.assign(
      new Error("Insufficient stock. Requested 900.000 but only 840.250 is available."),
      {
        code: "INSUFFICIENT_STOCK",
        fields: {
          quantity: "Insufficient stock. Requested 900.000 but only 840.250 is available.",
        },
        status: 409,
      },
    ));
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.type(
      screen.getByLabelText(`Release quantity for ${linkedInventoryMaterial.materialName}`),
      "900.000",
    );
    await user.click(screen.getByRole("button", {
      name: `Release stock to production for ${linkedInventoryMaterial.materialName}`,
    }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Insufficient stock");
    expect(screen.getByText("840.250 metre")).toBeInTheDocument();
  });

  it("rejects non-positive stock usage before calling the inventory API", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.type(
      screen.getByLabelText(`Release quantity for ${linkedInventoryMaterial.materialName}`),
      "0",
    );
    await user.click(screen.getByRole("button", {
      name: `Release stock to production for ${linkedInventoryMaterial.materialName}`,
    }));

    expect(screen.getByText(/Usage quantity must be greater than zero/i)).toBeInTheDocument();
    expect(mockedConsumeInventoryMaterial).not.toHaveBeenCalled();
  });

  it("edits inventory metadata without presenting current quantity as an editable field", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterial.mockResolvedValue(linkedInventoryMaterial);
    mockedUpdateInventoryMaterial.mockResolvedValue(updatedInventoryMaterialResponse);
    const user = userEvent.setup();
    renderApp(getEditInventoryMaterialPagePath(linkedInventoryMaterial.id));

    expect(await screen.findByRole("heading", { name: "Edit inventory material" }))
      .toBeInTheDocument();
    expect(screen.getByText("840.250 metre")).toBeInTheDocument();
    expect(screen.queryByLabelText("Current quantity")).not.toBeInTheDocument();
    expect(screen.getByText(/Current quantity is intentionally not editable/i))
      .toBeInTheDocument();

    const nameInput = screen.getByLabelText("Material name");
    await user.clear(nameInput);
    await user.type(nameInput, "Updated cotton twill");
    const thresholdInput = screen.getByLabelText("Low-stock threshold");
    await user.clear(thresholdInput);
    await user.type(thresholdInput, "125.000");
    await user.selectOptions(screen.getByLabelText("Status"), "INACTIVE");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(mockedUpdateInventoryMaterial).toHaveBeenCalledWith(
      linkedInventoryMaterial.id,
      expect.objectContaining({
        materialName: "Updated cotton twill",
        lowStockThreshold: "125.000",
        status: "INACTIVE",
      }),
    );
    expect(await screen.findByText("Inventory material updated successfully."))
      .toBeInTheDocument();
    expect(screen.getByText("840.250 metre")).toBeInTheDocument();
  });

  it("deletes an unused inventory material after confirmation and removes it from the list", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([{ ...linkedInventoryMaterial, currentQuantity: "0.000" }]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedDeleteInventoryMaterial.mockResolvedValue(deletedInventoryMaterialResponse);
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    expect(screen.queryByRole("button", { name: /archive|discontinue/i })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete material" }));
    const deleteDialog = screen.getByRole("alertdialog", {
      name: "Delete material?",
    });
    expect(deleteDialog).toHaveTextContent(`Are you sure you want to delete "${linkedInventoryMaterial.materialName}"?`);
    await user.click(within(deleteDialog).getByRole("button", {
      name: "Delete",
    }));

    expect(mockedDeleteInventoryMaterial).toHaveBeenCalledWith(linkedInventoryMaterial.id);
    expect(await screen.findByText("Inventory material deleted successfully."))
      .toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByRole("heading", { name: linkedInventoryMaterial.materialName }))
        .not.toBeInTheDocument();
    });
  });

  it("shows a useful conflict when an inventory material is already in use", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([{ ...linkedInventoryMaterial, currentQuantity: "0.000" }]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    mockedDeleteInventoryMaterial.mockRejectedValue(Object.assign(
      new Error("This material cannot be deleted because it is already used in existing records."),
      { code: "MATERIAL_IN_USE", status: 409 },
    ));
    const user = userEvent.setup();
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    await user.click(screen.getByRole("button", { name: "Delete material" }));
    await user.click(within(screen.getByRole("alertdialog", { name: "Delete material?" }))
      .getByRole("button", { name: "Delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This material cannot be deleted because it is already used in existing records.",
    );
    expect(screen.getByRole("heading", { name: linkedInventoryMaterial.materialName }))
      .toBeInTheDocument();
  });

  it("does not offer deletion while an inventory material still has stock", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetInventoryMaterials.mockResolvedValue([linkedInventoryMaterial]);
    mockedGetMaterialSupplies.mockResolvedValue([listedMaterialSupply]);
    renderApp(inventoryMaterialListPagePath);

    await screen.findByRole("heading", { name: linkedInventoryMaterial.materialName });
    expect(screen.getByRole("button", { name: "Delete material" })).toBeDisabled();
    expect(mockedDeleteInventoryMaterial).not.toHaveBeenCalled();
  });

  it("shows a retry state when the authorized supplier selector cannot load", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    mockedGetMaterialSupplies.mockRejectedValue(new Error("Supplier lookup unavailable."));
    renderApp(newInventoryMaterialPagePath);

    expect(await screen.findByRole("heading", { name: "Add inventory material" }))
      .toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("Supplier lookup unavailable.");
    expect(screen.getByRole("button", { name: "Retry supplier supplies" }))
      .toBeInTheDocument();
  });

  it("protects inventory material pages and navigation from other roles", async () => {
    mockedGetCurrentUser.mockResolvedValue(customer);
    renderApp(newInventoryMaterialPagePath);

    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedCreateInventoryMaterial).not.toHaveBeenCalled();

    cleanup();
    renderApp(getEditInventoryMaterialPagePath(linkedInventoryMaterial.id));
    expect(await screen.findByRole("heading", { name: "Access forbidden" }))
      .toBeInTheDocument();
    expect(mockedGetInventoryMaterial).not.toHaveBeenCalled();

    cleanup();
    renderApp();
    const customerNavigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(customerNavigation).queryByRole("link", {
      name: /add inventory material/i,
    })).not.toBeInTheDocument();

    cleanup();
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "INVENTORY_MANAGER" });
    renderApp();
    const inventoryNavigation = await screen.findByRole("navigation", {
      name: "Primary navigation",
    });
    expect(within(inventoryNavigation).getByRole("link", {
      name: /inventory materials/i,
    })).toHaveAttribute("href", inventoryMaterialListPagePath);
    expect(within(inventoryNavigation).getByRole("link", {
      name: /add inventory material/i,
    })).toHaveAttribute("href", newInventoryMaterialPagePath);
  });

  it("keeps the public mobile menu mounted through its exit transition", async () => {
    mockedGetCurrentUser.mockResolvedValue(null);
    const user = userEvent.setup();
    renderApp();

    const toggle = await screen.findByRole("button", { name: "Toggle navigation" });
    await user.click(toggle);
    expect(document.getElementById("public-mobile-navigation")).toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    const closingMenu = document.getElementById("public-mobile-navigation");
    expect(closingMenu).toBeInTheDocument();
    expect(closingMenu).toHaveAttribute("inert");
    await waitFor(() => expect(document.getElementById("public-mobile-navigation")).not.toBeInTheDocument());
  });

  it("shows the reusable not-found page for an unknown route", async () => {
    renderApp("/not-a-real-page");

    expect(await screen.findByRole("heading", { name: "Page not found" }))
      .toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Return to overview" })).toBeInTheDocument();
  });

  it("shows the reusable loading state while a protected session is restored", () => {
    mockedGetCurrentUser.mockReturnValue(new Promise(() => undefined));
    renderApp(dashboardPagePaths.ADMINISTRATOR);

    expect(screen.getByRole("status")).toHaveTextContent("Checking access…");
  });

  it("shows the reusable error state when server authorization cannot be confirmed", async () => {
    mockedGetCurrentUser.mockResolvedValue({ ...customer, role: "ADMINISTRATOR" });
    mockedGetRoleAccess.mockRejectedValue(new Error("Authorization service unavailable."));
    renderApp("/access/administrator");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Authorization service unavailable.",
    );
    expect(screen.getByRole("heading", { name: "Authorization check failed" }))
      .toBeInTheDocument();
  });

  it("exposes an accessible responsive navigation toggle", async () => {
    const user = userEvent.setup();
    renderApp();

    const toggle = await screen.findByRole("button", { name: "Toggle navigation" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await user.click(toggle);

    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(document.getElementById("primary-navigation")).toBeInTheDocument();
    await waitFor(() => expect(document.body.style.overflow).toBe("hidden"));

    await user.click(screen.getByRole("button", { name: "Close navigation" }));
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    const closingDrawer = document.getElementById("primary-navigation")?.parentElement;
    expect(closingDrawer).toHaveAttribute("inert");
    expect(document.body.style.overflow).toBe("hidden");
    await waitFor(() => expect(document.getElementById("primary-navigation")).not.toBeInTheDocument());
    await waitFor(() => expect(document.body.style.overflow).toBe(""));
  });
});

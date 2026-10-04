import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  changeAdminUserRole,
  changeAdminUserStatus,
  createInternalUser,
  getAdminUser,
  getAdminUsers,
  type AdminUser,
} from "../api/adminUsers";
import { useAuth } from "../auth/useAuth";
import { AdminUsersPage } from "./AdminUsersPage";

vi.mock("../api/adminUsers", async () => {
  const actual = await vi.importActual<typeof import("../api/adminUsers")>("../api/adminUsers");
  return {
    ...actual,
    changeAdminUserRole: vi.fn(),
    changeAdminUserStatus: vi.fn(),
    createInternalUser: vi.fn(),
    getAdminUser: vi.fn(),
    getAdminUsers: vi.fn(),
  };
});

vi.mock("../auth/useAuth", () => ({ useAuth: vi.fn() }));

const administrator: AdminUser = {
  id: 1,
  fullName: "System Administrator",
  email: "admin@example.com",
  role: "ADMINISTRATOR",
  active: true,
  createdAt: "2026-08-22T10:00:00Z",
  updatedAt: "2026-08-22T10:00:00Z",
};

const salesOfficer: AdminUser = {
  id: 2,
  fullName: "Sales Officer",
  email: "sales@example.com",
  role: "SALES_OFFICER",
  active: true,
  createdAt: "2026-08-23T10:00:00Z",
  updatedAt: "2026-08-23T10:00:00Z",
};

const mockedGetAdminUsers = vi.mocked(getAdminUsers);
const mockedGetAdminUser = vi.mocked(getAdminUser);
const mockedCreateInternalUser = vi.mocked(createInternalUser);
const mockedChangeRole = vi.mocked(changeAdminUserRole);
const mockedChangeStatus = vi.mocked(changeAdminUserStatus);
const mockedUseAuth = vi.mocked(useAuth);

describe("AdminUsersPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedUseAuth.mockReturnValue({
      user: {
        id: administrator.id,
        fullName: administrator.fullName,
        email: administrator.email,
        role: administrator.role,
      },
      isLoading: false,
      sessionError: null,
      login: vi.fn(),
      refreshUser: vi.fn(),
      logout: vi.fn(),
    });
    mockedGetAdminUsers.mockResolvedValue([administrator, salesOfficer]);
    mockedGetAdminUser.mockResolvedValue(salesOfficer);
  });

  it("loads user accounts, opens detail, and applies a role change", async () => {
    const user = userEvent.setup();
    mockedChangeRole.mockResolvedValue({
      message: "User role updated successfully.",
      user: { ...salesOfficer, role: "PRODUCTION_MANAGER" },
    });

    render(<AdminUsersPage />);

    expect(await screen.findByText("Sales Officer")).toBeInTheDocument();
    const row = screen.getByText("sales@example.com").closest("tr");
    expect(row).not.toBeNull();
    await user.click(within(row as HTMLTableRowElement).getByRole("button", { name: "Manage" }));

    const roleSelect = await screen.findByRole("combobox", { name: "Managed user role" });
    expect(roleSelect).toHaveValue("SALES_OFFICER");
    await user.selectOptions(roleSelect, "PRODUCTION_MANAGER");

    await waitFor(() => expect(mockedChangeRole).toHaveBeenCalledWith(2, "PRODUCTION_MANAGER"));
    expect(await screen.findByText("User role updated successfully.")).toBeInTheDocument();
  });

  it("creates only internal accounts through the administrator form", async () => {
    const user = userEvent.setup();
    const created = {
      ...salesOfficer,
      id: 3,
      fullName: "Inventory Manager",
      email: "inventory@example.com",
      role: "INVENTORY_MANAGER" as const,
    };
    mockedCreateInternalUser.mockResolvedValue({
      message: "Internal user account created successfully.",
      user: created,
    });

    render(<AdminUsersPage />);
    await screen.findByText("System users");

    const formHeading = screen.getByRole("heading", { name: "Create internal account" });
    const form = formHeading.closest("div") as HTMLDivElement;
    await user.type(within(form).getByLabelText("Full name"), "Inventory Manager");
    await user.type(within(form).getByLabelText("Email"), "inventory@example.com");
    await user.type(within(form).getByLabelText("Temporary password"), "StrongPass123!");
    await user.selectOptions(within(form).getByLabelText("Internal role"), "INVENTORY_MANAGER");
    await user.click(within(form).getByRole("button", { name: "Create internal account" }));

    await waitFor(() => expect(mockedCreateInternalUser).toHaveBeenCalledWith({
      fullName: "Inventory Manager",
      email: "inventory@example.com",
      password: "StrongPass123!",
      role: "INVENTORY_MANAGER",
    }));
    expect(within(form).queryByRole("option", { name: "Customer" })).not.toBeInTheDocument();
  });

  it("protects the current administrator controls in the UI", async () => {
    const user = userEvent.setup();
    mockedGetAdminUser.mockResolvedValue(administrator);

    render(<AdminUsersPage />);
    const row = (await screen.findByText("admin@example.com")).closest("tr");
    await user.click(within(row as HTMLTableRowElement).getByRole("button", { name: "Manage" }));

    expect(await screen.findByText(/your own Administrator role and active access are protected/i))
      .toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Managed user role" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Deactivate account" })).toBeDisabled();
    expect(mockedChangeStatus).not.toHaveBeenCalled();
  });
});

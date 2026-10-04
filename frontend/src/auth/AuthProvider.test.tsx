import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { getCurrentUser, logoutUser } from "../api/auth";
import { AuthProvider } from "./AuthProvider";
import { useAuth } from "./useAuth";

vi.mock("../api/auth", () => ({
  getCurrentUser: vi.fn(),
  loginUser: vi.fn(),
  logoutUser: vi.fn(),
}));

const mockedGetCurrentUser = vi.mocked(getCurrentUser);
const mockedLogoutUser = vi.mocked(logoutUser);

const customer = {
  id: 42,
  fullName: "Registered Customer",
  email: "customer@example.com",
  role: "CUSTOMER" as const,
};

function AuthStateProbe() {
  const { user, logout } = useAuth();
  return (
    <div>
      <span>{user ? user.email : "SIGNED_OUT"}</span>
      <button type="button" onClick={() => void logout().catch(() => undefined)}>
        Sign out
      </button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetCurrentUser.mockResolvedValue(customer);
    mockedLogoutUser.mockResolvedValue(undefined);
  });

  it("clears frontend authentication state after logout", async () => {
    const user = userEvent.setup();
    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    expect(await screen.findByText("customer@example.com")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByText("SIGNED_OUT")).toBeInTheDocument();
    expect(mockedLogoutUser).toHaveBeenCalledTimes(1);
  });

  it("still clears frontend authentication state if the logout request fails", async () => {
    const user = userEvent.setup();
    mockedLogoutUser.mockRejectedValue(new Error("network failure"));
    render(
      <AuthProvider>
        <AuthStateProbe />
      </AuthProvider>,
    );

    expect(await screen.findByText("customer@example.com")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByText("SIGNED_OUT")).toBeInTheDocument();
  });
});

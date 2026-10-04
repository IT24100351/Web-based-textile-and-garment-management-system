import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  changePassword,
  getProfile,
  getProfileApiError,
  updateProfile,
} from "../api/profile";
import { useAuth } from "../auth/useAuth";
import { ProfileSettingsPage } from "./ProfileSettingsPage";

vi.mock("../api/profile", () => ({
  changePassword: vi.fn(),
  getProfile: vi.fn(),
  getProfileApiError: vi.fn(),
  updateProfile: vi.fn(),
}));

vi.mock("../auth/useAuth", () => ({
  useAuth: vi.fn(),
}));

const mockedGetProfile = vi.mocked(getProfile);
const mockedChangePassword = vi.mocked(changePassword);
const mockedUpdateProfile = vi.mocked(updateProfile);
const mockedGetProfileApiError = vi.mocked(getProfileApiError);
const mockedUseAuth = vi.mocked(useAuth);
const refreshUser = vi.fn();
const logout = vi.fn();


function LoginRedirectProbe() {
  const location = useLocation();
  const message = typeof location.state === "object"
    && location.state !== null
    && "message" in location.state
    && typeof location.state.message === "string"
    ? location.state.message
    : "";
  return <div>{message}</div>;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/profile"]}>
      <Routes>
        <Route path="/profile" element={<ProfileSettingsPage />} />
        <Route path="/login" element={<LoginRedirectProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

const profile = {
  id: 42,
  fullName: "Registered Customer",
  email: "customer@example.com",
  role: "CUSTOMER" as const,
};

describe("ProfileSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedUseAuth.mockReturnValue({
      user: profile,
      isLoading: false,
      sessionError: null,
      login: vi.fn(),
      refreshUser,
      logout,
    });
    mockedGetProfile.mockResolvedValue(profile);
    mockedGetProfileApiError.mockReturnValue({
      message: "Profile request failed.",
      fields: {},
    });
    refreshUser.mockResolvedValue(profile);
    logout.mockResolvedValue(undefined);
  });

  it("loads protected identity values and saves only the editable full name", async () => {
    const user = userEvent.setup();
    const updated = { ...profile, fullName: "Updated Customer" };
    mockedUpdateProfile.mockResolvedValue(updated);

    renderPage();

    expect(await screen.findByRole("heading", { name: "Profile settings" })).toBeInTheDocument();
    expect(screen.getByText("42")).toBeInTheDocument();
    expect(screen.getByText("customer@example.com")).toBeInTheDocument();
    expect(screen.getByText("Customer")).toBeInTheDocument();
    expect(screen.queryByLabelText(/email/i)).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: /role/i })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Change password" })).toBeInTheDocument();

    const fullName = screen.getByLabelText("Full name");
    await user.clear(fullName);
    await user.type(fullName, "  Updated    Customer  ");
    await user.click(screen.getByRole("button", { name: "Save profile settings" }));

    expect(mockedUpdateProfile).toHaveBeenCalledWith({ fullName: "Updated Customer" });
    expect(await screen.findByText("Profile settings updated successfully.")).toBeInTheDocument();
    expect(refreshUser).toHaveBeenCalledTimes(1);
  });

  it("validates an empty full name before calling the API", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "Profile settings" });

    await user.clear(screen.getByLabelText("Full name"));
    await user.click(screen.getByRole("button", { name: "Save profile settings" }));

    expect(screen.getByText("Full name is required.")).toBeInTheDocument();
    expect(mockedUpdateProfile).not.toHaveBeenCalled();
  });

  it("shows a safe API error and keeps the stored identity values visible", async () => {
    const user = userEvent.setup();
    mockedUpdateProfile.mockRejectedValue(new Error("failure"));
    mockedGetProfileApiError.mockReturnValue({
      code: "INTERNAL_ERROR",
      message: "The request could not be completed.",
      fields: {},
    });

    renderPage();
    await screen.findByRole("heading", { name: "Profile settings" });
    await user.click(screen.getByRole("button", { name: "Save profile settings" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The request could not be completed.");
    expect(screen.getByText("customer@example.com")).toBeInTheDocument();
    expect(screen.getByText("Customer")).toBeInTheDocument();
  });

  it("changes the signed-in user's password, clears auth state, and redirects to login", async () => {
    const user = userEvent.setup();
    mockedChangePassword.mockResolvedValue("Password changed successfully. Please sign in again.");

    renderPage();
    await screen.findByRole("heading", { name: "Profile settings" });

    await user.type(screen.getByLabelText("Current password"), "secure-pass-123");
    await user.type(screen.getByLabelText("New password"), "new-secure-pass-456");
    await user.type(screen.getByLabelText("Confirm new password"), "new-secure-pass-456");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(mockedChangePassword).toHaveBeenCalledWith({
      currentPassword: "secure-pass-123",
      newPassword: "new-secure-pass-456",
    });
    expect(logout).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("Password changed successfully. Please sign in again.")).toBeInTheDocument();
  });

  it("rejects a mismatched confirmation before calling the password API", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "Profile settings" });

    await user.type(screen.getByLabelText("Current password"), "secure-pass-123");
    await user.type(screen.getByLabelText("New password"), "new-secure-pass-456");
    await user.type(screen.getByLabelText("Confirm new password"), "different-pass-789");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(screen.getByText("Passwords do not match.")).toBeInTheDocument();
    expect(mockedChangePassword).not.toHaveBeenCalled();
  });

  it("shows a current-password error returned by the API", async () => {
    const user = userEvent.setup();
    mockedChangePassword.mockRejectedValue(new Error("failure"));
    mockedGetProfileApiError.mockReturnValue({
      code: "VALIDATION_ERROR",
      message: "Please correct the highlighted password fields.",
      fields: { currentPassword: "Current password is incorrect." },
    });

    renderPage();
    await screen.findByRole("heading", { name: "Profile settings" });
    await user.type(screen.getByLabelText("Current password"), "wrong-password");
    await user.type(screen.getByLabelText("New password"), "new-secure-pass-456");
    await user.type(screen.getByLabelText("Confirm new password"), "new-secure-pass-456");
    await user.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByText("Current password is incorrect.")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Please correct the highlighted password fields.",
    );
  });
});

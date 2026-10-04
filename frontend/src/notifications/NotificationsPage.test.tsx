import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  getNotificationApiError,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from "../api/notifications";
import { NotificationsPage } from "./NotificationsPage";

vi.mock("../api/notifications", () => ({
  getNotifications: vi.fn(),
  getNotificationApiError: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

const mockedGetNotifications = vi.mocked(getNotifications);
const mockedGetNotificationApiError = vi.mocked(getNotificationApiError);
const mockedMarkNotificationRead = vi.mocked(markNotificationRead);
const mockedMarkAllNotificationsRead = vi.mocked(markAllNotificationsRead);

const inbox = {
  unreadCount: 1,
  notifications: [
    {
      id: 81,
      kind: "LOW_STOCK" as const,
      title: "Low stock: FAB-81",
      message: "Cotton fabric has 4 metre remaining; the configured low-stock threshold is 5.",
      sourceModule: "INVENTORY" as const,
      sourceRecordId: 81,
      unread: true,
      readAt: null,
      createdAt: "2026-08-26T10:30:00Z",
    },
    {
      id: 82,
      kind: "DELIVERY_STATUS" as const,
      title: "Delivery Delivered",
      message: "Delivery DEL-82 for order ORD-82 is now delivered.",
      sourceModule: "DELIVERY" as const,
      sourceRecordId: 82,
      unread: false,
      readAt: "2026-08-26T11:00:00Z",
      createdAt: "2026-08-26T10:45:00Z",
    },
  ],
};

describe("NotificationsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetNotifications.mockResolvedValue(inbox);
    mockedGetNotificationApiError.mockReturnValue({
      message: "Notification request failed.",
      fields: {},
    });
    mockedMarkNotificationRead.mockResolvedValue({ message: "Notification marked as read." });
    mockedMarkAllNotificationsRead.mockResolvedValue({ message: "All notifications marked as read." });
  });

  it("shows recipient notifications with unread state and marks one read", async () => {
    const user = userEvent.setup();
    render(<NotificationsPage />);

    expect(await screen.findByRole("heading", { name: "Notifications" })).toBeInTheDocument();
    expect(screen.getByText("1 unread")).toBeInTheDocument();
    expect(screen.getByText("Low stock: FAB-81")).toBeInTheDocument();
    expect(screen.getByText("Delivery Delivered")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Mark read" }));

    expect(mockedMarkNotificationRead).toHaveBeenCalledWith(81);
    expect(await screen.findByText("Notification marked as read.")).toBeInTheDocument();
    expect(screen.getByText("0 unread")).toBeInTheDocument();
  });

  it("supports unread-only loading and a proper empty state", async () => {
    const user = userEvent.setup();
    mockedGetNotifications
      .mockResolvedValueOnce(inbox)
      .mockResolvedValueOnce({ notifications: [], unreadCount: 0 });

    render(<NotificationsPage />);
    await screen.findByRole("heading", { name: "Notifications" });
    await user.click(screen.getByLabelText("Show unread notifications only"));

    expect(await screen.findByRole("heading", { name: "All caught up" })).toBeInTheDocument();
    expect(mockedGetNotifications).toHaveBeenLastCalledWith(true, expect.any(AbortSignal));
  });

  it("animates unread-only notification removal after a successful update", async () => {
    const user = userEvent.setup();
    mockedGetNotifications
      .mockResolvedValueOnce(inbox)
      .mockResolvedValueOnce({ notifications: [inbox.notifications[0]], unreadCount: 1 });

    render(<NotificationsPage />);
    await screen.findByRole("heading", { name: "Notifications" });
    await user.click(screen.getByLabelText("Show unread notifications only"));
    expect(await screen.findByText("Low stock: FAB-81")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Mark read" }));
    expect(mockedMarkNotificationRead).toHaveBeenCalledWith(81);
    const removingNotification = screen.getByText("Low stock: FAB-81").closest("li");
    await waitFor(() => expect(removingNotification).toHaveAttribute("data-motion-state", "closed"));
    expect(removingNotification).toHaveAttribute("inert");
    await waitFor(() => expect(screen.queryByText("Low stock: FAB-81")).not.toBeInTheDocument());
    expect(await screen.findByRole("heading", { name: "All caught up" })).toBeInTheDocument();
  });

  it("marks every unread notification read", async () => {
    const user = userEvent.setup();
    render(<NotificationsPage />);
    await screen.findByRole("heading", { name: "Notifications" });

    await user.click(screen.getByRole("button", { name: "Mark all read" }));

    expect(mockedMarkAllNotificationsRead).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("All notifications marked as read.")).toBeInTheDocument();
    expect(screen.getByText("0 unread")).toBeInTheDocument();
  });

  it("shows a safe load failure", async () => {
    mockedGetNotifications.mockRejectedValue(new Error("database details must not leak"));
    mockedGetNotificationApiError.mockReturnValue({
      code: "INTERNAL_ERROR",
      message: "The request could not be completed.",
      fields: {},
    });

    render(<NotificationsPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("The request could not be completed.");
    expect(screen.queryByText(/database details/i)).not.toBeInTheDocument();
  });
});

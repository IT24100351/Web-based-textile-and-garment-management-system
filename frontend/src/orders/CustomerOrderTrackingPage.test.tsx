import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  getMyOrders,
  getMyOrderTracking,
  type CustomerOrderTracking,
  type OrderSummary,
} from "../api/orders";
import {
  getMyDeliveryTracking,
  type CustomerDeliveryTracking,
} from "../api/deliveries";
import {
  customerOrderTrackingPagePath,
  customerOrderTrackingRoutePath,
} from "../navigation/navigation";
import { CustomerOrderTrackingPage } from "./CustomerOrderTrackingPage";

vi.mock("../api/orders", () => ({
  getMyOrders: vi.fn(),
  getMyOrderTracking: vi.fn(),
  getOrderApiError: (error: unknown, fallback: string) => {
    const structured = error as { message?: string; status?: number };
    return {
      message: structured?.message ?? fallback,
      status: structured?.status,
      fields: {},
    };
  },
}));

vi.mock("../api/deliveries", () => ({
  getMyDeliveryTracking: vi.fn(),
  getDeliveryApiError: (error: unknown, fallback: string) => {
    const structured = error as { message?: string; status?: number };
    return {
      message: structured?.message ?? fallback,
      status: structured?.status,
      fields: {},
    };
  },
}));

const mockedGetMyOrderTracking = vi.mocked(getMyOrderTracking);
const mockedGetMyOrders = vi.mocked(getMyOrders);
const mockedGetMyDeliveryTracking = vi.mocked(getMyDeliveryTracking);

const orderSummary: OrderSummary = {
  id: 91,
  orderNumber: "ORD-TRACK-91",
  customerId: 51,
  customerName: "Asha Perera",
  customerEmail: "asha@example.com",
  status: "IN_PRODUCTION",
  createdAt: "2026-08-23T10:00:00Z",
  updatedAt: "2026-08-23T11:00:00Z",
  itemCount: 2,
  totalAmount: "7670.00",
};

const noDelivery: CustomerDeliveryTracking = {
  orderId: 91,
  hasDelivery: false,
  delivery: null,
};

const scheduledDelivery: CustomerDeliveryTracking = {
  orderId: 91,
  hasDelivery: true,
  delivery: {
    deliveryId: 301,
    deliveryNumber: "DLV-TRACK-301",
    scheduledAt: "2026-08-24T08:30:00Z",
    status: "OUT_FOR_DELIVERY",
    lastUpdatedAt: "2026-08-24T08:45:00Z",
  },
};

const tracking: CustomerOrderTracking = {
  orderId: 91,
  orderNumber: "ORD-TRACK-91",
  currentStatus: "IN_PRODUCTION",
  placedAt: "2026-08-23T10:00:00Z",
  lastUpdatedAt: "2026-08-23T11:00:00Z",
  itemCount: 2,
  totalAmount: "7670.00",
  orderHistory: [
    {
      id: 201,
      fromStatus: "PENDING",
      toStatus: "CONFIRMED",
      changedAt: "2026-08-23T10:30:00Z",
    },
    {
      id: 202,
      fromStatus: "CONFIRMED",
      toStatus: "IN_PRODUCTION",
      changedAt: "2026-08-23T11:00:00Z",
    },
  ],
};

function renderTracking(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={customerOrderTrackingPagePath} element={<CustomerOrderTrackingPage />} />
        <Route path={customerOrderTrackingRoutePath} element={<CustomerOrderTrackingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("TGMS-47/TGMS-65 customer order and delivery tracking", () => {
  beforeEach(() => {
    mockedGetMyOrders.mockResolvedValue([orderSummary]);
    mockedGetMyOrderTracking.mockResolvedValue(tracking);
    mockedGetMyDeliveryTracking.mockResolvedValue(noDelivery);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("lets a customer enter an order ID and shows the ownership-scoped progress", async () => {
    const user = userEvent.setup();
    renderTracking(customerOrderTrackingPagePath);

    expect(screen.getByRole("heading", { name: "Track an order" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("Order ID or order number"), "91");
    await user.click(screen.getByRole("button", { name: "Track order" }));

    await waitFor(() => expect(mockedGetMyOrderTracking).toHaveBeenCalledWith(
      91,
      expect.any(AbortSignal),
    ));
    expect(await screen.findByRole("heading", { name: "ORD-TRACK-91" })).toBeInTheDocument();
    expect(screen.getAllByText("In Production").length).toBeGreaterThan(0);
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("LKR 7670.00")).toBeInTheDocument();
    expect(screen.getByText("Pending → Confirmed")).toBeInTheDocument();
    expect(screen.getByText("Confirmed → In Production")).toBeInTheDocument();
    expect(screen.getByText("Delivery not scheduled yet")).toBeInTheDocument();
    expect(mockedGetMyDeliveryTracking).toHaveBeenCalledWith(91, expect.any(AbortSignal));
  });

  it("resolves the visible order number within the signed-in customer's own history", async () => {
    const user = userEvent.setup();
    renderTracking(customerOrderTrackingPagePath);

    await user.type(screen.getByLabelText("Order ID or order number"), "ord-track-91");
    await user.click(screen.getByRole("button", { name: "Track order" }));

    await waitFor(() => expect(mockedGetMyOrders).toHaveBeenCalled());
    await waitFor(() => expect(mockedGetMyOrderTracking).toHaveBeenCalledWith(
      91,
      expect.any(AbortSignal),
    ));
    expect(await screen.findByRole("heading", { name: "ORD-TRACK-91" })).toBeInTheDocument();
  });

  it("shows the customer's real Delivery Management schedule and progress", async () => {
    mockedGetMyDeliveryTracking.mockResolvedValue(scheduledDelivery);
    renderTracking("/orders/track/91");

    expect(await screen.findByRole("heading", { name: "Delivery progress" })).toBeInTheDocument();
    expect(screen.getByText("DLV-TRACK-301")).toBeInTheDocument();
    expect(screen.getAllByText("Out For Delivery").length).toBeGreaterThan(0);
    expect(screen.queryByText("Delivery not scheduled yet")).not.toBeInTheDocument();
  });

  it("uses the same safe result for an unknown or not-owned order", async () => {
    mockedGetMyOrderTracking.mockRejectedValue({
      status: 404,
      message: "Order was not found.",
    });
    renderTracking("/orders/track/4502");

    expect(await screen.findByRole("heading", { name: "Order tracking unavailable" }))
      .toBeInTheDocument();
    expect(screen.getByText(/No order with that ID or order number is available in your account/i))
      .toBeInTheDocument();
    expect(screen.queryByText(/another customer/i)).not.toBeInTheDocument();
  });

  it("shows a clear initial tracking state before any later transition", async () => {
    mockedGetMyOrderTracking.mockResolvedValue({
      ...tracking,
      currentStatus: "PENDING",
      orderHistory: [],
    });
    renderTracking("/orders/track/91");

    expect(await screen.findByText("No later status transition has been recorded yet."))
      .toBeInTheDocument();
    expect(screen.getAllByText("Pending").length).toBeGreaterThan(0);
  });

  it("rejects malformed order IDs locally without calling the tracking API", async () => {
    const user = userEvent.setup();
    renderTracking(customerOrderTrackingPagePath);

    await user.type(screen.getByLabelText("Order ID or order number"), "abc");
    await user.click(screen.getByRole("button", { name: "Track order" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Enter a positive order ID");
    expect(mockedGetMyOrders).not.toHaveBeenCalled();
    expect(mockedGetMyOrderTracking).not.toHaveBeenCalled();
    expect(mockedGetMyDeliveryTracking).not.toHaveBeenCalled();
  });
});

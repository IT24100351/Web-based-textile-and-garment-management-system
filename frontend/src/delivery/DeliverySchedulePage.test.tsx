import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  scheduleDelivery,
  updateDeliveryStatus,
  validateDeliveryOrder,
  type DeliveryEligibleOrder,
  type ScheduleDeliveryResponse,
} from "../api/deliveries";
import { DeliverySchedulePage } from "./DeliverySchedulePage";

vi.mock("../api/deliveries", () => ({
  validateDeliveryOrder: vi.fn(),
  scheduleDelivery: vi.fn(),
  updateDeliveryStatus: vi.fn(),
  getDeliveryApiError: (error: unknown, fallback: string) => {
    const value = error as { message?: string; fields?: Record<string, string> };
    return { message: value?.message ?? fallback, fields: value?.fields ?? {} };
  },
}));

const mockedValidateOrder = vi.mocked(validateDeliveryOrder);
const mockedScheduleDelivery = vi.mocked(scheduleDelivery);
const mockedUpdateDeliveryStatus = vi.mocked(updateDeliveryStatus);

const readyOrder: DeliveryEligibleOrder = {
  orderId: 9101,
  orderNumber: "ORD-DELIVERY-READY-ASHA",
  customerId: 9001,
  customerName: "Asha Perera",
  customerEmail: "asha.delivery@example.com",
  currentStatus: "READY_FOR_DELIVERY",
  itemCount: 1,
  totalAmount: "5000.00",
  readyForDelivery: true,
  items: [{
    orderItemId: 9201,
    productId: 9402,
    variantId: 9403,
    quantity: 2,
    selectedSize: "M",
    selectedColor: "Blue",
  }],
};

const savedResponse: ScheduleDeliveryResponse = {
  message: "Delivery scheduled successfully.",
  delivery: {
    id: 9501,
    deliveryNumber: "DLV-ABC123",
    orderId: 9101,
    scheduledAt: "2099-12-31T05:00:00Z",
    deliveryAddress: "10 Main Street, Colombo",
    deliveryNotes: "Call before arrival",
    status: "SCHEDULED",
    allowedStatusTransitions: ["OUT_FOR_DELIVERY"],
    createdAt: "2026-08-24T01:00:00Z",
    updatedAt: "2026-08-24T01:00:00Z",
  },
};

function renderPage(path = "/deliveries/schedule/9101") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/deliveries/schedule/:orderId" element={<DeliverySchedulePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("DeliverySchedulePage", () => {
  beforeEach(() => {
    mockedValidateOrder.mockResolvedValue(readyOrder);
    mockedScheduleDelivery.mockResolvedValue(savedResponse);
    mockedUpdateDeliveryStatus.mockImplementation(async (_deliveryId, status) => ({
      message: "Delivery status updated successfully.",
      delivery: {
        ...savedResponse.delivery,
        status,
        allowedStatusTransitions: status === "OUT_FOR_DELIVERY" ? ["DELIVERED"] : [],
        updatedAt: "2026-08-24T02:00:00Z",
      },
    }));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("revalidates the selected order and saves a valid delivery schedule", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "Schedule product delivery" })).toBeInTheDocument();
    await waitFor(() => expect(mockedValidateOrder).toHaveBeenCalledWith(9101, expect.any(AbortSignal)));
    expect(screen.getByRole("heading", { name: readyOrder.orderNumber })).toBeInTheDocument();
    expect(screen.getByText("Asha Perera · asha.delivery@example.com")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Delivery date"), "2099-12-31");
    await user.type(screen.getByLabelText("Delivery time"), "10:30");
    await user.type(screen.getByLabelText("Delivery address"), " 10 Main   Street, Colombo ");
    await user.type(screen.getByLabelText("Delivery notes (optional)"), " Call   before arrival ");
    await user.click(screen.getByRole("button", { name: "Save delivery schedule" }));

    const expectedScheduledAt = new Date("2099-12-31T10:30").toISOString();
    await waitFor(() => expect(mockedScheduleDelivery).toHaveBeenCalledWith({
      orderId: 9101,
      scheduledAt: expectedScheduledAt,
      deliveryAddress: "10 Main Street, Colombo",
      deliveryNotes: "Call before arrival",
    }));
    expect(await screen.findByRole("heading", { name: "DLV-ABC123" })).toBeInTheDocument();
    expect(screen.getByText("SCHEDULED")).toBeInTheDocument();
    expect(screen.getByText("10 Main Street, Colombo")).toBeInTheDocument();
  });

  it("blocks missing required schedule details before calling the API", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: readyOrder.orderNumber });

    await user.click(screen.getByRole("button", { name: "Save delivery schedule" }));
    expect(screen.getByText("Enter a valid delivery date and time.")).toBeInTheDocument();
    expect(screen.getByText("Enter the delivery address.")).toBeInTheDocument();
    expect(mockedScheduleDelivery).not.toHaveBeenCalled();
  });


  it("shows a schedule conflict and lets the Sales Officer save an alternative time", async () => {
    mockedScheduleDelivery
      .mockRejectedValueOnce({
        code: "DELIVERY_SCHEDULE_CONFLICT",
        message: "The requested delivery time is unavailable.",
        fields: {
          scheduledAt: "This time conflicts with active delivery DLV-CONFLICT-01. Choose a time at least 60 minutes before or after existing active deliveries.",
        },
      })
      .mockResolvedValueOnce({
        ...savedResponse,
        delivery: { ...savedResponse.delivery, scheduledAt: "2099-12-31T11:30:00Z" },
      });

    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: readyOrder.orderNumber });
    expect(screen.getByText(/at least 60 minutes before or after every existing active delivery/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Delivery date"), "2099-12-31");
    await user.type(screen.getByLabelText("Delivery time"), "11:15");
    await user.type(screen.getByLabelText("Delivery address"), "10 Main Street, Colombo");
    await user.click(screen.getByRole("button", { name: "Save delivery schedule" }));

    expect(await screen.findByText(/DLV-CONFLICT-01/)).toBeInTheDocument();
    expect(screen.queryByText("Delivery scheduled")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("Delivery time"));
    await user.type(screen.getByLabelText("Delivery time"), "11:30");
    expect(screen.queryByText(/DLV-CONFLICT-01/)).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save delivery schedule" }));

    await waitFor(() => expect(mockedScheduleDelivery).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole("heading", { name: "DLV-ABC123" })).toBeInTheDocument();
  });

  it("updates delivery progress through allowed states and confirms Order completion after delivery", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: readyOrder.orderNumber });

    await user.type(screen.getByLabelText("Delivery date"), "2099-12-31");
    await user.type(screen.getByLabelText("Delivery time"), "10:30");
    await user.type(screen.getByLabelText("Delivery address"), "10 Main Street, Colombo");
    await user.click(screen.getByRole("button", { name: "Save delivery schedule" }));

    expect(await screen.findByRole("heading", { name: "Update delivery progress" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Mark out for delivery" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Mark out for delivery" }));
    await waitFor(() => expect(mockedUpdateDeliveryStatus).toHaveBeenCalledWith(9501, "OUT_FOR_DELIVERY"));
    expect(await screen.findByText("OUT FOR DELIVERY")).toBeInTheDocument();
    expect(screen.getByText(/Order Management remains READY FOR DELIVERY/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Mark delivered" }));
    await waitFor(() => expect(mockedUpdateDeliveryStatus).toHaveBeenCalledWith(9501, "DELIVERED"));
    expect(await screen.findByText("DELIVERED")).toBeInTheDocument();
    expect(screen.getByText(/Order Management has been synchronized to COMPLETED/i)).toBeInTheDocument();
    expect(screen.getByText(/terminal status/i)).toBeInTheDocument();
  });

  it("shows server-side stale-order errors and does not falsely confirm save", async () => {
    mockedScheduleDelivery.mockRejectedValue({
      message: "The selected order is not currently ready for Delivery Management.",
      fields: { orderId: "Choose another delivery-ready order." },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: readyOrder.orderNumber });

    await user.type(screen.getByLabelText("Delivery date"), "2099-12-31");
    await user.type(screen.getByLabelText("Delivery time"), "10:30");
    await user.type(screen.getByLabelText("Delivery address"), "10 Main Street, Colombo");
    await user.click(screen.getByRole("button", { name: "Save delivery schedule" }));

    expect(await screen.findByText("Choose another delivery-ready order.")).toBeInTheDocument();
    expect(screen.queryByText("Delivery scheduled")).not.toBeInTheDocument();
  });

  it("handles an invalid order route safely", () => {
    renderPage("/deliveries/schedule/not-a-number");
    expect(screen.getByRole("heading", { name: "Invalid delivery order" })).toBeInTheDocument();
    expect(mockedValidateOrder).not.toHaveBeenCalled();
  });
});

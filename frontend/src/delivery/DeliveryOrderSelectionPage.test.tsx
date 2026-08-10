import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  getDeliveryEligibleOrders,
  validateDeliveryOrder,
  type DeliveryEligibleOrder,
} from "../api/deliveries";
import { DeliveryOrderSelectionPage } from "./DeliveryOrderSelectionPage";

vi.mock("../api/deliveries", () => ({
  getDeliveryEligibleOrders: vi.fn(),
  validateDeliveryOrder: vi.fn(),
  getDeliveryApiError: (error: unknown, fallback: string) => {
    const value = error as { message?: string; fields?: Record<string, string> };
    return { message: value?.message ?? fallback, fields: value?.fields ?? {} };
  },
}));

const mockedGetEligibleOrders = vi.mocked(getDeliveryEligibleOrders);
const mockedValidateOrder = vi.mocked(validateDeliveryOrder);

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

function renderPage() {
  render(<MemoryRouter><DeliveryOrderSelectionPage /></MemoryRouter>);
}

describe("DeliveryOrderSelectionPage", () => {
  beforeEach(() => {
    mockedGetEligibleOrders.mockResolvedValue([readyOrder]);
    mockedValidateOrder.mockResolvedValue(readyOrder);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows delivery-ready Order/customer details and revalidates the selected order", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "Select an order for delivery" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "ORD-DELIVERY-READY-ASHA" })).toBeInTheDocument();
    expect(screen.getByText("Asha Perera · asha.delivery@example.com")).toBeInTheDocument();
    expect(screen.getByText(/Product #9402 · Variant #9403/)).toBeInTheDocument();
    expect(screen.getByText(/M · Blue · Quantity 2/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Select order" }));
    await waitFor(() => expect(mockedValidateOrder).toHaveBeenCalledWith(9101));
    expect(await screen.findByText("Selection revalidated")).toBeInTheDocument();
    expect(screen.getByText(/has no active\/completed Delivery record/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Schedule delivery" })).toHaveAttribute(
      "href",
      "/deliveries/schedule/9101",
    );
  });

  it("searches by existing Order/customer fields", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "ORD-DELIVERY-READY-ASHA" });

    await user.type(screen.getByLabelText("Search delivery-ready orders"), "Asha");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => expect(mockedGetEligibleOrders).toHaveBeenLastCalledWith("Asha", undefined));
  });

  it("shows an empty state when no eligible orders remain", async () => {
    mockedGetEligibleOrders.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByRole("heading", { name: "No orders ready for delivery" })).toBeInTheDocument();
  });

  it("shows stale not-ready or duplicate selection errors returned by the server", async () => {
    const user = userEvent.setup();
    mockedValidateOrder.mockRejectedValue({
      message: "The selected order is not currently ready for Delivery Management.",
      fields: { orderId: "Choose an order whose Order Management handoff reports readyForDelivery=true." },
    });
    renderPage();
    await screen.findByRole("heading", { name: "ORD-DELIVERY-READY-ASHA" });

    await user.click(screen.getByRole("button", { name: "Select order" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/readyForDelivery=true/);
  });
});

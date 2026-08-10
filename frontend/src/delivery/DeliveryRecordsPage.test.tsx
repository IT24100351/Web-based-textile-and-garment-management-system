import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { getDeliveryRecords, type StaffDeliveryRecord } from "../api/deliveries";
import { DeliveryRecordsPage } from "./DeliveryRecordsPage";

vi.mock("../api/deliveries", () => ({
  getDeliveryRecords: vi.fn(),
  getDeliveryApiError: (error: unknown, fallback: string) => ({
    message: error instanceof Error ? error.message : fallback,
    fields: {},
  }),
}));

const mockedGetRecords = vi.mocked(getDeliveryRecords);

const record: StaffDeliveryRecord = {
  delivery: {
    id: 77,
    deliveryNumber: "DLV-RECORD-77",
    orderId: 44,
    scheduledAt: "2099-12-31T10:30:00Z",
    deliveryAddress: "25 Galle Road",
    deliveryNotes: null,
    status: "SCHEDULED",
    allowedStatusTransitions: ["OUT_FOR_DELIVERY", "CANCELLED"],
    createdAt: "2099-12-30T10:00:00Z",
    updatedAt: "2099-12-30T10:00:00Z",
  },
  orderNumber: "ORD-44",
  customerId: 9,
  customerName: "Asha Perera",
  customerEmail: "asha@example.com",
  orderStatus: "READY_FOR_DELIVERY",
  orderTotal: "5000.00",
  items: [{
    orderItemId: 1,
    productId: 10,
    variantId: 11,
    productName: "Oxford Shirt",
    quantity: 2,
    selectedSize: "M",
    selectedColor: "Blue",
    unitPriceSnapshot: "2500.00",
    lineTotal: "5000.00",
  }],
};

describe("DeliveryRecordsPage", () => {
  beforeEach(() => mockedGetRecords.mockReset());

  it("shows live delivery/order/customer records and supports search", async () => {
    mockedGetRecords.mockResolvedValue([record]);
    const user = userEvent.setup();

    render(<MemoryRouter><DeliveryRecordsPage /></MemoryRouter>);

    expect(await screen.findByText("DLV-RECORD-77")).toBeInTheDocument();
    expect(screen.getByText("ORD-44")).toBeInTheDocument();
    expect(screen.getByText("Asha Perera")).toBeInTheDocument();
    expect(screen.getByText("asha@example.com")).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Search delivery number/i), "DLV-77");
    await user.click(screen.getByRole("button", { name: "Search" }));

    await waitFor(() => expect(mockedGetRecords).toHaveBeenLastCalledWith("DLV-77", "", expect.any(AbortSignal)));
  });

  it("shows a clear empty state", async () => {
    mockedGetRecords.mockResolvedValue([]);
    render(<MemoryRouter><DeliveryRecordsPage /></MemoryRouter>);
    expect(await screen.findByText("No delivery records found")).toBeInTheDocument();
  });
});

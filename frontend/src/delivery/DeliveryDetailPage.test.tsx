import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  getDeliveryRecord,
  updateDeliveryStatus,
  type StaffDeliveryRecord,
} from "../api/deliveries";
import { DeliveryDetailPage } from "./DeliveryDetailPage";

vi.mock("../api/deliveries", () => ({
  getDeliveryRecord: vi.fn(),
  updateDeliveryStatus: vi.fn(),
  getDeliveryApiError: (error: unknown, fallback: string) => ({
    message: error instanceof Error ? error.message : fallback,
    fields: {},
  }),
}));

const mockedGetRecord = vi.mocked(getDeliveryRecord);
const mockedUpdateStatus = vi.mocked(updateDeliveryStatus);

const scheduled: StaffDeliveryRecord = {
  delivery: {
    id: 77,
    deliveryNumber: "DLV-RECORD-77",
    orderId: 44,
    scheduledAt: "2099-12-31T10:30:00Z",
    deliveryAddress: "Wrong address",
    deliveryNotes: "Incorrect record",
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

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/deliveries/77"]}>
      <Routes><Route path="/deliveries/:deliveryId" element={<DeliveryDetailPage />} /></Routes>
    </MemoryRouter>,
  );
}

describe("DeliveryDetailPage", () => {
  beforeEach(() => {
    mockedGetRecord.mockReset();
    mockedUpdateStatus.mockReset();
  });

  it("shows complete record details and safely cancels an incorrect scheduled delivery", async () => {
    const cancelledRecord: StaffDeliveryRecord = {
      ...scheduled,
      delivery: {
        ...scheduled.delivery,
        status: "CANCELLED",
        allowedStatusTransitions: [],
        updatedAt: "2099-12-30T11:00:00Z",
      },
    };
    mockedGetRecord.mockResolvedValueOnce(scheduled).mockResolvedValueOnce(cancelledRecord);
    mockedUpdateStatus.mockResolvedValue({
      message: "Delivery status updated successfully.",
      delivery: {
        ...scheduled.delivery,
        status: "CANCELLED",
        allowedStatusTransitions: [],
        updatedAt: "2099-12-30T11:00:00Z",
      },
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("DLV-RECORD-77")).toBeInTheDocument();
    expect(screen.getByText("Oxford Shirt")).toBeInTheDocument();
    expect(screen.getByText(/Asha Perera/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Cancel incorrect delivery" }));
    const cancelDialog = screen.getByRole("alertdialog", { name: "Cancel this delivery?" });
    await user.click(within(cancelDialog).getByRole("button", { name: "Cancel delivery" }));

    await waitFor(() => expect(mockedUpdateStatus).toHaveBeenCalledWith(77, "CANCELLED"));
    expect(await screen.findByText(/historical record is preserved/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Prepare replacement" })).toBeInTheDocument();
  });
});

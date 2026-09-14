import type { ReactNode } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  generateOrderInvoice,
  getMyOrderBilling,
  getMyOrderDetail,
  getMyOrders,
  getOrderBilling,
  getOrderDetail,
  getOrders,
  updateOrderPayment,
  updateOrderStatus,
  type OrderBilling,
  type OrderDetail,
  type OrderSummary,
} from "../api/orders";
import {
  CustomerOrderDetailPage,
  CustomerOrderHistoryPage,
  StaffOrderDetailPage,
  StaffOrderListPage,
} from "./OrderReadPages";

vi.mock("../api/orders", () => ({
  getOrders: vi.fn(),
  getOrderDetail: vi.fn(),
  getOrderBilling: vi.fn(),
  getMyOrders: vi.fn(),
  getMyOrderDetail: vi.fn(),
  getMyOrderBilling: vi.fn(),
  generateOrderInvoice: vi.fn(),
  updateOrderPayment: vi.fn(),
  updateOrderStatus: vi.fn(),
  getOrderApiError: (error: unknown, fallback: string) => {
    const structured = error as { message?: string; status?: number };
    return {
      message: structured?.message ?? fallback,
      status: structured?.status,
      fields: {},
    };
  },
}));

const mockedGetOrders = vi.mocked(getOrders);
const mockedGetOrderDetail = vi.mocked(getOrderDetail);
const mockedGetOrderBilling = vi.mocked(getOrderBilling);
const mockedGetMyOrders = vi.mocked(getMyOrders);
const mockedGetMyOrderDetail = vi.mocked(getMyOrderDetail);
const mockedGetMyOrderBilling = vi.mocked(getMyOrderBilling);
const mockedGenerateOrderInvoice = vi.mocked(generateOrderInvoice);
const mockedUpdateOrderPayment = vi.mocked(updateOrderPayment);
const mockedUpdateOrderStatus = vi.mocked(updateOrderStatus);

const summary: OrderSummary = {
  id: 91,
  orderNumber: "ORD-ABC123",
  customerId: 51,
  customerName: "Asha Perera",
  customerEmail: "asha@example.com",
  status: "CONFIRMED",
  createdAt: "2026-08-23T10:00:00Z",
  updatedAt: "2026-08-23T10:05:00Z",
  itemCount: 2,
  totalAmount: "7670.00",
};

const detail: OrderDetail = {
  ...summary,
  items: [
    {
      id: 101,
      productId: 61,
      variantId: 81,
      productName: "Classic Oxford Shirt",
      quantity: 2,
      selectedSize: "M",
      selectedColor: "White",
      unitPriceSnapshot: "2490.00",
      lineTotal: "4980.00",
    },
    {
      id: 102,
      productId: 61,
      variantId: 82,
      productName: "Classic Oxford Shirt",
      quantity: 1,
      selectedSize: "L",
      selectedColor: "Navy",
      unitPriceSnapshot: "2690.00",
      lineTotal: "2690.00",
    },
  ],
  totalAmount: "7670.00",
  allowedStatusTransitions: ["CANCELLED"],
  statusHistory: [
    {
      id: 201,
      fromStatus: "PENDING",
      toStatus: "CONFIRMED",
      changedAt: "2026-08-23T10:05:00Z",
    },
  ],
};

const emptyBilling: OrderBilling = {
  invoiceGenerated: false,
  invoice: null,
  payment: null,
};

const issuedBilling: OrderBilling = {
  invoiceGenerated: true,
  invoice: {
    id: 301,
    orderId: 91,
    invoiceNumber: "INV-ABC123",
    totalAmount: "7670.00",
    issuedAt: "2026-08-23T10:06:00Z",
  },
  payment: {
    id: 302,
    paymentStatus: "UNPAID",
    amountPaid: "0.00",
    paymentMethod: null,
    paymentReference: null,
    note: null,
    recordedAt: "2026-08-23T10:06:00Z",
    updatedAt: "2026-08-23T10:06:00Z",
  },
};

function renderRoute(path: string, routePath: string, element: ReactNode) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={routePath} element={element} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("TGMS-45 order read pages", () => {
  beforeEach(() => {
    mockedGetOrders.mockResolvedValue([summary]);
    mockedGetOrderDetail.mockResolvedValue(detail);
    mockedGetOrderBilling.mockResolvedValue(emptyBilling);
    mockedGetMyOrders.mockResolvedValue([summary]);
    mockedGetMyOrderDetail.mockResolvedValue(detail);
    mockedGetMyOrderBilling.mockResolvedValue(emptyBilling);
    mockedGenerateOrderInvoice.mockResolvedValue({
      message: "Invoice generated successfully.",
      billing: issuedBilling,
    });
    mockedUpdateOrderPayment.mockResolvedValue({
      message: "Payment record updated successfully.",
      billing: {
        ...issuedBilling,
        payment: {
          ...issuedBilling.payment!,
          paymentStatus: "PAID",
          amountPaid: "7670.00",
          paymentMethod: "CASH",
        },
      },
    });
    mockedUpdateOrderStatus.mockResolvedValue({
      message: "Order status updated successfully.",
      order: {
        ...detail,
        status: "CONFIRMED",
        updatedAt: "2026-08-23T10:10:00Z",
        allowedStatusTransitions: ["CANCELLED"],
        statusHistory: [
          {
            id: 202,
            fromStatus: "PENDING",
            toStatus: "CONFIRMED",
            changedAt: "2026-08-23T10:10:00Z",
          },
        ],
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows a loading state while staff orders are being read", () => {
    mockedGetOrders.mockReturnValue(new Promise(() => undefined));
    renderRoute("/orders", "/orders", <StaffOrderListPage />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading orders");
  });

  it("lets staff search and filter permitted orders with stored totals", async () => {
    const user = userEvent.setup();
    renderRoute("/orders", "/orders", <StaffOrderListPage />);

    expect(await screen.findByRole("heading", { name: "Customer orders" })).toBeInTheDocument();
    expect(screen.getByText("Asha Perera")).toBeInTheDocument();
    expect(screen.getByText("LKR 7670.00")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Search orders"), "  Asha  ");
    await user.selectOptions(screen.getByLabelText("Status"), "CONFIRMED");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    await waitFor(() => expect(mockedGetOrders).toHaveBeenLastCalledWith(
      { search: "Asha", status: "CONFIRMED" },
      undefined,
    ));
  });

  it("renders staff order detail from stored item snapshots and totals", async () => {
    renderRoute("/orders/91", "/orders/:orderId", <StaffOrderDetailPage />);

    expect(await screen.findByRole("heading", { name: "ORD-ABC123" })).toBeInTheDocument();
    expect(screen.getByText("M · White · Qty 2")).toBeInTheDocument();
    expect(screen.getByText("LKR 2490.00 each")).toBeInTheDocument();
    expect(screen.getAllByText("LKR 7670.00").length).toBeGreaterThan(0);
    expect(mockedGetOrderDetail).toHaveBeenCalledWith(91, expect.any(AbortSignal));
  });

  it("lets staff confirm a pending order without bypassing Production Management", async () => {
    const user = userEvent.setup();
    mockedGetOrderDetail.mockResolvedValue({
      ...detail,
      status: "PENDING",
      allowedStatusTransitions: ["CONFIRMED", "CANCELLED"],
      statusHistory: [],
    });
    renderRoute("/orders/91", "/orders/:orderId", <StaffOrderDetailPage />);

    expect(await screen.findByRole("heading", { name: "Update order status" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Confirmed" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Cancelled" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "In Production" })).not.toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Completed" })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Next status"), "CONFIRMED");
    await user.click(screen.getByRole("button", { name: "Update status" }));

    await waitFor(() => expect(mockedUpdateOrderStatus).toHaveBeenCalledWith(91, "CONFIRMED"));
    expect(await screen.findByText("Order status updated successfully.")).toBeInTheDocument();
    expect(screen.getByText("Pending → Confirmed")).toBeInTheDocument();
    expect(screen.getByText(/production progress is owned by Production Management/i)).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "In Production" })).not.toBeInTheDocument();
  });

  it("lets staff generate an invoice and record manual payment details", async () => {
    const user = userEvent.setup();
    renderRoute("/orders/91", "/orders/:orderId", <StaffOrderDetailPage />);

    expect(await screen.findByRole("heading", { name: "Invoice & payment record" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Generate invoice" }));
    expect(await screen.findByText("INV-ABC123")).toBeInTheDocument();
    expect(screen.getByText("Invoice generated successfully.")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Payment status"), "PAID");
    expect(screen.getByLabelText("Amount paid")).toHaveValue("7670.00");
    expect(screen.getByLabelText("Amount paid")).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Save payment record" }));
    expect(mockedUpdateOrderPayment).not.toHaveBeenCalled();
    expect(screen.getByText("Select how the payment was recorded.")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Payment method (required)"), "CASH");
    await user.click(screen.getByRole("button", { name: "Save payment record" }));

    await waitFor(() => expect(mockedUpdateOrderPayment).toHaveBeenCalledWith(91, expect.objectContaining({
      paymentStatus: "PAID",
      amountPaid: "7670.00",
      paymentMethod: "CASH",
    })));
    expect(await screen.findByText("Payment record updated successfully.")).toBeInTheDocument();
    expect(screen.getByText(/payment is fully recorded/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save payment record" })).not.toBeInTheDocument();
  });

  it("validates partial payment amount and method before calling the API", async () => {
    const user = userEvent.setup();
    mockedGetOrderBilling.mockResolvedValue(issuedBilling);
    renderRoute("/orders/91", "/orders/:orderId", <StaffOrderDetailPage />);

    await screen.findByRole("heading", { name: "Invoice & payment record" });
    await user.selectOptions(screen.getByLabelText("Payment status"), "PARTIALLY_PAID");
    expect(screen.getByLabelText("Amount paid")).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "Save payment record" }));
    expect(mockedUpdateOrderPayment).not.toHaveBeenCalled();
    expect(screen.getByText("Enter a non-negative amount with up to 2 decimal places.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Amount paid"), "2500.00");
    await user.selectOptions(screen.getByLabelText("Payment method (required)"), "BANK_TRANSFER");
    await user.click(screen.getByRole("button", { name: "Save payment record" }));

    await waitFor(() => expect(mockedUpdateOrderPayment).toHaveBeenCalledWith(91, expect.objectContaining({
      paymentStatus: "PARTIALLY_PAID",
      amountPaid: "2500.00",
      paymentMethod: "BANK_TRANSFER",
    })));
  });

  it("uses ownership-scoped history and detail APIs for customers", async () => {
    renderRoute("/orders/history", "/orders/history", <CustomerOrderHistoryPage />);

    expect(await screen.findByRole("heading", { name: "My order history" })).toBeInTheDocument();
    expect(screen.getByText("ORD-ABC123")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Track progress" }))
      .toHaveAttribute("href", "/orders/track/91");
    expect(mockedGetMyOrders).toHaveBeenCalledWith(expect.any(AbortSignal));
    expect(mockedGetOrders).not.toHaveBeenCalled();

    cleanup();
    renderRoute("/orders/history/91", "/orders/history/:orderId", <CustomerOrderDetailPage />);
    expect(await screen.findByRole("heading", { name: "ORD-ABC123" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Order progress" })).toBeInTheDocument();
    expect(screen.getByText("Pending → Confirmed")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Update order status" })).not.toBeInTheDocument();
    expect(mockedGetMyOrderDetail).toHaveBeenCalledWith(91, expect.any(AbortSignal));
  });

  it("shows empty and ownership-safe not-found states", async () => {
    mockedGetMyOrders.mockResolvedValue([]);
    renderRoute("/orders/history", "/orders/history", <CustomerOrderHistoryPage />);
    expect(await screen.findByRole("heading", { name: "No orders yet" })).toBeInTheDocument();

    cleanup();
    mockedGetMyOrderDetail.mockRejectedValue({ status: 404, message: "Order was not found." });
    renderRoute("/orders/history/999", "/orders/history/:orderId", <CustomerOrderDetailPage />);
    expect(await screen.findByRole("heading", { name: "Order details unavailable" })).toBeInTheDocument();
    expect(screen.getByText(/not found or is not available to your account/i)).toBeInTheDocument();
  });
});

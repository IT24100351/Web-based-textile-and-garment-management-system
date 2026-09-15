import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  createProductionTask,
  getProductionEligibleOrders,
} from "../api/productionTasks";
import { CreateProductionTaskPage } from "./CreateProductionTaskPage";

vi.mock("../api/productionTasks", () => ({
  createProductionTask: vi.fn(),
  getProductionEligibleOrders: vi.fn(),
  getProductionTaskApiError: (error: unknown, fallback: string) => {
    const value = error as { message?: string; fields?: Record<string, string> };
    return { message: value?.message ?? fallback, fields: value?.fields ?? {} };
  },
}));

const mockedCreateTask = vi.mocked(createProductionTask);
const mockedGetEligibleOrders = vi.mocked(getProductionEligibleOrders);

const eligibleOrders = [{
  orderId: 7501,
  orderNumber: "ORD-PRODUCTION-READY",
  customerId: 7101,
  currentStatus: "CONFIRMED" as const,
  readyForProduction: true,
  items: [{
    orderItemId: 7601,
    productId: 7201,
    variantId: 7301,
    quantity: 3,
    selectedSize: "L",
    selectedColor: "Navy",
  }],
}];

function renderPage() {
  render(<MemoryRouter><CreateProductionTaskPage /></MemoryRouter>);
}

describe("CreateProductionTaskPage", () => {
  beforeEach(() => {
    mockedGetEligibleOrders.mockResolvedValue(eligibleOrders);
    mockedCreateTask.mockResolvedValue({
      message: "Production task created successfully.",
      task: {
        id: 8801,
        taskNumber: "PRD-ABCDEF12345678901234",
        orderId: 7501,
        status: "PENDING",
        startedAt: null,
        completedAt: null,
        createdAt: "2026-08-23T14:00:00Z",
        updatedAt: "2026-08-23T14:00:00Z",
        qualityControlResult: "PENDING",
        qualityCheckedByUserId: null,
        qualityCheckedAt: null,
      },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("creates a task from a production-ready Order Management handoff", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "Create production task" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Eligible customer order"), "7501");

    expect(screen.getByRole("heading", { name: "ORD-PRODUCTION-READY" })).toBeInTheDocument();
    expect(screen.getByText(/Product #7201 · Variant #7301 · L · Navy/)).toBeInTheDocument();
    expect(screen.getByText("Required quantity: 3")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Create production task" }));
    await waitFor(() => expect(mockedCreateTask).toHaveBeenCalledWith(7501));
    expect(await screen.findByText("PRD-ABCDEF12345678901234")).toBeInTheDocument();
    expect(screen.getByText(/Task ID #8801 · Order ID #7501 · PENDING/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Add production work details" }))
      .toHaveAttribute("href", "/production/tasks/8801");
  });

  it("shows a no-eligible-order empty state", async () => {
    mockedGetEligibleOrders.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByRole("heading", { name: "No orders ready for production" }))
      .toBeInTheDocument();
  });

  it("shows an actionable stale-order error returned at save time", async () => {
    const user = userEvent.setup();
    mockedCreateTask.mockRejectedValue({
      message: "The selected order is not currently ready for Production Management.",
      fields: { orderId: "Choose an order whose Order Management handoff reports readyForProduction=true." },
    });
    renderPage();
    await screen.findByRole("heading", { name: "Create production task" });
    await user.selectOptions(screen.getByLabelText("Eligible customer order"), "7501");
    await user.click(screen.getByRole("button", { name: "Create production task" }));
    expect(await screen.findByText(/readyForProduction=true/)).toBeInTheDocument();
    expect(screen.getByLabelText("Eligible customer order")).toHaveAttribute("aria-invalid", "true");
  });
});

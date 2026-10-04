import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getProductionTaskRecords } from "../api/productionTasks";
import { ProductionTaskRecordsPage } from "./ProductionTaskRecordsPage";

vi.mock("../api/productionTasks", () => ({
  getProductionTaskRecords: vi.fn(),
  getProductionTaskApiError: (_error: unknown, fallback: string) => ({ message: fallback, fields: {} }),
}));

const mockedGetRecords = vi.mocked(getProductionTaskRecords);

const completed = {
  task: {
    id: 8801,
    taskNumber: "PRD-RECORD-8801",
    orderId: 7501,
    status: "COMPLETED" as const,
    startedAt: "2026-08-23T15:00:00Z",
    completedAt: "2026-08-23T17:00:00Z",
    createdAt: "2026-08-23T14:00:00Z",
    updatedAt: "2026-08-23T17:00:00Z",
    qualityControlResult: "PASSED" as const,
    qualityCheckedByUserId: 7999,
    qualityCheckedAt: "2026-08-23T16:45:00Z",
  },
  orderNumber: "ORD-RECORD-7501",
  customerId: 7101,
  orderStatus: "READY_FOR_DELIVERY" as const,
  orderItemCount: 2,
  readyForDelivery: true,
};

const readyToComplete = {
  task: {
    id: 8802,
    taskNumber: "PRD-RECORD-8802",
    orderId: 7502,
    status: "IN_PROGRESS" as const,
    startedAt: "2026-08-24T09:00:00Z",
    completedAt: null,
    createdAt: "2026-08-24T08:30:00Z",
    updatedAt: "2026-08-24T10:15:00Z",
    qualityControlResult: "PASSED" as const,
    qualityCheckedByUserId: 7999,
    qualityCheckedAt: "2026-08-24T10:00:00Z",
  },
  orderNumber: "ORD-RECORD-7502",
  customerId: 7102,
  orderStatus: "IN_PRODUCTION" as const,
  orderItemCount: 1,
  readyForDelivery: false,
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("ProductionTaskRecordsPage", () => {
  it("shows completed records and their QC/delivery signal", async () => {
    mockedGetRecords.mockImplementation(async (view) => view === "COMPLETED" ? [completed] : []);
    const user = userEvent.setup();
    render(<MemoryRouter><ProductionTaskRecordsPage /></MemoryRouter>);

    expect(await screen.findByRole("heading", { name: "No production records found" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Records"), "COMPLETED");

    expect(await screen.findByText("PRD-RECORD-8801")).toBeInTheDocument();
    expect(screen.getByText("PASSED")).toBeInTheDocument();
    expect(screen.getByText("READY")).toBeInTheDocument();
    expect(screen.getByText("Production is complete and the order is ready for delivery.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View completed record" })).toHaveAttribute("href", "/production/tasks/8801");
    await waitFor(() => expect(mockedGetRecords).toHaveBeenLastCalledWith("COMPLETED", expect.any(AbortSignal)));
  });

  it("shows when an active production record is ready to complete", async () => {
    mockedGetRecords.mockResolvedValue([readyToComplete]);
    render(<MemoryRouter><ProductionTaskRecordsPage /></MemoryRouter>);

    expect(await screen.findByText("PRD-RECORD-8802")).toBeInTheDocument();
    expect(screen.getByText("Quality control has passed. Open the record to mark production completed.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Complete production" })).toHaveAttribute("href", "/production/tasks/8802");
  });

  it("explains why a completed task's order is still in production", async () => {
    mockedGetRecords.mockResolvedValue([{
      ...completed,
      orderStatus: "IN_PRODUCTION",
      readyForDelivery: false,
    }]);
    render(<MemoryRouter><ProductionTaskRecordsPage /></MemoryRouter>);

    expect(await screen.findByText("This task is complete; other production tasks for the order are still pending."))
      .toBeInTheDocument();
    expect(screen.queryByText("Production is complete and the order is ready for delivery."))
      .not.toBeInTheDocument();
  });
});

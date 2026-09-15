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
    expect(screen.getByRole("link", { name: "View production record" })).toHaveAttribute("href", "/production/tasks/8801");
    await waitFor(() => expect(mockedGetRecords).toHaveBeenLastCalledWith("COMPLETED", expect.any(AbortSignal)));
  });
});

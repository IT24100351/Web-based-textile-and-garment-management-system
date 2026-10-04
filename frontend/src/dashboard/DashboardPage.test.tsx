import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { getDashboardReport } from "../api/reports";
import { DashboardPage } from "./DashboardPage";

vi.mock("../api/reports", () => ({ getDashboardReport: vi.fn() }));
const mockedGetDashboardReport = vi.mocked(getDashboardReport);

const report = {
  role: "ADMINISTRATOR" as const,
  from: "2026-08-01",
  to: "2026-08-31",
  timeZone: "UTC",
  generatedAt: "2026-08-26T12:00:00Z",
  sections: [
    {
      key: "orders",
      title: "Orders",
      basis: "Orders created in the selected period, grouped by current status.",
      metrics: [
        { key: "totalCreated", label: "Created in period", value: 7 },
        { key: "completed", label: "Completed", value: 4 },
      ],
    },
  ],
  notes: ["Metrics come from stored core records."],
};

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetDashboardReport.mockResolvedValue(report);
  });

  it("loads stored-data metrics and displays the applied report period", async () => {
    render(<DashboardPage role="ADMINISTRATOR" />);

    expect(await screen.findByRole("heading", { name: "Orders" })).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText(/2026-08-01 through 2026-08-31 · UTC/)).toBeInTheDocument();
  });

  it("validates and applies date filters through the server report API", async () => {
    const user = userEvent.setup();
    render(<DashboardPage role="ADMINISTRATOR" />);
    await screen.findByRole("heading", { name: "Orders" });

    const from = screen.getByLabelText("From");
    const to = screen.getByLabelText("To");
    await user.clear(from);
    await user.type(from, "2026-08-01");
    await user.clear(to);
    await user.type(to, "2026-08-31");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    expect(mockedGetDashboardReport).toHaveBeenLastCalledWith(
      "2026-08-01",
      "2026-08-31",
      expect.any(AbortSignal),
    );
  });

  it("handles empty permitted data without inventing analytics", async () => {
    mockedGetDashboardReport.mockResolvedValue({
      ...report,
      sections: [{
        ...report.sections[0],
        metrics: [{ key: "totalCreated", label: "Created in period", value: 0 }],
      }],
    });
    render(<DashboardPage role="ADMINISTRATOR" />);

    expect(await screen.findByRole("heading", { name: "No matching report activity" }))
      .toBeInTheDocument();
    expect(screen.getByText("0")).toBeInTheDocument();
  });

  it("shows an explicit empty-scope state for a role without these core report permissions", async () => {
    mockedGetDashboardReport.mockResolvedValue({
      ...report,
      role: "SUPPLIER",
      sections: [],
      notes: ["Supplier Management has no cross-module operational report scope."],
    });
    render(<DashboardPage role="SUPPLIER" />);

    expect(await screen.findByRole("heading", { name: "No permitted operational report sections" }))
      .toBeInTheDocument();
  });

  it("shows a safe retry state when the report API fails", async () => {
    mockedGetDashboardReport.mockRejectedValue(new Error("report unavailable"));
    render(<DashboardPage role="ADMINISTRATOR" />);

    expect(await screen.findByRole("heading", { name: "Report unavailable" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Retry report" })).toBeInTheDocument();
  });
});

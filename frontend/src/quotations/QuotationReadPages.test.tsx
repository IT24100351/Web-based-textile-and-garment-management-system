import { StrictMode } from "react";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

import { getQuotation, getQuotations } from "../api/quotations";
import { QuotationDetailPage, QuotationListPage } from "./QuotationReadPages";

vi.mock("../api/quotations", () => ({
  getQuotations: vi.fn(),
  getQuotation: vi.fn(),
  getQuotationApiError: (error: unknown, fallback: string) => ({ message: error instanceof Error ? error.message : fallback, fields: {} }),
}));

const mockedGetQuotations = vi.mocked(getQuotations);
const mockedGetQuotation = vi.mocked(getQuotation);

const summary = { id: 91, quotationNumber: "QUO-ABC", customerId: 51, customerName: "Asha Perera", customerEmail: "asha@example.com", issuedAt: "2026-08-23T10:10:00Z", itemCount: 1, totalAmount: "4980.00" };

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("quotation read pages", () => {
  it("lists issued quotations with historical totals", async () => {
    mockedGetQuotations.mockResolvedValue([summary]);
    render(<MemoryRouter><QuotationListPage /></MemoryRouter>);
    expect(await screen.findByText("QUO-ABC")).toBeInTheDocument();
    expect(screen.getByText("LKR 4980.00")).toBeInTheDocument();
  });

  it("ignores a canceled Strict Mode request after the replacement request succeeds", async () => {
    let rejectCanceledRequest!: (reason?: unknown) => void;
    const canceledRequest = new Promise<never>((_resolve, reject) => {
      rejectCanceledRequest = reject;
    });
    mockedGetQuotations
      .mockReturnValueOnce(canceledRequest)
      .mockResolvedValueOnce([summary]);

    render(
      <StrictMode>
        <MemoryRouter><QuotationListPage /></MemoryRouter>
      </StrictMode>,
    );

    expect(await screen.findByText("QUO-ABC")).toBeInTheDocument();
    await act(async () => {
      rejectCanceledRequest(new DOMException("The request was aborted.", "AbortError"));
    });

    await waitFor(() => {
      expect(screen.queryByText("Quotations unavailable")).not.toBeInTheDocument();
      expect(screen.getByText("QUO-ABC")).toBeInTheDocument();
    });
  });

  it("shows immutable quoted item snapshots in detail", async () => {
    mockedGetQuotation.mockResolvedValue({
      ...summary,
      items: [{ id: 101, productId: 61, variantId: 81, productName: "Classic Oxford Shirt", quantity: 2, selectedSize: "M", selectedColor: "White", unitPriceSnapshot: "2490.00", lineTotal: "4980.00" }],
    });
    render(
      <MemoryRouter initialEntries={["/quotations/91"]}>
        <Routes><Route path="/quotations/:quotationId" element={<QuotationDetailPage />} /></Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText("QUO-ABC")).toBeInTheDocument();
    expect(screen.getByText("Classic Oxford Shirt")).toBeInTheDocument();
    expect(screen.getByText(/M · White · Qty 2/)).toBeInTheDocument();
    expect(screen.getByText("LKR 4980.00", { selector: "span" })).toBeInTheDocument();
    expect(screen.getByText(/immutable/i)).toBeInTheDocument();
  });
});

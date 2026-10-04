import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createQuotation, getQuotationCustomers } from "../api/quotations";
import { getCatalogProducts } from "../api/products";
import { CreateQuotationPage } from "./CreateQuotationPage";

vi.mock("../api/quotations", () => ({
  createQuotation: vi.fn(),
  getQuotationCustomers: vi.fn(),
  getQuotationApiError: (error: unknown, fallback: string) => {
    const value = error as { message?: string; fields?: Record<string, string> };
    return { message: value?.message ?? fallback, fields: value?.fields ?? {} };
  },
}));
vi.mock("../api/products", () => ({
  getCatalogProducts: vi.fn(),
  getProductApiError: (error: unknown, fallback: string) => ({
    message: error instanceof Error ? error.message : fallback,
    fields: {},
  }),
}));

const mockedCreateQuotation = vi.mocked(createQuotation);
const mockedGetCustomers = vi.mocked(getQuotationCustomers);
const mockedGetProducts = vi.mocked(getCatalogProducts);

const customers = [{ id: 51, fullName: "Asha Perera", email: "asha@example.com" }];
const products = [{
  id: 61,
  categoryId: 71,
  name: "Classic Oxford Shirt",
  description: null,
  status: "ACTIVE" as const,
  createdAt: "2026-08-23T10:00:00Z",
  updatedAt: "2026-08-23T10:00:00Z",
  category: { id: 71, name: "Formal Wear", description: null, status: "ACTIVE" as const, createdAt: "2026-08-23T10:00:00Z", updatedAt: "2026-08-23T10:00:00Z" },
  variants: [{ id: 81, productId: 61, size: "M", color: "White", price: "2490.00", status: "AVAILABLE" as const, createdAt: "2026-08-23T10:00:00Z", updatedAt: "2026-08-23T10:00:00Z" }],
}];

function renderPage() {
  render(<MemoryRouter><CreateQuotationPage /></MemoryRouter>);
}

describe("CreateQuotationPage", () => {
  beforeEach(() => {
    mockedGetCustomers.mockResolvedValue(customers);
    mockedGetProducts.mockResolvedValue(products);
    mockedCreateQuotation.mockResolvedValue({
      message: "Customer quotation issued successfully.",
      id: 91,
      quotationNumber: "QUO-ABCDEF12345678901234",
      customer: customers[0],
      issuedAt: "2026-08-23T10:10:00Z",
      items: [{ id: 101, productId: 61, variantId: 81, productName: "Classic Oxford Shirt", quantity: 2, selectedSize: "M", selectedColor: "White", unitPriceSnapshot: "2490.00", lineTotal: "4980.00" }],
      totalAmount: "4980.00",
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("issues a quotation using customer and product variant IDs", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByRole("heading", { name: "Prepare customer quotation" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Customer"), "51");
    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    const quantity = screen.getByLabelText("Quantity");
    await user.clear(quantity);
    await user.type(quantity, "2");
    expect(screen.getByText(/Line total LKR 4980.00/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Issue quotation" }));
    await waitFor(() => expect(mockedCreateQuotation).toHaveBeenCalledWith({
      customerId: 51,
      items: [{ productId: 61, variantId: 81, quantity: 2 }],
    }));
    expect(await screen.findByText("QUO-ABCDEF12345678901234")).toBeInTheDocument();
    expect(screen.getByText("Total: LKR 4980.00")).toBeInTheDocument();
  });


  it("shows an empty state when no active customer can receive a quotation", async () => {
    mockedGetCustomers.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByRole("heading", { name: "No active customers" }))
      .toBeInTheDocument();
  });

  it("shows actionable server availability errors on the exact line", async () => {
    const user = userEvent.setup();
    mockedCreateQuotation.mockRejectedValue({
      message: "One or more quoted garment selections are no longer available.",
      fields: { "items[0].variantId": "This product/size/color selection is no longer available. Choose an available garment variant." },
    });
    renderPage();
    await screen.findByRole("heading", { name: "Prepare customer quotation" });
    await user.selectOptions(screen.getByLabelText("Customer"), "51");
    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    await user.click(screen.getByRole("button", { name: "Issue quotation" }));
    expect(await screen.findByText(/selection is no longer available/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Size / color")).toHaveAttribute("aria-invalid", "true");
  });
});

import { StrictMode } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createMyOrder } from "../api/orders";
import { getCatalogProducts } from "../api/products";
import { CustomerPlaceOrderPage } from "./CustomerPlaceOrderPage";
import { downloadOrderReceipt } from "./orderReceiptPdf";

vi.mock("../api/orders", () => ({
  createMyOrder: vi.fn(),
  getOrderApiError: (error: unknown, fallback: string) => {
    const structured = error as { message?: string; fields?: Record<string, string> };
    return {
      message: structured?.message ?? (error instanceof Error ? error.message : fallback),
      fields: structured?.fields ?? {},
    };
  },
}));

vi.mock("../api/products", () => ({
  getCatalogProducts: vi.fn(),
  getProductApiError: (error: unknown, fallback: string) => ({
    message: error instanceof Error ? error.message : fallback,
    fields: {},
  }),
}));

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: {
      id: 51,
      fullName: "Asha Perera",
      email: "asha@example.com",
      role: "CUSTOMER",
    },
    isLoading: false,
    sessionError: null,
    login: vi.fn(),
    refreshUser: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock("./orderReceiptPdf", () => ({
  downloadOrderReceipt: vi.fn(),
}));

const mockedCreateMyOrder = vi.mocked(createMyOrder);
const mockedGetCatalogProducts = vi.mocked(getCatalogProducts);
const mockedDownloadOrderReceipt = vi.mocked(downloadOrderReceipt);

function renderPage(path = "/orders/place", strict = false) {
  const page = (
    <MemoryRouter initialEntries={[path]}>
      <CustomerPlaceOrderPage />
    </MemoryRouter>
  );
  return render(strict ? <StrictMode>{page}</StrictMode> : page);
}

const products = [
  {
    id: 61,
    categoryId: 71,
    name: "Classic Oxford Shirt",
    description: null,
    imageUrl: "/demo-products/classic-oxford-shirt.jpg",
    status: "ACTIVE" as const,
    createdAt: "2026-08-23T10:00:00Z",
    updatedAt: "2026-08-23T10:00:00Z",
    category: {
      id: 71,
      name: "Formal Wear",
      description: null,
      status: "ACTIVE" as const,
      createdAt: "2026-08-23T10:00:00Z",
      updatedAt: "2026-08-23T10:00:00Z",
    },
    variants: [
      {
        id: 81,
        productId: 61,
        size: "M",
        color: "White",
        price: "2490.00",
        status: "AVAILABLE" as const,
        createdAt: "2026-08-23T10:00:00Z",
        updatedAt: "2026-08-23T10:00:00Z",
      },
    ],
  },
];

describe("CustomerPlaceOrderPage", () => {
  beforeEach(() => {
    mockedGetCatalogProducts.mockResolvedValue(products);
    mockedCreateMyOrder.mockResolvedValue({
      message: "Customer order created successfully.",
      id: 91,
      orderNumber: "ORD-ABCDEF12345678901234",
      customerId: 51,
      customer: { id: 51, fullName: "Asha Perera", email: "asha@example.com" },
      status: "PENDING",
      createdAt: "2026-08-23T10:10:00Z",
      totalAmount: "4980.00",
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
      ],
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("places an order without allowing a customer identity in the payload", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole("heading", { name: "Place an order" }))
      .toBeInTheDocument();
    expect(screen.getByText(/Signed in as/)).toHaveTextContent("Asha Perera");

    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    const quantity = screen.getByLabelText("Quantity");
    await user.clear(quantity);
    await user.type(quantity, "2");

    expect(screen.getByText("Selected item preview")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Classic Oxford Shirt selected item preview" }))
      .toBeInTheDocument();
    expect(screen.getByText("Line total").parentElement).toHaveTextContent("LKR 4980.00");

    await user.click(screen.getByRole("button", { name: "Place my order" }));

    await waitFor(() => expect(mockedCreateMyOrder).toHaveBeenCalledWith([
      { productId: 61, variantId: 81, quantity: 2 },
    ]));
    expect(await screen.findByRole("heading", { name: "Order confirmed" })).toBeInTheDocument();
    expect(screen.getAllByText("ORD-ABCDEF12345678901234").length).toBeGreaterThan(0);
    expect(screen.getByText(/Your order was recorded successfully for Asha Perera/i))
      .toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Items you selected" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Classic Oxford Shirt selected garment" }))
      .toBeInTheDocument();
    expect(screen.getByText("Line total:").parentElement).toHaveTextContent("LKR 4980.00");
    expect(screen.getByText("Order total").parentElement).toHaveTextContent("LKR 4980.00");

    await user.click(screen.getByRole("button", { name: "Download receipt (PDF)" }));
    await waitFor(() => expect(mockedDownloadOrderReceipt).toHaveBeenCalledWith(
      expect.objectContaining({
        orderNumber: "ORD-ABCDEF12345678901234",
        totalAmount: "4980.00",
      }),
    ));
  });

  it("validates positive whole-number quantity before submission", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("heading", { name: "Place an order" });

    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    const quantity = screen.getByLabelText("Quantity");
    await user.clear(quantity);
    await user.type(quantity, "0");
    await user.click(screen.getByRole("button", { name: "Place my order" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Please correct the highlighted order fields.",
    );
    expect(screen.getByText("Quantity must be a positive whole number."))
      .toBeInTheDocument();
    expect(mockedCreateMyOrder).not.toHaveBeenCalled();
  });

  it("surfaces the same server availability field error used by the Sales Officer flow", async () => {
    const user = userEvent.setup();
    mockedCreateMyOrder.mockRejectedValue({
      message: "The selected product variant is not available for a new order.",
      fields: {
        "items[0].variantId": "This product/size/color selection is no longer available. Choose an available garment variant.",
      },
    });
    renderPage();
    await screen.findByRole("heading", { name: "Place an order" });

    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    await user.click(screen.getByRole("button", { name: "Place my order" }));

    expect(await screen.findByText(/selection is no longer available/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Size / color")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("alert")).toHaveTextContent(/product variant is not available for a new order/i);
  });

  it("shows an empty state when there are no available product variants", async () => {
    mockedGetCatalogProducts.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByRole("heading", { name: "No garments available to order" }))
      .toBeInTheDocument();
  });

  it("does not show a false catalog warning when Strict Mode cancels the initial request", async () => {
    mockedGetCatalogProducts
      .mockImplementationOnce((_filters, signal) => new Promise((_resolve, reject) => {
        signal?.addEventListener("abort", () => reject(new Error("Request cancelled")));
      }))
      .mockResolvedValueOnce(products);

    renderPage("/orders/place", true);

    expect(await screen.findByRole("heading", { name: "Place an order" }))
      .toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Classic Oxford Shirt/i }))
      .toBeInTheDocument();
    expect(screen.queryByText("Request cancelled")).not.toBeInTheDocument();
    expect(screen.queryByText(/Available garments could not be loaded/i))
      .not.toBeInTheDocument();
  });

  it("prefills the exact catalog item passed through the add-to-cart link", async () => {
    renderPage("/orders/place?productId=61&variantId=81");

    expect(await screen.findByRole("heading", { name: "Place an order" }))
      .toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByLabelText("Product")).toHaveValue("61");
      expect(screen.getByLabelText("Size / color")).toHaveValue("81");
    });
    expect(screen.getByRole("status")).toHaveTextContent(
      "Classic Oxford Shirt was added from the garment catalog",
    );
    expect(screen.getByText("Selected item preview")).toBeInTheDocument();
  });
});

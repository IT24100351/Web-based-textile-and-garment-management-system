import { StrictMode } from "react";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createOrder, getOrderCustomers } from "../api/orders";
import { getCatalogProducts } from "../api/products";
import { CreateOrderPage } from "./CreateOrderPage";

vi.mock("../api/orders", () => ({
  createOrder: vi.fn(),
  getOrderCustomers: vi.fn(),
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

const mockedCreateOrder = vi.mocked(createOrder);
const mockedGetOrderCustomers = vi.mocked(getOrderCustomers);
const mockedGetCatalogProducts = vi.mocked(getCatalogProducts);

const customers = [
  { id: 51, fullName: "Asha Perera", email: "asha@example.com" },
];

const products = [
  {
    id: 61,
    categoryId: 71,
    name: "Classic Oxford Shirt",
    description: null,
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

describe("CreateOrderPage", () => {
  beforeEach(() => {
    mockedGetOrderCustomers.mockResolvedValue(customers);
    mockedGetCatalogProducts.mockResolvedValue(products);
    mockedCreateOrder.mockResolvedValue({
      message: "Customer order created successfully.",
      id: 91,
      orderNumber: "ORD-ABCDEF12345678901234",
      customerId: 51,
      customer: customers[0],
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

  it("creates a reviewed customer order using product variant IDs and quantity", async () => {
    const user = userEvent.setup();
    render(<CreateOrderPage />);

    expect(await screen.findByRole("heading", { name: "Create customer order" }))
      .toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Active customer"), "51");
    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    const quantity = screen.getByLabelText("Quantity");
    await user.clear(quantity);
    await user.type(quantity, "2");

    expect(screen.getByText(/Review:/i).closest("div"))
      .toHaveTextContent("Review: Classic Oxford Shirt · M · White · LKR 2490.00 each");

    await user.click(screen.getByRole("button", { name: "Save customer order" }));

    await waitFor(() => expect(mockedCreateOrder).toHaveBeenCalledWith({
      customerId: 51,
      items: [{ productId: 61, variantId: 81, quantity: 2 }],
    }));
    expect(await screen.findByText("ORD-ABCDEF12345678901234")).toBeInTheDocument();
    expect(screen.getByText(/Customer order created successfully for Asha Perera/i))
      .toBeInTheDocument();
    expect(screen.getByText(/M · White · Qty 2 · LKR 2490\.00 each/i))
      .toBeInTheDocument();
  });

  it("does not show a form-data error when Strict Mode cancels the first load", async () => {
    let rejectCanceledRequest!: (reason?: unknown) => void;
    const canceledRequest = new Promise<never>((_resolve, reject) => {
      rejectCanceledRequest = reject;
    });
    mockedGetOrderCustomers
      .mockReturnValueOnce(canceledRequest)
      .mockResolvedValueOnce(customers);

    render(
      <StrictMode>
        <CreateOrderPage />
      </StrictMode>,
    );

    expect(await screen.findByRole("heading", { name: "Create customer order" }))
      .toBeInTheDocument();
    await act(async () => {
      rejectCanceledRequest(new DOMException("The request was aborted.", "AbortError"));
    });

    await waitFor(() => {
      expect(screen.queryByText("Order form data could not be loaded."))
        .not.toBeInTheDocument();
      expect(screen.getByRole("option", { name: /Asha Perera/ })).toBeInTheDocument();
    });
  });

  it("requires a selected customer and valid item before calling the API", async () => {
    const user = userEvent.setup();
    render(<CreateOrderPage />);
    await screen.findByRole("heading", { name: "Create customer order" });

    await user.click(screen.getByRole("button", { name: "Save customer order" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Please correct the highlighted order fields.",
    );
    expect(screen.getByText("Select an active customer before saving the order."))
      .toBeInTheDocument();
    expect(mockedCreateOrder).not.toHaveBeenCalled();
  });

  it("shows server product-availability errors on the exact order line", async () => {
    const user = userEvent.setup();
    mockedCreateOrder.mockRejectedValue({
      message: "The selected product variant is not available for a new order.",
      fields: {
        "items[0].variantId": "This product/size/color selection is no longer available. Choose an available garment variant.",
      },
    });
    render(<CreateOrderPage />);
    await screen.findByRole("heading", { name: "Create customer order" });

    await user.selectOptions(screen.getByLabelText("Active customer"), "51");
    await user.selectOptions(screen.getByLabelText("Product"), "61");
    await user.selectOptions(screen.getByLabelText("Size / color"), "81");
    await user.click(screen.getByRole("button", { name: "Save customer order" }));

    expect(await screen.findByText(/selection is no longer available/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Size / color")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByRole("alert")).toHaveTextContent(/product variant is not available for a new order/i);
  });

  it("supports customer search through the Sales Officer customer query", async () => {
    const user = userEvent.setup();
    mockedGetOrderCustomers
      .mockResolvedValueOnce(customers)
      .mockResolvedValueOnce(customers);
    render(<CreateOrderPage />);
    await screen.findByRole("heading", { name: "Create customer order" });

    await user.type(screen.getByLabelText("Find customer"), " Asha ");
    await user.click(screen.getByRole("button", { name: "Search customers" }));

    await waitFor(() => expect(mockedGetOrderCustomers).toHaveBeenLastCalledWith(" Asha "));
  });

  it("shows an empty state when Product Management has no selectable variants", async () => {
    mockedGetCatalogProducts.mockResolvedValue([]);
    render(<CreateOrderPage />);

    expect(await screen.findByRole("heading", { name: "No selectable garments" }))
      .toBeInTheDocument();
    expect(screen.getByText(/cannot be created until Product Management/i)).toBeInTheDocument();
  });
});

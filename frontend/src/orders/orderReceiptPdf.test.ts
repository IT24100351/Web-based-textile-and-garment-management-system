import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { CreateOrderResponse } from "../api/orders";
import { downloadOrderReceipt } from "./orderReceiptPdf";

const save = vi.fn();
const text = vi.fn();
const documentMock = {
  internal: {
    pageSize: {
      getHeight: () => 297,
      getWidth: () => 210,
    },
  },
  addImage: vi.fn(),
  addPage: vi.fn(),
  line: vi.fn(),
  rect: vi.fn(),
  roundedRect: vi.fn(),
  save,
  setDrawColor: vi.fn(),
  setFillColor: vi.fn(),
  setFont: vi.fn(),
  setFontSize: vi.fn(),
  setTextColor: vi.fn(),
  splitTextToSize: (value: string) => [value],
  text,
};

vi.mock("jspdf", () => ({
  jsPDF: vi.fn(function JsPdfMock() {
    return documentMock;
  }),
}));

const order: CreateOrderResponse = {
  message: "Customer order created successfully.",
  id: 91,
  orderNumber: "ORD-ABCDEF12345678901234",
  customerId: 51,
  customer: { id: 51, fullName: "Asha Perera", email: "asha@example.com" },
  status: "PENDING",
  createdAt: "2026-08-23T10:10:00Z",
  totalAmount: "4980.00",
  items: [{
    id: 101,
    productId: 61,
    variantId: 81,
    productName: "Classic Oxford Shirt",
    quantity: 2,
    selectedSize: "M",
    selectedColor: "White",
    unitPriceSnapshot: "2490.00",
    lineTotal: "4980.00",
  }],
};

describe("order receipt PDF", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("downloads a confirmation receipt using authoritative saved totals", async () => {
    await downloadOrderReceipt(order);

    expect(text).toHaveBeenCalledWith(
      expect.stringContaining(order.orderNumber),
      expect.any(Number),
      expect.any(Number),
    );
    expect(text).toHaveBeenCalledWith(
      "LKR 4980.00",
      expect.any(Number),
      expect.any(Number),
      expect.objectContaining({ align: "right" }),
    );
    expect(save).toHaveBeenCalledWith(
      "LankaWear-Order-Receipt-ORD-ABCDEF12345678901234.pdf",
    );
  });
});

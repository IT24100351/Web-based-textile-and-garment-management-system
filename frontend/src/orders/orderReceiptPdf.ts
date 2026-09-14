import { jsPDF } from "jspdf";

import type { CreateOrderResponse } from "../api/orders";

const teal = [11, 116, 112] as const;
const dark = [22, 32, 39] as const;
const muted = [92, 104, 116] as const;
const border = [214, 221, 226] as const;

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function statusLabel(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

async function loadBrandLogo() {
  try {
    const response = await fetch("/brand/lankawear-logo.png");
    if (!response.ok) return null;
    return new Uint8Array(await response.arrayBuffer());
  } catch {
    return null;
  }
}

function safeFilePart(value: string) {
  return value.replace(/[^A-Z0-9-]+/gi, "-");
}

export async function downloadOrderReceipt(order: CreateOrderResponse) {
  const document = new jsPDF({ format: "a4", unit: "mm" });
  const pageWidth = document.internal.pageSize.getWidth();
  const pageHeight = document.internal.pageSize.getHeight();
  const margin = 18;
  const contentWidth = pageWidth - margin * 2;

  document.setFillColor(...dark);
  document.rect(0, 0, pageWidth, 42, "F");
  const logo = await loadBrandLogo();
  if (logo) {
    try {
      document.addImage(logo, "PNG", margin, 8, 24, 24);
    } catch {
      // The receipt remains downloadable with the text brand if the image cannot be decoded.
    }
  }
  document.setTextColor(255, 255, 255);
  document.setFont("helvetica", "bold");
  document.setFontSize(18);
  document.text("LankaWear Apparel", logo ? 47 : margin, 17);
  document.setFont("helvetica", "normal");
  document.setFontSize(9);
  document.text("ORDER CONFIRMATION RECEIPT", logo ? 47 : margin, 24);
  document.text(`Confirmation code: ${order.orderNumber}`, logo ? 47 : margin, 30);

  let y = 54;
  document.setTextColor(...dark);
  document.setFont("helvetica", "bold");
  document.setFontSize(16);
  document.text("Order confirmed", margin, y);
  y += 7;
  document.setFont("helvetica", "normal");
  document.setFontSize(9.5);
  document.setTextColor(...muted);
  const confirmationText = document.splitTextToSize(
    "This receipt confirms that LankaWear Apparel recorded the order. Fulfilment remains subject to the current processing status shown below.",
    contentWidth,
  );
  document.text(confirmationText, margin, y);
  y += confirmationText.length * 5 + 5;

  document.setDrawColor(...border);
  document.setFillColor(246, 249, 249);
  document.roundedRect(margin, y, contentWidth, 32, 2, 2, "FD");
  document.setFontSize(8);
  document.setTextColor(...muted);
  document.text("CUSTOMER", margin + 5, y + 7);
  document.text("SUBMITTED", margin + 78, y + 7);
  document.text("STATUS", margin + 130, y + 7);
  document.setFont("helvetica", "bold");
  document.setFontSize(10);
  document.setTextColor(...dark);
  document.text(order.customer.fullName, margin + 5, y + 14, { maxWidth: 68 });
  document.setFont("helvetica", "normal");
  document.setFontSize(8.5);
  document.setTextColor(...muted);
  document.text(order.customer.email, margin + 5, y + 20, { maxWidth: 68 });
  document.setTextColor(...dark);
  document.text(formatDate(order.createdAt), margin + 78, y + 14, { maxWidth: 47 });
  document.setFont("helvetica", "bold");
  document.setTextColor(...teal);
  document.text(statusLabel(order.status), margin + 130, y + 14);
  y += 43;

  document.setFont("helvetica", "bold");
  document.setFontSize(12);
  document.setTextColor(...dark);
  document.text("Selected items", margin, y);
  y += 8;

  order.items.forEach((item, index) => {
    if (y > pageHeight - 55) {
      document.addPage();
      y = margin;
    }
    document.setDrawColor(...border);
    document.roundedRect(margin, y, contentWidth, 29, 2, 2, "S");
    document.setFont("helvetica", "bold");
    document.setFontSize(10);
    document.setTextColor(...dark);
    document.text(`${index + 1}. ${item.productName}`, margin + 5, y + 7, { maxWidth: 100 });
    document.setFont("helvetica", "normal");
    document.setFontSize(8.5);
    document.setTextColor(...muted);
    document.text(`Size: ${item.selectedSize}    Colour: ${item.selectedColor}    Quantity: ${item.quantity}`, margin + 5, y + 14);
    document.text(`Unit price: LKR ${item.unitPriceSnapshot}`, margin + 5, y + 21);
    document.setFont("helvetica", "bold");
    document.setTextColor(...dark);
    document.text(`LKR ${item.lineTotal}`, pageWidth - margin - 5, y + 21, { align: "right" });
    y += 35;
  });

  if (y > pageHeight - 48) {
    document.addPage();
    y = margin;
  }

  document.setDrawColor(...teal);
  document.line(pageWidth - margin - 70, y, pageWidth - margin, y);
  y += 8;
  document.setFont("helvetica", "bold");
  document.setFontSize(12);
  document.setTextColor(...dark);
  document.text("Order total", pageWidth - margin - 70, y);
  document.setTextColor(...teal);
  document.text(`LKR ${order.totalAmount}`, pageWidth - margin, y, { align: "right" });

  y += 16;
  document.setFont("helvetica", "normal");
  document.setFontSize(8.5);
  document.setTextColor(...muted);
  const note = document.splitTextToSize(
    "Important: This is an order-submission receipt, not proof of payment or a tax invoice. Use the confirmation code when contacting LankaWear Apparel or tracking the order.",
    contentWidth,
  );
  document.text(note, margin, y);

  document.setDrawColor(...border);
  document.line(margin, pageHeight - 18, pageWidth - margin, pageHeight - 18);
  document.setFontSize(8);
  document.text(`Order ID #${order.id}  |  ${order.orderNumber}`, margin, pageHeight - 12);
  document.text("Generated by LankaWear Apparel", pageWidth - margin, pageHeight - 12, { align: "right" });

  document.save(`LankaWear-Order-Receipt-${safeFilePart(order.orderNumber)}.pdf`);
}

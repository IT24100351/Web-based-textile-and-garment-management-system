import { Badge, type BadgeTone } from "./Badge";

const successStatuses = new Set(["ACTIVE", "AVAILABLE", "COMPLETED", "DELIVERED", "PAID", "CONFIRMED"]);
const warningStatuses = new Set(["PENDING", "SCHEDULED", "PARTIALLY_PAID", "LOW_STOCK"]);
const dangerStatuses = new Set(["CANCELLED", "FAILED"]);
const infoStatuses = new Set(["IN_PROGRESS", "IN_PRODUCTION", "READY_FOR_DELIVERY", "OUT_FOR_DELIVERY"]);

function toneForStatus(status: string): BadgeTone {
  if (successStatuses.has(status)) return "success";
  if (warningStatuses.has(status)) return "warning";
  if (dangerStatuses.has(status)) return "danger";
  if (infoStatuses.has(status)) return "info";
  return "neutral";
}

function formatStatus(status: string) {
  if (status === "READY_FOR_DELIVERY") return "Ready For Delivery";
  return status
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function StatusBadge({ status, label }: { status: string; label?: string }) {
  return <Badge className="motion-status-badge" tone={toneForStatus(status)}>{label ?? formatStatus(status)}</Badge>;
}

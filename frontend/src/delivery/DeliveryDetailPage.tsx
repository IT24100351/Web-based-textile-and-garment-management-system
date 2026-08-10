import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import {
  getDeliveryApiError,
  getDeliveryRecord,
  updateDeliveryStatus,
  type DeliveryStatus,
  type StaffDeliveryRecord,
} from "../api/deliveries";
import { ErrorState, LoadingState } from "../components/AppStates";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  deliveryOrderSelectionPagePath,
  deliveryRecordsPagePath,
} from "../navigation/navigation";

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function DeliveryDetailPage() {
  const { deliveryId: deliveryIdParam } = useParams<{ deliveryId: string }>();
  const deliveryId = Number(deliveryIdParam);
  const validDeliveryId = Number.isInteger(deliveryId) && deliveryId > 0;
  const [record, setRecord] = useState<StaffDeliveryRecord | null>(null);
  const [isLoading, setIsLoading] = useState(validDeliveryId);
  const [error, setError] = useState<string | null>(null);
  const [isUpdating, setIsUpdating] = useState(false);
  const [isCancelConfirmOpen, setIsCancelConfirmOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!validDeliveryId) return;
    const controller = new AbortController();
    setIsLoading(true);
    setError(null);
    getDeliveryRecord(deliveryId, controller.signal)
      .then(setRecord)
      .catch((caught: unknown) => {
        if (controller.signal.aborted) return;
        setError(getDeliveryApiError(caught, "Delivery record could not be loaded.").message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [deliveryIdParam, deliveryId, validDeliveryId]);

  async function changeStatus(status: DeliveryStatus) {
    if (!record) return;
    if (status === "CANCELLED") {
      setIsCancelConfirmOpen(true);
      return;
    }
    await updateStatus(status);
  }

  async function updateStatus(status: DeliveryStatus) {
    if (!record) return;
    setIsUpdating(true);
    setError(null);
    setMessage(null);
    try {
      const response = await updateDeliveryStatus(record.delivery.id, status);
      const refreshed = await getDeliveryRecord(record.delivery.id);
      setRecord(refreshed);
      setMessage(status === "CANCELLED"
        ? "Delivery cancelled safely. The historical record is preserved and the order can be scheduled again."
        : response.message);
      setIsCancelConfirmOpen(false);
    } catch (caught: unknown) {
      setError(getDeliveryApiError(caught, "Delivery status could not be updated.").message);
    } finally {
      setIsUpdating(false);
    }
  }

  if (!validDeliveryId) {
    return <section className="mx-auto max-w-5xl px-4 py-10"><ErrorState message="Select a valid delivery record." /></section>;
  }
  if (isLoading) {
    return <section className="mx-auto max-w-5xl px-4 py-10"><LoadingState message="Loading delivery record…" /></section>;
  }
  if (!record) {
    return <section className="mx-auto max-w-5xl px-4 py-10"><ErrorState message={error ?? "Delivery record was not found."} /></section>;
  }

  const { delivery } = record;

  return (
    <section className="mx-auto w-full max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="rounded-3xl border border-border bg-surface/70 p-6 app-shadow">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Delivery record</p>
            <h1 className="mt-2 text-3xl font-bold text-foreground">{delivery.deliveryNumber}</h1>
            <p className="mt-2 text-muted">Linked to {record.orderNumber} · Delivery ID #{delivery.id}</p>
          </div>
          <StatusBadge label={delivery.status.replaceAll("_", " ")} status={delivery.status} />
        </div>

        {error ? <p className="mt-5 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">{error}</p> : null}
        {message ? <p className="mt-5 rounded-xl border border-success-border bg-success-soft p-4 text-sm text-success" role="status">{message}</p> : null}

        <div className="mt-7 grid gap-5 lg:grid-cols-2">
          <section className="rounded-2xl border border-border bg-background/45 p-5">
            <h2 className="text-lg font-bold text-foreground">Delivery details</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <div><dt className="text-muted">Scheduled</dt><dd className="mt-1 text-foreground">{formatDateTime(delivery.scheduledAt)}</dd></div>
              <div><dt className="text-muted">Address</dt><dd className="mt-1 text-foreground">{delivery.deliveryAddress}</dd></div>
              <div><dt className="text-muted">Notes</dt><dd className="mt-1 text-foreground">{delivery.deliveryNotes ?? "No delivery notes"}</dd></div>
              <div><dt className="text-muted">Last updated</dt><dd className="mt-1 text-foreground">{formatDateTime(delivery.updatedAt)}</dd></div>
            </dl>
          </section>

          <section className="rounded-2xl border border-border bg-background/45 p-5">
            <h2 className="text-lg font-bold text-foreground">Order Management link</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <div><dt className="text-muted">Order</dt><dd className="mt-1 text-foreground">{record.orderNumber} · #{delivery.orderId}</dd></div>
              <div><dt className="text-muted">Customer</dt><dd className="mt-1 text-foreground">{record.customerName} · {record.customerEmail}</dd></div>
              <div><dt className="text-muted">Order status</dt><dd className="mt-1 text-foreground">{record.orderStatus.replaceAll("_", " ")}</dd></div>
              <div><dt className="text-muted">Order total</dt><dd className="mt-1 text-foreground">LKR {record.orderTotal}</dd></div>
            </dl>
          </section>
        </div>

        <section className="mt-6 rounded-2xl border border-border bg-background/45 p-5">
          <h2 className="text-lg font-bold text-foreground">Ordered items</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {record.items.map((item) => (
              <div className="rounded-xl border border-border p-4" key={item.orderItemId}>
                <p className="font-semibold text-foreground">{item.productName}</p>
                <p className="mt-1 text-sm text-muted">Product #{item.productId} · Variant #{item.variantId}</p>
                <p className="mt-1 text-sm text-foreground-muted">{item.selectedSize} · {item.selectedColor} · Quantity {item.quantity}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="mt-6 rounded-2xl border border-border bg-background/45 p-5" aria-labelledby="delivery-actions-heading">
          <h2 className="text-lg font-bold text-foreground" id="delivery-actions-heading">Delivery progress and safe removal</h2>
          {delivery.allowedStatusTransitions.length > 0 ? (
            <div className="mt-4 flex flex-wrap gap-3">
              {delivery.allowedStatusTransitions.map((status) => (
                <button
                  className={status === "CANCELLED"
                    ? "rounded-xl border border-danger-border px-4 py-2.5 font-semibold text-danger disabled:opacity-50"
                    : "rounded-xl bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50"}
                  disabled={isUpdating}
                  key={status}
                  onClick={() => void changeStatus(status)}
                  type="button"
                >
                  {status === "OUT_FOR_DELIVERY" ? "Mark out for delivery" : status === "DELIVERED" ? "Mark delivered" : "Cancel incorrect delivery"}
                </button>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-muted">This historical record is terminal and cannot be changed.</p>
          )}
          {delivery.status === "CANCELLED" ? (
            <div className="mt-4 rounded-xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">
              This record was cancelled instead of deleted. The Order remains preserved and can be selected for a corrected replacement Delivery.
              <Link className="ml-2 font-semibold text-primary" to={deliveryOrderSelectionPagePath}>Prepare replacement</Link>
            </div>
          ) : null}
        </section>

        <Link className="mt-6 inline-block font-semibold text-primary hover:text-primary" to={deliveryRecordsPagePath}>Back to delivery records</Link>
      </div>
      <ConfirmDialog
        cancelLabel="Keep delivery scheduled"
        confirmLabel="Cancel delivery"
        description="The delivery record will be preserved as history and the linked order can be scheduled again."
        isBusy={isUpdating}
        isOpen={isCancelConfirmOpen}
        onCancel={() => setIsCancelConfirmOpen(false)}
        onConfirm={() => void updateStatus("CANCELLED")}
        title="Cancel this delivery?"
      />
    </section>
  );
}

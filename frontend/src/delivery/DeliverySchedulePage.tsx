import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";

import {
  getDeliveryApiError,
  scheduleDelivery,
  updateDeliveryStatus,
  validateDeliveryOrder,
  type DeliveryEligibleOrder,
  type DeliveryRecord,
  type DeliveryStatus,
} from "../api/deliveries";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import { StatusBadge } from "../components/ui/StatusBadge";
import { deliveryOrderSelectionPagePath } from "../navigation/navigation";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

interface FormErrors {
  scheduledAt?: string;
  deliveryAddress?: string;
  deliveryNotes?: string;
}

function money(value: string) {
  const amount = Number(value);
  return Number.isFinite(amount)
    ? new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(amount)
    : `LKR ${value}`;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function DeliverySchedulePage() {
  const { orderId: orderIdParam } = useParams<{ orderId: string }>();
  const orderId = Number(orderIdParam);
  const validOrderId = Number.isInteger(orderId) && orderId > 0;
  const [order, setOrder] = useState<DeliveryEligibleOrder | null>(null);
  const [scheduledDate, setScheduledDate] = useState("");
  const [scheduledTime, setScheduledTime] = useState("");
  const [deliveryAddress, setDeliveryAddress] = useState("");
  const [deliveryNotes, setDeliveryNotes] = useState("");
  const [errors, setErrors] = useState<FormErrors>({});
  const [isLoading, setIsLoading] = useState(validOrderId);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState<DeliveryRecord | null>(null);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [isCancelConfirmOpen, setIsCancelConfirmOpen] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  async function loadSelectedOrder(signal?: AbortSignal) {
    if (!validOrderId) return;
    setIsLoading(true);
    setLoadError(null);
    try {
      setOrder(await validateDeliveryOrder(orderId, signal));
    } catch (error: unknown) {
      if (signal?.aborted) return;
      const apiError = getDeliveryApiError(
        error,
        "The selected order could not be revalidated for delivery scheduling.",
      );
      setLoadError(apiError.fields.orderId ?? apiError.message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    if (!validOrderId) return;
    const controller = new AbortController();
    void loadSelectedOrder(controller.signal);
    return () => controller.abort();
    // The route order ID is stable for the lifetime of this page instance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderIdParam]);

  const scheduledAt = useMemo(() => {
    if (!scheduledDate || !scheduledTime) return null;
    const date = new Date(`${scheduledDate}T${scheduledTime}`);
    return Number.isNaN(date.getTime()) ? null : date;
  }, [scheduledDate, scheduledTime]);

  function validateForm() {
    const next: FormErrors = {};
    if (!scheduledDate || !scheduledTime || !scheduledAt) {
      next.scheduledAt = "Enter a valid delivery date and time.";
    } else if (scheduledAt.getTime() <= Date.now()) {
      next.scheduledAt = "Delivery date and time must be in the future.";
    }

    const address = deliveryAddress.trim().replace(/\s+/g, " ");
    if (!address) {
      next.deliveryAddress = "Enter the delivery address.";
    } else if (address.length > 500) {
      next.deliveryAddress = "Delivery address must be 500 characters or fewer.";
    }

    const notes = deliveryNotes.trim().replace(/\s+/g, " ");
    if (notes.length > 1000) {
      next.deliveryNotes = "Delivery notes must be 1000 characters or fewer.";
    }
    setErrors(next);
    return { valid: Object.keys(next).length === 0, address, notes };
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!order || saved) return;
    setSaveError(null);
    const validation = validateForm();
    if (!validation.valid || !scheduledAt) return;

    setIsSaving(true);
    try {
      const response = await scheduleDelivery({
        orderId: order.orderId,
        scheduledAt: scheduledAt.toISOString(),
        deliveryAddress: validation.address,
        deliveryNotes: validation.notes || null,
      });
      setSaved(response.delivery);
      setErrors({});
    } catch (error: unknown) {
      const apiError = getDeliveryApiError(error, "Delivery schedule could not be saved.");
      setErrors({
        scheduledAt: apiError.fields.scheduledAt,
        deliveryAddress: apiError.fields.deliveryAddress,
        deliveryNotes: apiError.fields.deliveryNotes,
      });
      setSaveError(apiError.fields.orderId ?? apiError.message);
    } finally {
      setIsSaving(false);
    }
  }

  async function changeStatus(status: DeliveryStatus) {
    if (!saved) return;
    if (status === "CANCELLED") {
      setIsCancelConfirmOpen(true);
      return;
    }
    await updateStatus(status);
  }

  async function updateStatus(status: DeliveryStatus) {
    if (!saved) return;
    setIsUpdatingStatus(true);
    setStatusError(null);
    setStatusMessage(null);
    try {
      const response = await updateDeliveryStatus(saved.id, status);
      setSaved(response.delivery);
      setStatusMessage(status === "CANCELLED"
        ? "Delivery cancelled safely. The record is preserved and the order can be scheduled again."
        : response.message);
      setIsCancelConfirmOpen(false);
    } catch (error: unknown) {
      const apiError = getDeliveryApiError(error, "Delivery status could not be updated.");
      setStatusError(apiError.fields.status ?? apiError.message);
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  if (!validOrderId) {
    return (
      <div className="mx-auto max-w-4xl p-6 sm:p-10">
        <EmptyState
          title="Invalid delivery order"
          message="Choose a delivery-ready order before opening the schedule form."
        />
        <Link className="mt-5 inline-block text-sm font-semibold text-primary hover:text-primary" to={deliveryOrderSelectionPagePath}>
          Back to delivery-ready orders
        </Link>
      </div>
    );
  }

  if (isLoading) {
    return <div className="mx-auto max-w-5xl p-6 sm:p-10"><LoadingState title="Loading selected order…" message="Rechecking Delivery readiness before scheduling." /></div>;
  }

  if (loadError || !order) {
    return (
      <div className="mx-auto max-w-5xl p-6 sm:p-10">
        <ErrorState
          title="Order cannot be scheduled"
          message={loadError ?? "The selected order is not available for delivery scheduling."}
          action={<Link className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" to={deliveryOrderSelectionPagePath}>Choose another order</Link>}
        />
      </div>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Delivery Management</p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Schedule product delivery</h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Schedule the selected Order Management record. The server revalidates delivery readiness and the one-active-delivery-per-order rule again when saving.
        </p>

        <section className="mt-8 rounded-3xl border border-border bg-surface/70 p-6" aria-labelledby="selected-order-heading">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-success">Selected delivery-ready order</p>
          <h2 className="mt-2 text-2xl font-bold text-foreground" id="selected-order-heading">{order.orderNumber}</h2>
          <p className="mt-2 text-foreground-muted">{order.customerName} · {order.customerEmail}</p>
          <p className="mt-1 text-sm text-muted">Order ID #{order.orderId} · Customer ID #{order.customerId} · {order.itemCount} item{order.itemCount === 1 ? "" : "s"} · {money(order.totalAmount)}</p>
          <div className="mt-5 grid gap-3 md:grid-cols-2">
            {order.items.map((item) => (
              <div className="rounded-2xl border border-border bg-background/50 p-4" key={item.orderItemId}>
                <p className="font-semibold text-foreground">Order item #{item.orderItemId}</p>
                <p className="mt-2 text-sm text-foreground-muted">Product #{item.productId} · Variant #{item.variantId}</p>
                <p className="mt-1 text-sm text-muted">{item.selectedSize} · {item.selectedColor} · Quantity {item.quantity}</p>
              </div>
            ))}
          </div>
        </section>

        {saved ? (
          <section className="mt-8 rounded-3xl border border-success-border bg-success-soft p-6" aria-labelledby="delivery-saved-heading">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-success">Delivery scheduled</p>
            <h2 className="mt-2 text-2xl font-bold text-foreground" id="delivery-saved-heading">{saved.deliveryNumber}</h2>
            <dl className="mt-5 grid gap-4 sm:grid-cols-2">
              <div><dt className="text-sm text-muted">Status</dt><dd className="mt-1"><StatusBadge label={saved.status.replaceAll("_", " ")} status={saved.status} /></dd></div>
              <div><dt className="text-sm text-muted">Scheduled for</dt><dd className="mt-1 font-semibold text-foreground">{formatDateTime(saved.scheduledAt)}</dd></div>
              <div className="sm:col-span-2"><dt className="text-sm text-muted">Delivery address</dt><dd className="mt-1 text-foreground">{saved.deliveryAddress}</dd></div>
              {saved.deliveryNotes ? <div className="sm:col-span-2"><dt className="text-sm text-muted">Notes</dt><dd className="mt-1 text-foreground">{saved.deliveryNotes}</dd></div> : null}
            </dl>
            <p className="mt-5 text-sm text-success">The Delivery record is linked to Order #{saved.orderId}. Delivery progress is controlled by the server lifecycle.</p>

            <div className="mt-6 rounded-2xl border border-border bg-background/50 p-5" aria-labelledby="delivery-progress-heading">
              <h3 className="text-lg font-bold text-foreground" id="delivery-progress-heading">Update delivery progress</h3>
              {saved.allowedStatusTransitions.length > 0 ? (
                <div className="mt-4 flex flex-wrap gap-3">
                  {saved.allowedStatusTransitions.map((status) => (
                    <button
                      className={status === "CANCELLED" ? "rounded-xl border border-danger-border px-4 py-2.5 font-semibold text-danger disabled:opacity-50" : "rounded-xl bg-primary px-4 py-2.5 font-semibold text-primary-foreground disabled:opacity-50"}
                      disabled={isUpdatingStatus}
                      key={status}
                      onClick={() => void changeStatus(status)}
                      type="button"
                    >
                      {isUpdatingStatus ? "Updating…" : status === "OUT_FOR_DELIVERY" ? "Mark out for delivery" : status === "DELIVERED" ? "Mark delivered" : "Cancel incorrect delivery"}
                    </button>
                  ))}
                </div>
              ) : (
                <p className="mt-3 text-sm text-muted">This delivery is in a terminal status and has no permitted next transition.</p>
              )}
              {saved.status === "OUT_FOR_DELIVERY" ? <p className="mt-3 text-sm text-muted">Order Management remains READY FOR DELIVERY while dispatch is in progress.</p> : null}
              {saved.status === "DELIVERED" ? <p className="mt-3 text-sm text-success">Order Management has been synchronized to COMPLETED.</p> : null}
              {statusError ? <p className="mt-3 text-sm text-danger" role="alert">{statusError}</p> : null}
              {statusMessage ? <p className="mt-3 text-sm text-success" role="status">{statusMessage}</p> : null}
            </div>

            <Link className="mt-5 inline-block text-sm font-semibold text-primary hover:text-primary" to={deliveryOrderSelectionPagePath}>Back to delivery-ready orders</Link>
          </section>
        ) : (
          <form className="mt-8 rounded-3xl border border-border bg-surface/70 p-6" onSubmit={submit} noValidate>
            <h2 className="text-xl font-bold text-foreground">Delivery schedule</h2>
            <div className="mt-6 grid gap-5 sm:grid-cols-2">
              <label className="block text-sm font-medium text-foreground">Delivery date
                <input className={inputClassName} min={new Date().toISOString().slice(0, 10)} onChange={(event) => { setScheduledDate(event.target.value); setErrors((current) => ({ ...current, scheduledAt: undefined })); setSaveError(null); }} type="date" value={scheduledDate} />
              </label>
              <label className="block text-sm font-medium text-foreground">Delivery time
                <input className={inputClassName} onChange={(event) => { setScheduledTime(event.target.value); setErrors((current) => ({ ...current, scheduledAt: undefined })); setSaveError(null); }} type="time" value={scheduledTime} />
              </label>
            </div>
            <p className="mt-2 text-sm text-muted">Single-branch scheduling rule: choose a time at least 60 minutes before or after every existing active delivery.</p>
            {errors.scheduledAt ? <p className="mt-2 text-sm text-danger" role="alert">{errors.scheduledAt}</p> : null}

            <label className="mt-5 block text-sm font-medium text-foreground">Delivery address
              <textarea className={inputClassName} maxLength={500} onChange={(event) => setDeliveryAddress(event.target.value)} rows={4} value={deliveryAddress} />
            </label>
            {errors.deliveryAddress ? <p className="mt-2 text-sm text-danger" role="alert">{errors.deliveryAddress}</p> : null}

            <label className="mt-5 block text-sm font-medium text-foreground">Delivery notes (optional)
              <textarea className={inputClassName} maxLength={1000} onChange={(event) => setDeliveryNotes(event.target.value)} rows={3} value={deliveryNotes} />
            </label>
            {errors.deliveryNotes ? <p className="mt-2 text-sm text-danger" role="alert">{errors.deliveryNotes}</p> : null}

            {saveError ? <p className="mt-5 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">{saveError}</p> : null}

            <div className="mt-6 flex flex-wrap gap-3">
              <button className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:opacity-50" disabled={isSaving} type="submit">
                {isSaving ? "Saving schedule…" : "Save delivery schedule"}
              </button>
              <Link className="rounded-xl border border-border px-6 py-3 font-semibold text-foreground hover:border-border" to={deliveryOrderSelectionPagePath}>Choose another order</Link>
            </div>
          </form>
        )}
      </div>
      <ConfirmDialog
        cancelLabel="Keep delivery scheduled"
        confirmLabel="Cancel delivery"
        description="The record will be preserved and the linked order can be scheduled again."
        isBusy={isUpdatingStatus}
        isOpen={isCancelConfirmOpen}
        onCancel={() => setIsCancelConfirmOpen(false)}
        onConfirm={() => void updateStatus("CANCELLED")}
        title="Cancel this delivery?"
      />
    </section>
  );
}

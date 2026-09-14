import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import {
  getMyOrders,
  getMyOrderTracking,
  getOrderApiError,
  type CustomerOrderTracking,
  type OrderStatus,
} from "../api/orders";
import {
  getDeliveryApiError,
  getMyDeliveryTracking,
  type CustomerDeliveryTracking,
  type DeliveryStatus,
} from "../api/deliveries";
import { ErrorState, LoadingState } from "../components/AppStates";
import {
  customerOrderHistoryPagePath,
  customerOrderTrackingPagePath,
  getCustomerOrderTrackingPagePath,
  parseOrderPageId,
} from "../navigation/navigation";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

const orderNumberPattern = /^[A-Z0-9]+(?:-[A-Z0-9]+)+$/i;

function statusLabel(status: OrderStatus | DeliveryStatus) {
  return status.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function money(value: string) {
  return `LKR ${value}`;
}

export function CustomerOrderTrackingPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const parsedOrderId = orderId === undefined ? undefined : parseOrderPageId(orderId);
  const [lookupId, setLookupId] = useState(orderId ?? "");
  const [tracking, setTracking] = useState<CustomerOrderTracking | null>(null);
  const [deliveryTracking, setDeliveryTracking] = useState<CustomerDeliveryTracking | null>(null);
  const [isLoading, setIsLoading] = useState(parsedOrderId !== undefined && parsedOrderId !== null);
  const [isResolving, setIsResolving] = useState(false);
  const [error, setError] = useState<string | null>(
    parsedOrderId === null ? "Enter a valid order ID or order number." : null,
  );

  async function load(id: number, signal?: AbortSignal) {
    setIsLoading(true);
    setError(null);
    setTracking(null);
    setDeliveryTracking(null);
    try {
      const [orderResult, deliveryResult] = await Promise.all([
        getMyOrderTracking(id, signal),
        getMyDeliveryTracking(id, signal),
      ]);
      setTracking(orderResult);
      setDeliveryTracking(deliveryResult);
    } catch (loadError: unknown) {
      if (signal?.aborted) return;
      const orderError = getOrderApiError(
        loadError,
        "Order tracking could not be loaded. Please try again.",
      );
      const deliveryError = getDeliveryApiError(
        loadError,
        "Delivery tracking could not be loaded. Please try again.",
      );
      const status = orderError.status ?? deliveryError.status;
      const message = orderError.code || orderError.status
        ? orderError.message
        : deliveryError.message;
      setError(status === 404
        ? "No order with that ID or order number is available in your account. Check your order history and try again."
        : message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    if (parsedOrderId === undefined || parsedOrderId === null) {
      return undefined;
    }
    const controller = new AbortController();
    void load(parsedOrderId, controller.signal);
    return () => controller.abort();
  }, [parsedOrderId]);

  async function submitLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedLookup = lookupId.trim();
    const parsed = parseOrderPageId(normalizedLookup);
    if (parsed !== null) {
      navigate(getCustomerOrderTrackingPagePath(parsed));
      return;
    }

    if (!orderNumberPattern.test(normalizedLookup)) {
      setError("Enter a positive order ID (for example, 15) or a valid order number (for example, ORD-ABC123).");
      setTracking(null);
      setDeliveryTracking(null);
      return;
    }

    setIsResolving(true);
    setError(null);
    try {
      const ownedOrders = await getMyOrders();
      const matchingOrder = ownedOrders.find(
        (order) => order.orderNumber.toLocaleUpperCase() === normalizedLookup.toLocaleUpperCase(),
      );
      if (!matchingOrder) {
        setError("No order with that order number is available in your account. Check your order history and try again.");
        setTracking(null);
        setDeliveryTracking(null);
        return;
      }
      navigate(getCustomerOrderTrackingPagePath(matchingOrder.id));
    } catch (lookupError: unknown) {
      setError(getOrderApiError(
        lookupError,
        "Your order number could not be checked. Please try again.",
      ).message);
    } finally {
      setIsResolving(false);
    }
  }

  const lookup = (
    <form
      className="rounded-3xl border border-border bg-surface/70 p-6"
      onSubmit={submitLookup}
    >
      <label className="text-sm font-medium text-foreground">
        Order ID or order number
        <input
          className={inputClassName}
          autoCapitalize="characters"
          disabled={isResolving}
          maxLength={64}
          onChange={(event) => setLookupId(event.target.value)}
          placeholder="Example: 15 or ORD-ABC123"
          value={lookupId}
        />
      </label>
      <div className="mt-4 flex flex-wrap gap-3">
        <button
          className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover"
          disabled={isResolving}
          type="submit"
        >
          {isResolving ? "Finding order…" : "Track order"}
        </button>
        <Link
          className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground"
          to={customerOrderHistoryPagePath}
        >
          Find ID in order history
        </Link>
      </div>
    </form>
  );

  if (parsedOrderId === undefined) {
    return (
      <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <div className="mx-auto max-w-4xl">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Order Management</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Track an order</h1>
          <p className="mt-3 max-w-3xl leading-7 text-muted">
            Enter either the numeric ID or the visible order number from your own account to see its latest Order Management progress and any linked Delivery Management schedule/status.
          </p>
          <div className="mt-8">{lookup}</div>
          {error && <p className="mt-4 text-sm text-danger" role="alert">{error}</p>}
        </div>
      </section>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <Link className="text-sm font-semibold text-primary" to={customerOrderTrackingPagePath}>← Track another order</Link>
        <div className="mt-5">
          {isLoading ? (
            <LoadingState
              title="Loading order tracking…"
              message="Reading the latest ownership-protected Order and Delivery Management tracking information."
            />
          ) : error || !tracking ? (
            <>
              <ErrorState
                title="Order tracking unavailable"
                message={error ?? "No tracking information is available for this order."}
                action={parsedOrderId !== null ? (
                  <button
                    className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground"
                    onClick={() => void load(parsedOrderId)}
                    type="button"
                  >
                    Retry
                  </button>
                ) : undefined}
              />
              <div className="mt-5">{lookup}</div>
            </>
          ) : (
            <div className="rounded-3xl border border-border bg-surface/70 p-7 sm:p-9">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Customer order tracking</p>
                  <h1 className="mt-3 text-3xl font-bold text-foreground">{tracking.orderNumber}</h1>
                  <p className="mt-2 text-sm text-muted">Order ID #{tracking.orderId}</p>
                </div>
                <span className="rounded-full border border-primary bg-primary-soft px-4 py-2 text-sm font-semibold text-primary">
                  {statusLabel(tracking.currentStatus)}
                </span>
              </div>

              <dl className="mt-7 grid gap-4 rounded-2xl border border-border bg-background/50 p-5 sm:grid-cols-2 lg:grid-cols-4">
                <div><dt className="text-xs uppercase text-muted">Placed</dt><dd className="mt-1 text-foreground">{formatDate(tracking.placedAt)}</dd></div>
                <div><dt className="text-xs uppercase text-muted">Last updated</dt><dd className="mt-1 text-foreground">{formatDate(tracking.lastUpdatedAt)}</dd></div>
                <div><dt className="text-xs uppercase text-muted">Items</dt><dd className="mt-1 font-semibold text-foreground">{tracking.itemCount}</dd></div>
                <div><dt className="text-xs uppercase text-muted">Order total</dt><dd className="mt-1 font-semibold text-foreground">{money(tracking.totalAmount)}</dd></div>
              </dl>

              <section className="mt-7 rounded-2xl border border-border bg-background/50 p-5" aria-labelledby="tracking-progress-heading">
                <h2 className="text-xl font-bold text-foreground" id="tracking-progress-heading">Order progress</h2>
                <p className="mt-2 text-sm text-muted">
                  Current Order Management status: <span className="font-semibold text-primary">{statusLabel(tracking.currentStatus)}</span>
                </p>
                {tracking.orderHistory.length === 0 ? (
                  <div className="mt-4 rounded-xl border border-border bg-surface/60 p-4">
                    <p className="font-medium text-foreground">Order placed</p>
                    <p className="mt-1 text-sm text-muted">No later status transition has been recorded yet.</p>
                  </div>
                ) : (
                  <ol className="mt-4 space-y-3">
                    <li className="rounded-xl border border-border bg-surface/60 p-4">
                      <p className="font-medium text-foreground">Order placed</p>
                      <p className="mt-1 text-xs text-muted">{formatDate(tracking.placedAt)}</p>
                    </li>
                    {tracking.orderHistory.map((entry) => (
                      <li className="motion-record rounded-xl border border-border bg-surface/60 p-4" key={entry.id}>
                        <p className="font-medium text-foreground">{statusLabel(entry.fromStatus)} → {statusLabel(entry.toStatus)}</p>
                        <p className="mt-1 text-xs text-muted">{formatDate(entry.changedAt)}</p>
                      </li>
                    ))}
                  </ol>
                )}
              </section>

              <section className="mt-7 rounded-2xl border border-border bg-background/50 p-5" aria-labelledby="delivery-progress-heading">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 className="text-xl font-bold text-foreground" id="delivery-progress-heading">Delivery progress</h2>
                    <p className="mt-2 text-sm text-muted">
                      Delivery information comes from the Delivery Management record linked to this owned order.
                    </p>
                  </div>
                  {deliveryTracking?.delivery && (
                    <span className="rounded-full border border-info-border bg-info-soft px-3 py-1.5 text-sm font-semibold text-info">
                      {statusLabel(deliveryTracking.delivery.status)}
                    </span>
                  )}
                </div>

                {!deliveryTracking?.hasDelivery || !deliveryTracking.delivery ? (
                  <div className="mt-4 rounded-xl border border-border bg-surface/60 p-4">
                    <p className="font-medium text-foreground">Delivery not scheduled yet</p>
                    <p className="mt-1 text-sm text-muted">
                      No Delivery Management record is linked to this order yet. When the Sales Officer schedules delivery, its date, time and progress will appear here.
                    </p>
                  </div>
                ) : (
                  <dl className="mt-4 grid gap-4 rounded-xl border border-border bg-surface/60 p-4 sm:grid-cols-2">
                    <div>
                      <dt className="text-xs uppercase text-muted">Delivery number</dt>
                      <dd className="mt-1 font-semibold text-foreground">{deliveryTracking.delivery.deliveryNumber}</dd>
                    </div>
                    <div>
                      <dt className="text-xs uppercase text-muted">Scheduled date & time</dt>
                      <dd className="mt-1 text-foreground">{formatDate(deliveryTracking.delivery.scheduledAt)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs uppercase text-muted">Current delivery status</dt>
                      <dd className="mt-1 font-semibold text-info">{statusLabel(deliveryTracking.delivery.status)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs uppercase text-muted">Delivery last updated</dt>
                      <dd className="mt-1 text-foreground">{formatDate(deliveryTracking.delivery.lastUpdatedAt)}</dd>
                    </div>
                  </dl>
                )}
              </section>

              <div className="mt-7 flex flex-wrap gap-3">
                <Link className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" to={customerOrderHistoryPagePath}>View order history</Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

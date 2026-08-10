import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  getDeliveryApiError,
  getDeliveryEligibleOrders,
  validateDeliveryOrder,
  type DeliveryEligibleOrder,
} from "../api/deliveries";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { getDeliverySchedulePagePath } from "../navigation/navigation";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

function money(value: string) {
  const amount = Number(value);
  return Number.isFinite(amount)
    ? new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(amount)
    : `LKR ${value}`;
}

export function DeliveryOrderSelectionPage() {
  const [orders, setOrders] = useState<DeliveryEligibleOrder[]>([]);
  const [search, setSearch] = useState("");
  const [appliedSearch, setAppliedSearch] = useState("");
  const [selected, setSelected] = useState<DeliveryEligibleOrder | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectionError, setSelectionError] = useState<string | null>(null);
  const [isSelecting, setIsSelecting] = useState<number | null>(null);

  async function loadOrders(nextSearch = appliedSearch, signal?: AbortSignal) {
    setIsLoading(true);
    setLoadError(null);
    try {
      const result = await getDeliveryEligibleOrders(nextSearch, signal);
      setOrders(result);
    } catch (error: unknown) {
      if (signal?.aborted) return;
      setLoadError(getDeliveryApiError(
        error,
        "Delivery-ready customer orders could not be loaded. Please try again.",
      ).message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void loadOrders("", controller.signal);
    return () => controller.abort();
    // Initial load intentionally runs once; searches are submitted explicitly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = search.trim();
    setAppliedSearch(normalized);
    setSelected(null);
    setSelectionError(null);
    await loadOrders(normalized);
  }

  async function selectOrder(orderId: number) {
    setSelectionError(null);
    setSelected(null);
    setIsSelecting(orderId);
    try {
      const revalidated = await validateDeliveryOrder(orderId);
      setSelected(revalidated);
    } catch (error: unknown) {
      const apiError = getDeliveryApiError(
        error,
        "The selected order could not be validated for delivery. Please refresh and try again.",
      );
      const nextSelectionError = apiError.fields.orderId ?? apiError.message;
      await loadOrders(appliedSearch);
      setSelectionError(nextSelectionError);
    } finally {
      setIsSelecting(null);
    }
  }

  if (isLoading && orders.length === 0) {
    return (
      <div className="mx-auto max-w-6xl p-6 sm:p-10">
        <LoadingState
          title="Loading delivery-ready orders…"
          message="Checking Order Management readiness and existing Delivery records."
        />
      </div>
    );
  }

  if (loadError && orders.length === 0) {
    return (
      <div className="mx-auto max-w-6xl p-6 sm:p-10">
        <ErrorState
          title="Delivery-ready orders unavailable"
          message={loadError}
          action={
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground"
              onClick={() => void loadOrders(appliedSearch)}
              type="button"
            >
              Retry
            </button>
          }
        />
      </div>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-6xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Delivery Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Select an order for delivery
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Find an Order Management record whose handoff currently reports readyForDelivery=true.
          Orders that already have a non-cancelled Delivery are excluded automatically; cancelled history does not block a corrected replacement.
        </p>

        <form className="mt-8 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={submitSearch}>
          <label className="block flex-1 text-sm font-medium text-foreground">
            Search delivery-ready orders
            <input
              className={inputClassName}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Order number, customer name or email"
              value={search}
            />
          </label>
          <button
            className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:opacity-50"
            disabled={isLoading}
            type="submit"
          >
            {isLoading ? "Searching…" : "Search"}
          </button>
          {appliedSearch ? (
            <button
              className="rounded-xl border border-border px-6 py-3 font-semibold text-foreground hover:border-border"
              onClick={() => {
                setSearch("");
                setAppliedSearch("");
                setSelected(null);
                setSelectionError(null);
                void loadOrders("");
              }}
              type="button"
            >
              Clear
            </button>
          ) : null}
        </form>

        {selectionError ? (
          <p className="mt-5 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">
            {selectionError}
          </p>
        ) : null}

        {selected ? (
          <section className="mt-7 rounded-3xl border border-success-border bg-success-soft p-6" aria-labelledby="selected-delivery-order">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-success">
              Selection revalidated
            </p>
            <h2 className="mt-2 text-2xl font-bold text-foreground" id="selected-delivery-order">
              {selected.orderNumber}
            </h2>
            <p className="mt-2 text-foreground-muted">
              {selected.customerName} · {selected.customerEmail}
            </p>
            <p className="mt-1 text-sm text-muted">
              Order ID #{selected.orderId} · Customer ID #{selected.customerId} · {selected.itemCount} item{selected.itemCount === 1 ? "" : "s"} · {money(selected.totalAmount)}
            </p>
            <p className="mt-4 text-sm text-success">
              This order is still ready for delivery and has no active/completed Delivery record. Continue to enter the approved delivery schedule.
            </p>
            <Link
              className="mt-5 inline-block rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover"
              to={getDeliverySchedulePagePath(selected.orderId)}
            >
              Schedule delivery
            </Link>
          </section>
        ) : null}

        {orders.length === 0 ? (
          <div className="mt-8">
            <EmptyState
              title={appliedSearch ? "No matching delivery-ready orders" : "No orders ready for delivery"}
              message={appliedSearch
                ? "No eligible Order Management records match this search. Try another order number, customer name, or email."
                : "There are no orders whose handoff currently reports readyForDelivery=true without a non-cancelled Delivery record."}
            />
          </div>
        ) : (
          <div className="mt-8 space-y-5">
            <p className="text-sm text-muted">
              {orders.length} eligible order{orders.length === 1 ? "" : "s"} found
              {appliedSearch ? ` for “${appliedSearch}”` : ""}.
            </p>
            {orders.map((order) => (
              <article className="motion-record rounded-3xl border border-border bg-surface/70 p-6" key={order.orderId}>
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
                      Ready for delivery
                    </p>
                    <h2 className="mt-2 text-2xl font-bold text-foreground">{order.orderNumber}</h2>
                    <p className="mt-2 text-foreground-muted">{order.customerName} · {order.customerEmail}</p>
                    <p className="mt-1 text-sm text-muted">
                      Order ID #{order.orderId} · Customer ID #{order.customerId} · {order.itemCount} item{order.itemCount === 1 ? "" : "s"} · {money(order.totalAmount)}
                    </p>
                  </div>
                  <button
                    className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50"
                    disabled={isSelecting !== null}
                    onClick={() => void selectOrder(order.orderId)}
                    type="button"
                  >
                    {isSelecting === order.orderId ? "Checking…" : "Select order"}
                  </button>
                </div>

                <div className="mt-5 grid gap-3 md:grid-cols-2">
                  {order.items.map((item) => (
                    <div className="rounded-2xl border border-border bg-background/50 p-4" key={item.orderItemId}>
                      <p className="font-semibold text-foreground">Order item #{item.orderItemId}</p>
                      <p className="mt-2 text-sm text-foreground-muted">
                        Product #{item.productId} · Variant #{item.variantId}
                      </p>
                      <p className="mt-1 text-sm text-muted">
                        {item.selectedSize} · {item.selectedColor} · Quantity {item.quantity}
                      </p>
                    </div>
                  ))}
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

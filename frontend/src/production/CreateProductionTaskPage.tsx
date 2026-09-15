import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  createProductionTask,
  getProductionEligibleOrders,
  getProductionTaskApiError,
  type CreateProductionTaskResponse,
  type ProductionEligibleOrder,
} from "../api/productionTasks";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { getProductionTaskDetailPagePath } from "../navigation/navigation";

const selectClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

export function CreateProductionTaskPage() {
  const [orders, setOrders] = useState<ProductionEligibleOrder[]>([]);
  const [selectedOrderId, setSelectedOrderId] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [created, setCreated] = useState<CreateProductionTaskResponse | null>(null);

  const selectedOrder = useMemo(
    () => orders.find((order) => String(order.orderId) === selectedOrderId) ?? null,
    [orders, selectedOrderId],
  );

  async function loadOrders(signal?: AbortSignal) {
    setIsLoading(true);
    setLoadError(null);
    try {
      const eligibleOrders = await getProductionEligibleOrders(signal);
      setOrders(eligibleOrders);
      setSelectedOrderId((current) =>
        current && eligibleOrders.some((order) => String(order.orderId) === current)
          ? current
          : "",
      );
    } catch (error: unknown) {
      if (signal?.aborted) return;
      setLoadError(getProductionTaskApiError(
        error,
        "Eligible customer orders could not be loaded. Please try again.",
      ).message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void loadOrders(controller.signal);
    return () => controller.abort();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setFieldError(null);
    setCreated(null);

    const orderId = Number(selectedOrderId);
    if (!Number.isSafeInteger(orderId) || orderId <= 0 || !selectedOrder) {
      setFieldError("Select an eligible customer order before creating the production task.");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await createProductionTask(orderId);
      setCreated(result);
      await loadOrders();
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "The production task could not be created. Please try again.",
      );
      setSubmitError(apiError.message);
      setFieldError(apiError.fields.orderId ?? null);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-5xl p-6 sm:p-10">
        <LoadingState
          title="Loading production-ready orders…"
          message="Reading eligible customer orders through the Order Management handoff contract."
        />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="mx-auto max-w-5xl p-6 sm:p-10">
        <ErrorState
          title="Production-ready orders unavailable"
          message={loadError}
          action={
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground"
              onClick={() => void loadOrders()}
              type="button"
            >
              Retry
            </button>
          }
        />
      </div>
    );
  }

  if (orders.length === 0 && !created) {
    return (
      <div className="mx-auto max-w-5xl p-6 sm:p-10">
        <EmptyState
          title="No orders ready for production"
          message="Order Management currently has no customer orders whose handoff reports readyForProduction=true. Confirm an order before creating a Production task."
          action={
            <button
              className="rounded-xl border border-primary/50 px-5 py-3 font-semibold text-primary"
              onClick={() => void loadOrders()}
              type="button"
            >
              Refresh eligible orders
            </button>
          }
        />
      </div>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Production Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Create production task
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Select an order that Order Management currently marks ready for production. The task stores only the stable Order ID; order items remain owned by Order Management.
        </p>

        {created ? (
          <div className="mt-7 rounded-2xl border border-success-border bg-success-soft p-5" role="status">
            <p className="font-semibold text-success">{created.message}</p>
            <p className="mt-2 text-xl font-bold text-foreground">{created.task.taskNumber}</p>
            <p className="mt-1 text-sm text-foreground-muted">
              Task ID #{created.task.id} · Order ID #{created.task.orderId} · {created.task.status}
            </p>
            <Link
              className="mt-4 inline-flex rounded-xl border border-success-border px-4 py-2 text-sm font-semibold text-success hover:bg-success-soft"
              to={getProductionTaskDetailPagePath(created.task.id)}
            >
              Add production work details
            </Link>
          </div>
        ) : null}

        {submitError ? <p className="mt-5 text-sm text-danger" role="alert">{submitError}</p> : null}

        <form className="mt-8 space-y-6" onSubmit={submit}>
          <div>
            <label className="block text-sm font-medium text-foreground" htmlFor="eligible-production-order">Eligible customer order</label>
            <select
              aria-invalid={fieldError ? "true" : undefined}
              className={selectClassName}
              id="eligible-production-order"
              onChange={(event) => {
                setSelectedOrderId(event.target.value);
                setFieldError(null);
                setSubmitError(null);
              }}
              value={selectedOrderId}
            >
              <option value="">Select an order</option>
              {orders.map((order) => (
                <option key={order.orderId} value={order.orderId}>
                  {order.orderNumber} · Order #{order.orderId} · {order.items.length} item{order.items.length === 1 ? "" : "s"}
                </option>
              ))}
            </select>
            {fieldError ? <span className="mt-2 block text-sm text-danger">{fieldError}</span> : null}
          </div>

          {selectedOrder ? (
            <section className="rounded-3xl border border-border bg-surface/70 p-6" aria-labelledby="selected-order-heading">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Selected Order Management handoff</p>
                  <h2 className="mt-2 text-2xl font-bold text-foreground" id="selected-order-heading">{selectedOrder.orderNumber}</h2>
                  <p className="mt-1 text-sm text-muted">Order ID #{selectedOrder.orderId} · Customer ID #{selectedOrder.customerId}</p>
                </div>
                <span className="rounded-full border border-success-border bg-success-soft px-4 py-2 text-sm font-semibold text-success">
                  Ready for production
                </span>
              </div>

              <div className="mt-6 space-y-3">
                {selectedOrder.items.map((item) => (
                  <article className="rounded-2xl border border-border bg-background/50 p-4" key={item.orderItemId}>
                    <p className="font-semibold text-foreground">Order item #{item.orderItemId}</p>
                    <p className="mt-2 text-sm text-foreground-muted">
                      Product #{item.productId} · Variant #{item.variantId} · {item.selectedSize} · {item.selectedColor}
                    </p>
                    <p className="mt-1 text-sm text-muted">Required quantity: {item.quantity}</p>
                  </article>
                ))}
              </div>
            </section>
          ) : null}

          <button
            className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50"
            disabled={isSubmitting || !selectedOrder}
            type="submit"
          >
            {isSubmitting ? "Creating task…" : "Create production task"}
          </button>
        </form>
      </div>
    </section>
  );
}

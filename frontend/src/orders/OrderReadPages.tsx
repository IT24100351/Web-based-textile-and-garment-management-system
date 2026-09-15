import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";

import {
  generateOrderInvoice,
  getMyOrderBilling,
  getMyOrderDetail,
  getMyOrders,
  getOrderApiError,
  getOrderBilling,
  getOrderDetail,
  getOrders,
  updateOrderPayment,
  updateOrderStatus,
  type OrderBilling,
  type OrderDetail,
  type OrderPaymentMethod,
  type OrderPaymentStatus,
  type OrderStatus,
  type OrderSummary,
} from "../api/orders";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  customerOrderHistoryPagePath,
  getCustomerOrderDetailPagePath,
  getCustomerOrderTrackingPagePath,
  getStaffOrderDetailPagePath,
  parseOrderPageId,
  staffOrderListPagePath,
} from "../navigation/navigation";

type Audience = "staff" | "customer";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

const statuses: OrderStatus[] = [
  "PENDING",
  "CONFIRMED",
  "IN_PRODUCTION",
  "READY_FOR_DELIVERY",
  "COMPLETED",
  "CANCELLED",
];

function statusLabel(status: OrderStatus) {
  return status.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function money(value: string) {
  return `LKR ${value}`;
}

type PaymentField = "paymentStatus" | "amountPaid" | "paymentMethod";
type PaymentFieldErrors = Partial<Record<PaymentField, string>>;

function paymentStatusOptions(current: OrderPaymentStatus): OrderPaymentStatus[] {
  switch (current) {
    case "UNPAID":
      return ["UNPAID", "PARTIALLY_PAID", "PAID"];
    case "PARTIALLY_PAID":
      return ["PARTIALLY_PAID", "PAID"];
    case "PAID":
      return ["PAID"];
  }
}

function paymentStatusLabel(status: OrderPaymentStatus) {
  return status === "PARTIALLY_PAID" ? "Partially paid" : status === "PAID" ? "Paid" : "Unpaid";
}

function decimalAmount(value: string) {
  if (!/^(?:0|[1-9]\d*)(?:\.\d{1,2})?$/.test(value.trim())) return null;
  const amount = Number(value);
  return Number.isFinite(amount) ? amount : null;
}

function validatePaymentDraft(
  billing: OrderBilling | null,
  status: OrderPaymentStatus,
  amountValue: string,
  method: OrderPaymentMethod | "",
): PaymentFieldErrors {
  const errors: PaymentFieldErrors = {};
  const invoiceTotal = billing?.invoice ? decimalAmount(billing.invoice.totalAmount) : null;
  const amount = decimalAmount(amountValue);

  if (amount === null) {
    errors.amountPaid = "Enter a non-negative amount with up to 2 decimal places.";
    return errors;
  }
  if (invoiceTotal === null) {
    errors.amountPaid = "The invoice total is unavailable. Reload the order before recording payment.";
    return errors;
  }
  if (status === "UNPAID" && amount !== 0) {
    errors.amountPaid = "Unpaid records must have an amount paid of 0.00.";
  }
  if (status === "PARTIALLY_PAID" && (amount <= 0 || amount >= invoiceTotal)) {
    errors.amountPaid = "Enter an amount greater than 0 and less than the invoice total.";
  }
  if (status === "PAID" && amount !== invoiceTotal) {
    errors.amountPaid = `Paid amount must equal the invoice total of ${money(billing!.invoice!.totalAmount)}.`;
  }
  const currentPayment = billing?.payment;
  if (
    currentPayment?.paymentStatus === "PARTIALLY_PAID"
    && status === "PARTIALLY_PAID"
    && amount < Number(currentPayment.amountPaid)
  ) {
    errors.amountPaid = "Amount paid cannot be reduced because refunds/reversals are not enabled.";
  }
  if (status !== "UNPAID" && !method) {
    errors.paymentMethod = "Select how the payment was recorded.";
  }
  if (status === "UNPAID" && method) {
    errors.paymentMethod = "Unpaid records cannot have a payment method.";
  }
  return errors;
}

function detailPath(audience: Audience, orderId: number) {
  return audience === "staff"
    ? getStaffOrderDetailPagePath(orderId)
    : getCustomerOrderDetailPagePath(orderId);
}

function listPath(audience: Audience) {
  return audience === "staff" ? staffOrderListPagePath : customerOrderHistoryPagePath;
}

function OrderListPage({ audience }: { audience: Audience }) {
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [activeSearch, setActiveSearch] = useState("");
  const [activeStatus, setActiveStatus] = useState<OrderStatus | "">("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(signal?: AbortSignal, nextSearch = activeSearch, nextStatus = activeStatus) {
    setIsLoading(true);
    setError(null);
    try {
      const loaded = audience === "staff"
        ? await getOrders({ search: nextSearch, status: nextStatus }, signal)
        : await getMyOrders(signal);
      setOrders(loaded);
    } catch (loadError: unknown) {
      if (signal?.aborted) return;
      setError(getOrderApiError(
        loadError,
        audience === "staff"
          ? "Customer orders could not be loaded. Please try again."
          : "Your order history could not be loaded. Please try again.",
      ).message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal, "", "");
    return () => controller.abort();
    // The initial fetch intentionally ignores draft staff filters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [audience]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedSearch = search.trim();
    setActiveSearch(normalizedSearch);
    setActiveStatus(status);
    void load(undefined, normalizedSearch, status);
  }

  function clearFilters() {
    setSearch("");
    setStatus("");
    setActiveSearch("");
    setActiveStatus("");
    void load(undefined, "", "");
  }

  const hasFilters = audience === "staff" && (activeSearch !== "" || activeStatus !== "");

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-6xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Order Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          {audience === "staff" ? "Customer orders" : "My order history"}
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          {audience === "staff"
            ? "Find customer orders by order number, customer name or email, and inspect the stored item and price snapshots."
            : "Review orders placed by your signed-in customer account. Other customers' orders are never included in this history."}
        </p>

        {audience === "staff" && (
          <form className="mt-8 grid gap-4 rounded-3xl border border-border bg-surface/70 p-6 md:grid-cols-[1fr_240px_auto] md:items-end" onSubmit={applyFilters}>
            <label className="text-sm font-medium text-foreground">
              Search orders
              <input
                className={inputClassName}
                maxLength={120}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Order number, customer name or email"
                value={search}
              />
            </label>
            <label className="text-sm font-medium text-foreground">
              Status
              <select className={inputClassName} onChange={(event) => setStatus(event.target.value as OrderStatus | "")} value={status}>
                <option value="">All statuses</option>
                {statuses.map((option) => <option key={option} value={option}>{statusLabel(option)}</option>)}
              </select>
            </label>
            <div className="flex gap-3">
              <button className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover" type="submit">
                Apply filters
              </button>
              {hasFilters && (
                <button className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground" onClick={clearFilters} type="button">
                  Clear
                </button>
              )}
            </div>
          </form>
        )}

        <div className="mt-8">
          {isLoading && orders.length === 0 ? (
            <LoadingState title="Loading orders…" message="Reading the latest stored order headers and totals." />
          ) : error && orders.length === 0 ? (
            <ErrorState
              title="Orders unavailable"
              message={error}
              action={<button className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" onClick={() => void load()} type="button">Retry</button>}
            />
          ) : orders.length === 0 ? (
            <EmptyState
              title={hasFilters ? "No matching customer orders" : audience === "staff" ? "No customer orders" : "No orders yet"}
              message={hasFilters
                ? "No orders match the current search and status filter."
                : audience === "staff"
                  ? "No customer orders have been created yet."
                  : "Orders placed with your registered customer account will appear here."}
              action={hasFilters ? <button className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground" onClick={clearFilters} type="button">Clear filters</button> : undefined}
            />
          ) : (
            <>
              {error && <p className="mb-4 rounded-xl border border-warning-border bg-warning-soft p-4 text-sm text-warning" role="alert">{error}</p>}
              <p className="mb-4 text-sm text-muted">
                {orders.length} {orders.length === 1 ? "order" : "orders"}{hasFilters ? " found for the current filters" : ""}
              </p>
              <div className="grid gap-5 lg:grid-cols-2">
                {orders.map((order) => (
                  <article className="motion-record rounded-3xl border border-border bg-surface/70 p-6" key={order.id}>
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div>
                        <p className="text-xs uppercase tracking-[0.16em] text-muted">Order #{order.id}</p>
                        <h2 className="mt-2 text-xl font-bold text-foreground">{order.orderNumber}</h2>
                      </div>
                      <StatusBadge status={order.status} />
                    </div>
                    {audience === "staff" && (
                      <div className="mt-4">
                        <p className="font-medium text-foreground">{order.customerName}</p>
                        <p className="text-sm text-muted">{order.customerEmail}</p>
                      </div>
                    )}
                    <dl className="mt-5 grid grid-cols-2 gap-4 rounded-2xl border border-border bg-background/50 p-4 text-sm sm:grid-cols-3">
                      <div><dt className="text-muted">Placed</dt><dd className="mt-1 text-foreground">{formatDate(order.createdAt)}</dd></div>
                      <div><dt className="text-muted">Items</dt><dd className="mt-1 font-semibold text-foreground">{order.itemCount}</dd></div>
                      <div><dt className="text-muted">Total</dt><dd className="mt-1 font-semibold text-foreground">{money(order.totalAmount)}</dd></div>
                    </dl>
                    <div className="mt-5 flex flex-wrap gap-3">
                      <Link className="inline-flex rounded-xl bg-primary px-4 py-2.5 font-semibold text-primary-foreground transition hover:bg-primary-hover" to={detailPath(audience, order.id)}>
                        View order details
                      </Link>
                      {audience === "customer" && (
                        <Link className="inline-flex rounded-xl border border-border px-4 py-2.5 font-semibold text-foreground" to={getCustomerOrderTrackingPagePath(order.id)}>
                          Track progress
                        </Link>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function OrderDetailPage({ audience }: { audience: Audience }) {
  const { orderId } = useParams();
  const parsedOrderId = parseOrderPageId(orderId);
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [isLoading, setIsLoading] = useState(parsedOrderId !== null);
  const [error, setError] = useState<string | null>(null);
  const [nextStatus, setNextStatus] = useState<OrderStatus | "">("");
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [statusUpdateError, setStatusUpdateError] = useState<string | null>(null);
  const [statusUpdateMessage, setStatusUpdateMessage] = useState<string | null>(null);

  const [billing, setBilling] = useState<OrderBilling | null>(null);
  const [billingMessage, setBillingMessage] = useState<string | null>(null);
  const [billingError, setBillingError] = useState<string | null>(null);
  const [isSavingBilling, setIsSavingBilling] = useState(false);
  const [paymentStatus, setPaymentStatus] = useState<OrderPaymentStatus>("UNPAID");
  const [amountPaid, setAmountPaid] = useState("0.00");
  const [paymentMethod, setPaymentMethod] = useState<OrderPaymentMethod | "">("");
  const [paymentReference, setPaymentReference] = useState("");
  const [paymentNote, setPaymentNote] = useState("");
  const [paymentFieldErrors, setPaymentFieldErrors] = useState<PaymentFieldErrors>({});

  function applyBilling(nextBilling: OrderBilling) {
    setBilling(nextBilling);
    setPaymentFieldErrors({});
    if (nextBilling.payment) {
      setPaymentStatus(nextBilling.payment.paymentStatus);
      setAmountPaid(nextBilling.payment.amountPaid);
      setPaymentMethod(nextBilling.payment.paymentMethod ?? "");
      setPaymentReference(nextBilling.payment.paymentReference ?? "");
      setPaymentNote(nextBilling.payment.note ?? "");
    }
  }

  async function load(signal?: AbortSignal) {
    if (parsedOrderId === null) {
      setError("The order identifier in this URL is invalid.");
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const [loadedOrder, loadedBilling] = await Promise.all([
        audience === "staff"
          ? getOrderDetail(parsedOrderId, signal)
          : getMyOrderDetail(parsedOrderId, signal),
        audience === "staff"
          ? getOrderBilling(parsedOrderId, signal)
          : getMyOrderBilling(parsedOrderId, signal),
      ]);
      setOrder(loadedOrder);
      applyBilling(loadedBilling);
    } catch (loadError: unknown) {
      if (signal?.aborted) return;
      const apiError = getOrderApiError(loadError, "Order details could not be loaded. Please try again.");
      setError(apiError.status === 404 ? "This order was not found or is not available to your account." : apiError.message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  async function submitStatusUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (audience !== "staff" || parsedOrderId === null || !nextStatus) {
      setStatusUpdateError("Select an allowed next status.");
      return;
    }

    setIsUpdatingStatus(true);
    setStatusUpdateError(null);
    setStatusUpdateMessage(null);
    try {
      const response = await updateOrderStatus(parsedOrderId, nextStatus);
      setOrder(response.order);
      setNextStatus("");
      setStatusUpdateMessage(response.message);
    } catch (updateError: unknown) {
      const apiError = getOrderApiError(
        updateError,
        "Order status could not be updated. Please try again.",
      );
      setStatusUpdateError(apiError.fields.status ?? apiError.message);
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  async function createInvoice() {
    if (audience !== "staff" || parsedOrderId === null) return;
    setIsSavingBilling(true);
    setBillingError(null);
    setBillingMessage(null);
    try {
      const response = await generateOrderInvoice(parsedOrderId);
      applyBilling(response.billing);
      setBillingMessage(response.message);
    } catch (invoiceError: unknown) {
      setBillingError(getOrderApiError(
        invoiceError,
        "Invoice could not be generated. Please try again.",
      ).message);
    } finally {
      setIsSavingBilling(false);
    }
  }

  async function submitPaymentUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (audience !== "staff" || parsedOrderId === null) return;
    const draftErrors = validatePaymentDraft(
      billing,
      paymentStatus,
      amountPaid,
      paymentMethod,
    );
    if (Object.keys(draftErrors).length > 0) {
      setPaymentFieldErrors(draftErrors);
      setBillingError("Please correct the highlighted payment fields.");
      setBillingMessage(null);
      return;
    }
    setIsSavingBilling(true);
    setBillingError(null);
    setBillingMessage(null);
    setPaymentFieldErrors({});
    try {
      const response = await updateOrderPayment(parsedOrderId, {
        paymentStatus,
        amountPaid,
        paymentMethod,
        paymentReference,
        note: paymentNote,
      });
      applyBilling(response.billing);
      setBillingMessage(response.message);
    } catch (paymentError: unknown) {
      const apiError = getOrderApiError(
        paymentError,
        "Payment record could not be updated. Please try again.",
      );
      setPaymentFieldErrors(apiError.fields);
      setBillingError(Object.values(apiError.fields)[0] ?? apiError.message);
    } finally {
      setIsSavingBilling(false);
    }
  }

  function changePaymentStatus(nextStatus: OrderPaymentStatus) {
    setPaymentStatus(nextStatus);
    setPaymentFieldErrors({});
    setBillingError(null);
    setBillingMessage(null);
    if (nextStatus === "UNPAID") {
      setAmountPaid("0.00");
      setPaymentMethod("");
    } else if (nextStatus === "PAID" && billing?.invoice) {
      setAmountPaid(billing.invoice.totalAmount);
    } else if (billing?.payment?.paymentStatus === "UNPAID") {
      setAmountPaid("");
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
    // The parsed path ID and audience fully define this read.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [audience, parsedOrderId]);

  if (isLoading) {
    return <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12"><LoadingState title="Loading order details…" message="Reading the stored order header, items and historical totals." /></div>;
  }

  if (error || !order) {
    return (
      <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          title="Order details unavailable"
          message={error ?? "Order details could not be loaded."}
          action={parsedOrderId !== null ? <button className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" onClick={() => void load()} type="button">Retry</button> : undefined}
        />
        <Link className="mt-5 inline-flex text-sm font-semibold text-primary" to={listPath(audience)}>← Back to orders</Link>
      </div>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <Link className="text-sm font-semibold text-primary" to={listPath(audience)}>← Back to orders</Link>
        <div className="mt-5 rounded-3xl border border-border bg-surface/70 p-7 sm:p-9">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Order details</p>
              <h1 className="mt-3 text-3xl font-bold text-foreground">{order.orderNumber}</h1>
              <p className="mt-2 text-sm text-muted">Stable order ID #{order.id}</p>
            </div>
            <StatusBadge status={order.status} />
          </div>

          <dl className="mt-7 grid gap-4 rounded-2xl border border-border bg-background/50 p-5 sm:grid-cols-2 lg:grid-cols-4">
            <div><dt className="text-xs uppercase text-muted">Customer</dt><dd className="mt-1 font-semibold text-foreground">{order.customerName}</dd><dd className="text-sm text-muted">{order.customerEmail}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Created</dt><dd className="mt-1 text-foreground">{formatDate(order.createdAt)}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Last updated</dt><dd className="mt-1 text-foreground">{formatDate(order.updatedAt)}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Order total</dt><dd className="mt-1 text-xl font-bold text-foreground">{money(order.totalAmount)}</dd></div>
          </dl>

          <section className="mt-7 rounded-2xl border border-border bg-background/50 p-5" aria-labelledby="billing-heading">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-bold text-foreground" id="billing-heading">Invoice & payment record</h2>
                <p className="mt-1 text-sm text-muted">Billing uses the stored order-item price snapshots. No payment gateway is connected.</p>
              </div>
              {billing?.invoice && (
                <StatusBadge status={billing.payment?.paymentStatus ?? "UNPAID"} />
              )}
            </div>

            {!billing?.invoiceGenerated || !billing.invoice ? (
              audience === "staff" ? (
                <div className="mt-4">
                  <p className="text-sm text-muted">No invoice has been issued for this order yet.</p>
                  <button
                    className="mt-3 rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={isSavingBilling || order.status === "CANCELLED"}
                    onClick={() => void createInvoice()}
                    type="button"
                  >
                    {isSavingBilling ? "Generating…" : "Generate invoice"}
                  </button>
                </div>
              ) : <p className="mt-4 text-sm text-muted">An invoice has not been issued for this order yet.</p>
            ) : (
              <div className="mt-4 space-y-4">
                <dl className="grid gap-3 rounded-xl border border-border bg-surface/60 p-4 sm:grid-cols-3">
                  <div><dt className="text-xs uppercase text-muted">Invoice</dt><dd className="mt-1 font-semibold text-foreground">{billing.invoice.invoiceNumber}</dd></div>
                  <div><dt className="text-xs uppercase text-muted">Issued</dt><dd className="mt-1 text-foreground">{formatDate(billing.invoice.issuedAt)}</dd></div>
                  <div><dt className="text-xs uppercase text-muted">Invoice total</dt><dd className="mt-1 font-bold text-primary">{money(billing.invoice.totalAmount)}</dd></div>
                </dl>
                {billing.payment && (
                  <p className="text-sm text-muted">Recorded amount: <span className="font-semibold text-foreground">{money(billing.payment.amountPaid)}</span>{billing.payment.paymentMethod ? ` · ${billing.payment.paymentMethod.replaceAll("_", " ")}` : ""}</p>
                )}
                {audience === "staff" && billing.payment && order.status !== "CANCELLED" && billing.payment.paymentStatus !== "PAID" && (
                  <form className="grid gap-3 sm:grid-cols-2" noValidate onSubmit={submitPaymentUpdate}>
                    <div className="text-sm font-medium text-foreground">
                      <label htmlFor="order-payment-status">Payment status</label>
                      <select
                        aria-invalid={Boolean(paymentFieldErrors.paymentStatus)}
                        className={inputClassName}
                        id="order-payment-status"
                        onChange={(event) => changePaymentStatus(event.target.value as OrderPaymentStatus)}
                        value={paymentStatus}
                      >
                        {paymentStatusOptions(billing.payment.paymentStatus).map((status) => (
                          <option key={status} value={status}>{paymentStatusLabel(status)}</option>
                        ))}
                      </select>
                      {paymentFieldErrors.paymentStatus && <span className="mt-2 block text-xs text-danger">{paymentFieldErrors.paymentStatus}</span>}
                    </div>
                    <div className="text-sm font-medium text-foreground">
                      <label htmlFor="order-payment-amount">Amount paid</label>
                      <input
                        aria-invalid={Boolean(paymentFieldErrors.amountPaid)}
                        className={inputClassName}
                        disabled={paymentStatus === "UNPAID" || paymentStatus === "PAID"}
                        id="order-payment-amount"
                        inputMode="decimal"
                        min="0"
                        onChange={(event) => {
                          setAmountPaid(event.target.value);
                          setPaymentFieldErrors((current) => ({ ...current, amountPaid: undefined }));
                        }}
                        step="0.01"
                        type="text"
                        value={amountPaid}
                      />
                      {paymentStatus === "PAID" && billing.invoice && <span className="mt-2 block text-xs text-muted">Automatically set to the invoice total of {money(billing.invoice.totalAmount)}.</span>}
                      {paymentStatus === "UNPAID" && <span className="mt-2 block text-xs text-muted">Unpaid records are fixed at LKR 0.00.</span>}
                      {paymentFieldErrors.amountPaid && <span className="mt-2 block text-xs text-danger">{paymentFieldErrors.amountPaid}</span>}
                    </div>
                    <div className="text-sm font-medium text-foreground">
                      <label htmlFor="order-payment-method">Payment method{paymentStatus !== "UNPAID" ? " (required)" : ""}</label>
                      <select
                        aria-invalid={Boolean(paymentFieldErrors.paymentMethod)}
                        className={inputClassName}
                        disabled={paymentStatus === "UNPAID"}
                        id="order-payment-method"
                        onChange={(event) => {
                          setPaymentMethod(event.target.value as OrderPaymentMethod | "");
                          setPaymentFieldErrors((current) => ({ ...current, paymentMethod: undefined }));
                        }}
                        value={paymentMethod}
                      >
                        <option value="">Not applicable / select</option>
                        <option value="CASH">Cash</option>
                        <option value="BANK_TRANSFER">Bank transfer</option>
                        <option value="OTHER">Other</option>
                      </select>
                      {paymentFieldErrors.paymentMethod && <span className="mt-2 block text-xs text-danger">{paymentFieldErrors.paymentMethod}</span>}
                    </div>
                    <label className="text-sm font-medium text-foreground">Reference
                      <input className={inputClassName} maxLength={120} onChange={(event) => setPaymentReference(event.target.value)} value={paymentReference} />
                    </label>
                    <label className="text-sm font-medium text-foreground sm:col-span-2">Note
                      <textarea className={inputClassName} maxLength={500} onChange={(event) => setPaymentNote(event.target.value)} rows={2} value={paymentNote} />
                    </label>
                    <button className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground disabled:opacity-60 sm:col-span-2 sm:justify-self-start" disabled={isSavingBilling} type="submit">
                      {isSavingBilling ? "Saving…" : "Save payment record"}
                    </button>
                  </form>
                )}
                {audience === "staff" && billing.payment?.paymentStatus === "PAID" && (
                  <p className="rounded-xl border border-success-border bg-success-soft p-4 text-sm text-success">
                    Payment is fully recorded. Refund and reversal workflows are not enabled, so this record is read-only.
                  </p>
                )}
              </div>
            )}
            {billingError && <p className="mt-3 text-sm text-danger" role="alert">{billingError}</p>}
            {billingMessage && <p className="mt-3 text-sm text-success" role="status">{billingMessage}</p>}
          </section>

          {audience === "staff" && (
            <section className="mt-7 rounded-2xl border border-border bg-background/50 p-5" aria-labelledby="status-update-heading">
              <h2 className="text-lg font-bold text-foreground" id="status-update-heading">Update order status</h2>
              {order.status === "CONFIRMED" && (
                <p className="mt-2 text-sm text-muted">
                  Production progress is owned by Production Management. Cancellation is the only remaining manual order action at this stage.
                </p>
              )}
              {order.allowedStatusTransitions.length > 0 ? (
                <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={submitStatusUpdate}>
                  <label className="min-w-0 flex-1 text-sm font-medium text-foreground">
                    Next status
                    <select
                      className={inputClassName}
                      onChange={(event) => setNextStatus(event.target.value as OrderStatus | "")}
                      value={nextStatus}
                    >
                      <option value="">Select an allowed transition</option>
                      {order.allowedStatusTransitions.map((status) => (
                        <option key={status} value={status}>{statusLabel(status)}</option>
                      ))}
                    </select>
                  </label>
                  <button
                    className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
                    disabled={isUpdatingStatus || !nextStatus}
                    type="submit"
                  >
                    {isUpdatingStatus ? "Updating…" : "Update status"}
                  </button>
                </form>
              ) : (
                <p className="mt-3 text-sm text-muted">
                  {order.status === "CONFIRMED"
                    ? "The order is ready for Production Management. Starting production updates this order automatically."
                    : order.status === "IN_PRODUCTION"
                      ? "Production Management controls progress and marks the order ready for delivery when all required work is complete."
                      : order.status === "READY_FOR_DELIVERY"
                        ? "Final order completion is synchronized by Delivery Management after the delivery is marked delivered."
                        : "This order is in a terminal status and has no permitted next transition."}
                </p>
              )}
              {statusUpdateError && <p className="mt-3 text-sm text-danger" role="alert">{statusUpdateError}</p>}
              {statusUpdateMessage && <p className="mt-3 text-sm text-success" role="status">{statusUpdateMessage}</p>}
            </section>
          )}

          <section className="mt-7 rounded-2xl border border-border bg-background/50 p-5" aria-labelledby="order-progress-heading">
            <h2 className="text-lg font-bold text-foreground" id="order-progress-heading">Order progress</h2>
            <div className="mt-2 flex items-center gap-2 text-sm text-muted">Current status: <StatusBadge status={order.status} /></div>
            {order.statusHistory.length === 0 ? (
              <p className="mt-4 text-sm text-muted">No status transitions have been recorded yet.</p>
            ) : (
              <ol className="mt-4 space-y-3">
                {order.statusHistory.map((entry) => (
                  <li className="motion-record rounded-xl border border-border bg-surface/60 p-4" key={entry.id}>
                    <p className="font-medium text-foreground">{statusLabel(entry.fromStatus)} → {statusLabel(entry.toStatus)}</p>
                    <p className="mt-1 text-xs text-muted">{formatDate(entry.changedAt)}</p>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <div className="mt-8">
            <h2 className="text-xl font-bold text-foreground">Items</h2>
            <p className="mt-2 text-sm text-muted">Size, color and price shown below are the stored order-time snapshots. Product name is the current catalog label for the referenced stable Product ID.</p>
            <div className="mt-4 space-y-4">
              {order.items.map((item) => (
                <article className="rounded-2xl border border-border bg-background/60 p-5" key={item.id}>
                  <div className="flex flex-wrap justify-between gap-4">
                    <div>
                      <h3 className="font-semibold text-foreground">{item.productName}</h3>
                      <p className="mt-1 text-xs text-muted">Product #{item.productId} · Variant #{item.variantId} · Line #{item.id}</p>
                      <p className="mt-2 text-sm text-foreground-muted">{item.selectedSize} · {item.selectedColor} · Qty {item.quantity}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-sm text-muted">{money(item.unitPriceSnapshot)} each</p>
                      <p className="mt-1 text-lg font-bold text-foreground">{money(item.lineTotal)}</p>
                    </div>
                  </div>
                </article>
              ))}
            </div>
            <div className="mt-6 flex justify-end border-t border-border pt-5">
              <p className="text-lg font-semibold text-foreground">Total: <span className="text-primary">{money(order.totalAmount)}</span></p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export function StaffOrderListPage() {
  return <OrderListPage audience="staff" />;
}

export function CustomerOrderHistoryPage() {
  return <OrderListPage audience="customer" />;
}

export function StaffOrderDetailPage() {
  return <OrderDetailPage audience="staff" />;
}

export function CustomerOrderDetailPage() {
  return <OrderDetailPage audience="customer" />;
}

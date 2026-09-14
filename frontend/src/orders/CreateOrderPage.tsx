import { useEffect, useRef, useState, type FormEvent } from "react";

import {
  createOrder,
  getOrderApiError,
  getOrderCustomers,
  type CreateOrderResponse,
  type OrderCustomerOption,
} from "../api/orders";
import { isApiRequestCanceled } from "../api/client";
import { getCatalogProducts, getProductApiError } from "../api/products";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { StatusBadge } from "../components/ui/StatusBadge";
import type { GarmentProductDetails, ProductVariant } from "../products/productTypes";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

interface OrderLineDraft {
  key: number;
  productId: string;
  variantId: string;
  quantity: string;
}

function emptyLine(key: number): OrderLineDraft {
  return { key, productId: "", variantId: "", quantity: "1" };
}

function selectedProduct(
  products: GarmentProductDetails[],
  productId: string,
): GarmentProductDetails | undefined {
  const id = Number(productId);
  return products.find((product) => product.id === id);
}

function selectedVariant(
  product: GarmentProductDetails | undefined,
  variantId: string,
): ProductVariant | undefined {
  const id = Number(variantId);
  return product?.variants.find((variant) => variant.id === id);
}

export function CreateOrderPage() {
  const nextLineKey = useRef(2);
  const [customers, setCustomers] = useState<OrderCustomerOption[]>([]);
  const [products, setProducts] = useState<GarmentProductDetails[]>([]);
  const [customerId, setCustomerId] = useState("");
  const [customerSearch, setCustomerSearch] = useState("");
  const [lines, setLines] = useState<OrderLineDraft[]>([emptyLine(1)]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [savedOrder, setSavedOrder] = useState<CreateOrderResponse | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSearchingCustomers, setIsSearchingCustomers] = useState(false);

  async function loadFormData(signal?: AbortSignal) {
    setIsLoading(true);
    setLoadError(null);
    try {
      const [customerOptions, catalogProducts] = await Promise.all([
        getOrderCustomers(undefined, signal),
        getCatalogProducts({ availability: "AVAILABLE" }, signal),
      ]);
      if (!signal?.aborted) {
        setCustomers(customerOptions);
        setProducts(catalogProducts);
      }
    } catch (error: unknown) {
      if (isApiRequestCanceled(error, signal)) return;
      const orderError = getOrderApiError(error, "Order form data could not be loaded.");
      const productError = getProductApiError(error, orderError.message);
      setLoadError(productError.message || orderError.message);
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void loadFormData(controller.signal);
    return () => controller.abort();
  }, []);

  async function searchCustomers() {
    setIsSearchingCustomers(true);
    setLoadError(null);
    try {
      const result = await getOrderCustomers(customerSearch);
      setCustomers(result);
      if (customerId && !result.some((customer) => String(customer.id) === customerId)) {
        setCustomerId("");
      }
    } catch (error: unknown) {
      setLoadError(getOrderApiError(
        error,
        "Customers could not be searched. Please try again.",
      ).message);
    } finally {
      setIsSearchingCustomers(false);
    }
  }

  function updateLine(key: number, patch: Partial<OrderLineDraft>) {
    setLines((current) => current.map((line) =>
      line.key === key ? { ...line, ...patch } : line));
    setValidationError(null);
    setFieldErrors({});
  }

  function addLine() {
    if (lines.length >= 50) {
      setValidationError("An order may contain at most 50 items.");
      return;
    }
    const key = nextLineKey.current++;
    setLines((current) => [...current, emptyLine(key)]);
  }

  function removeLine(key: number) {
    if (lines.length === 1) {
      return;
    }
    setLines((current) => current.filter((line) => line.key !== key));
    setValidationError(null);
    setFieldErrors({});
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmissionError(null);
    setValidationError(null);
    setFieldErrors({});

    const parsedCustomerId = Number(customerId);
    if (!Number.isSafeInteger(parsedCustomerId) || parsedCustomerId <= 0) {
      setValidationError("Please correct the highlighted order fields.");
      setFieldErrors({ customerId: "Select an active customer before saving the order." });
      return;
    }

    const items = [] as Array<{ productId: number; variantId: number; quantity: number }>;
    for (let index = 0; index < lines.length; index += 1) {
      const line = lines[index];
      const productId = Number(line.productId);
      const variantId = Number(line.variantId);
      const quantity = Number(line.quantity);
      if (
        !Number.isSafeInteger(productId) || productId <= 0
        || !Number.isSafeInteger(variantId) || variantId <= 0
        || !Number.isSafeInteger(quantity) || quantity <= 0
      ) {
        const prefix = `items[${index}]`;
        const errors: Record<string, string> = {};
        if (!Number.isSafeInteger(productId) || productId <= 0) {
          errors[`${prefix}.productId`] = "Select a product.";
        }
        if (!Number.isSafeInteger(variantId) || variantId <= 0) {
          errors[`${prefix}.variantId`] = "Select an available size/color variant.";
        }
        if (!Number.isSafeInteger(quantity) || quantity <= 0) {
          errors[`${prefix}.quantity`] = "Quantity must be a positive whole number.";
        }
        setValidationError("Please correct the highlighted order fields.");
        setFieldErrors(errors);
        return;
      }
      items.push({ productId, variantId, quantity });
    }

    setIsSubmitting(true);
    try {
      const result = await createOrder({ customerId: parsedCustomerId, items });
      setSavedOrder(result);
    } catch (error: unknown) {
      const orderError = getOrderApiError(
        error,
        "The customer order could not be saved. Please review the selections and try again.",
      );
      setFieldErrors(orderError.fields);
      setSubmissionError(orderError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  function resetForm() {
    setCustomerId("");
    setCustomerSearch("");
    setLines([emptyLine(1)]);
    nextLineKey.current = 2;
    setSavedOrder(null);
    setSubmissionError(null);
    setValidationError(null);
    setFieldErrors({});
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          title="Loading order form…"
          message="Loading active customers and currently selectable garment variants."
        />
      </div>
    );
  }

  if (loadError && customers.length === 0 && products.length === 0) {
    return (
      <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          title="Order form unavailable"
          message={loadError}
          action={(
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground"
              onClick={() => void loadFormData()}
              type="button"
            >
              Retry
            </button>
          )}
        />
      </div>
    );
  }

  if (products.length === 0) {
    return (
      <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <EmptyState
          title="No selectable garments"
          message="A customer order cannot be created until Product Management has at least one active product with an available size/color variant."
        />
      </div>
    );
  }

  if (savedOrder) {
    return (
      <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <div className="mx-auto max-w-4xl rounded-3xl border border-success-border bg-success-soft p-7 sm:p-10">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-success">
            Order saved
          </p>
          <h1 className="mt-3 text-3xl font-bold text-foreground">{savedOrder.orderNumber}</h1>
          <p aria-live="polite" className="mt-3 text-success">
            Customer order created successfully for {savedOrder.customer.fullName}.
          </p>
          <dl className="mt-7 grid gap-4 rounded-2xl border border-success-border bg-background/60 p-5 sm:grid-cols-3">
            <div><dt className="text-xs uppercase text-muted">Order ID</dt><dd className="mt-1 font-semibold text-foreground">{savedOrder.id}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Status</dt><dd className="mt-1"><StatusBadge status={savedOrder.status} /></dd></div>
            <div><dt className="text-xs uppercase text-muted">Items</dt><dd className="mt-1 font-semibold text-foreground">{savedOrder.items.length}</dd></div>
          </dl>
          <div className="mt-6 space-y-3">
            {savedOrder.items.map((item) => (
              <article className="rounded-2xl border border-border bg-background/60 p-5" key={item.id}>
                <h2 className="font-semibold text-foreground">{item.productName}</h2>
                <p className="mt-1 text-sm text-foreground-muted">
                  {item.selectedSize} · {item.selectedColor} · Qty {item.quantity} · LKR {item.unitPriceSnapshot} each
                </p>
              </article>
            ))}
          </div>
          <button
            className="mt-8 rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover"
            onClick={resetForm}
            type="button"
          >
            Create another order
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Order Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Create customer order
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Select an active registered customer and available garment variants. Size, color and price are verified again by the server when the order is saved.
        </p>

        <form className="mt-8 space-y-8" noValidate onSubmit={handleSubmit}>
          <section className="rounded-3xl border border-border bg-surface/70 p-6 sm:p-8">
            <h2 className="text-xl font-semibold text-foreground">Customer</h2>
            <div className="mt-5 grid gap-4 md:grid-cols-[1fr_auto]">
              <label className="text-sm font-medium text-foreground" htmlFor="customer-search">
                Find customer
                <input
                  className={inputClassName}
                  id="customer-search"
                  onChange={(event) => setCustomerSearch(event.target.value)}
                  placeholder="Name or email"
                  value={customerSearch}
                />
              </label>
              <button
                className="self-end rounded-xl border border-primary px-5 py-3 font-semibold text-primary disabled:opacity-60"
                disabled={isSearchingCustomers}
                onClick={() => void searchCustomers()}
                type="button"
              >
                {isSearchingCustomers ? "Searching…" : "Search customers"}
              </button>
            </div>
            <label className="mt-5 block text-sm font-medium text-foreground" htmlFor="order-customer">
              Active customer
              <select
                className={inputClassName}
                id="order-customer"
                onChange={(event) => {
                  setCustomerId(event.target.value);
                  setValidationError(null);
                  setFieldErrors({});
                }}
                aria-invalid={Boolean(fieldErrors.customerId)}
                value={customerId}
              >
                <option value="">Select customer</option>
                {customers.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.fullName} · {customer.email}
                  </option>
                ))}
              </select>
              {fieldErrors.customerId ? <span className="mt-2 block text-xs text-danger">{fieldErrors.customerId}</span> : null}
            </label>
            {customers.length === 0 ? (
              <p className="mt-3 text-sm text-warning">No active customers match the current search.</p>
            ) : null}
          </section>

          <section className="rounded-3xl border border-border bg-surface/70 p-6 sm:p-8">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold text-foreground">Order items</h2>
                <p className="mt-1 text-sm text-muted">Choose the exact size/color variant and enter a whole-number quantity.</p>
              </div>
              <button
                className="rounded-xl border border-primary px-4 py-2 font-semibold text-primary"
                onClick={addLine}
                type="button"
              >
                Add item
              </button>
            </div>

            <div className="mt-6 space-y-5">
              {lines.map((line, index) => {
                const product = selectedProduct(products, line.productId);
                const variant = selectedVariant(product, line.variantId);
                return (
                  <article className="rounded-2xl border border-border bg-background/60 p-5" key={line.key}>
                    <div className="flex items-center justify-between gap-4">
                      <h3 className="font-semibold text-foreground">Item {index + 1}</h3>
                      {lines.length > 1 ? (
                        <button
                          className="text-sm font-semibold text-danger hover:text-danger"
                          onClick={() => removeLine(line.key)}
                          type="button"
                        >
                          Remove item {index + 1}
                        </button>
                      ) : null}
                    </div>
                    <div className="mt-4 grid gap-5 md:grid-cols-3">
                      <label className="text-sm font-medium text-foreground" htmlFor={`order-product-${line.key}`}>
                        Product
                        <select
                          className={inputClassName}
                          aria-invalid={Boolean(fieldErrors[`items[${index}].productId`])}
                          id={`order-product-${line.key}`}
                          onChange={(event) => updateLine(line.key, {
                            productId: event.target.value,
                            variantId: "",
                          })}
                          value={line.productId}
                        >
                          <option value="">Select product</option>
                          {products.map((candidate) => (
                            <option key={candidate.id} value={candidate.id}>
                              {candidate.name} · {candidate.category.name}
                            </option>
                          ))}
                        </select>
                        {fieldErrors[`items[${index}].productId`] ? <span className="mt-2 block text-xs text-danger">{fieldErrors[`items[${index}].productId`]}</span> : null}
                      </label>

                      <div>
                        <label className="text-sm font-medium text-foreground" htmlFor={`order-variant-${line.key}`}>Size / color</label>
                        <select
                          className={inputClassName}
                          aria-invalid={Boolean(fieldErrors[`items[${index}].variantId`])}
                          disabled={!product}
                          id={`order-variant-${line.key}`}
                          onChange={(event) => updateLine(line.key, { variantId: event.target.value })}
                          value={line.variantId}
                        >
                          <option value="">Select size / color</option>
                          {product?.variants.map((candidate) => (
                            <option key={candidate.id} value={candidate.id}>
                              {candidate.size} · {candidate.color} · LKR {candidate.price}
                            </option>
                          ))}
                        </select>
                        {fieldErrors[`items[${index}].variantId`] ? <span className="mt-2 block text-xs text-danger">{fieldErrors[`items[${index}].variantId`]}</span> : null}
                      </div>

                      <label className="text-sm font-medium text-foreground" htmlFor={`order-quantity-${line.key}`}>
                        Quantity
                        <input
                          aria-invalid={Boolean(fieldErrors[`items[${index}].quantity`])}
                          className={inputClassName}
                          id={`order-quantity-${line.key}`}
                          inputMode="numeric"
                          min="1"
                          onChange={(event) => updateLine(line.key, { quantity: event.target.value })}
                          step="1"
                          type="number"
                          value={line.quantity}
                        />
                        {fieldErrors[`items[${index}].quantity`] ? <span className="mt-2 block text-xs text-danger">{fieldErrors[`items[${index}].quantity`]}</span> : null}
                      </label>
                    </div>
                    {variant ? (
                      <div className="mt-4 rounded-xl border border-primary bg-primary-soft p-4 text-sm text-primary">
                        Review: <strong>{product?.name}</strong> · {variant.size} · {variant.color} · LKR {variant.price} each
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          </section>

          {loadError ? <p className="rounded-xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">{loadError}</p> : null}
          {validationError ? <p role="alert" className="rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger">{validationError}</p> : null}
          {submissionError ? <p role="alert" className="rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger">{submissionError}</p> : null}

          <button
            className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
            disabled={isSubmitting || customers.length === 0}
            type="submit"
          >
            {isSubmitting ? "Saving order…" : "Save customer order"}
          </button>
        </form>
      </div>
    </section>
  );
}

import { useEffect, useRef, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";

import {
  createMyOrder,
  getOrderApiError,
  type CreateOrderResponse,
} from "../api/orders";
import { getCatalogProducts, getProductApiError } from "../api/products";
import { useAuth } from "../auth/useAuth";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { StatusBadge } from "../components/ui/StatusBadge";
import { ProductImage } from "../products/ProductImage";
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

function moneyTotal(unitPrice: string, quantity: number): bigint | null {
  if (!Number.isSafeInteger(quantity) || quantity <= 0) return null;
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(unitPrice);
  if (!match) return null;
  const cents = BigInt(match[1]) * 100n + BigInt((match[2] ?? "").padEnd(2, "0"));
  return cents * BigInt(quantity);
}

function formatMoney(cents: bigint | null) {
  if (cents === null) return "Unavailable";
  const amount = cents.toString().padStart(3, "0");
  return `LKR ${amount.slice(0, -2)}.${amount.slice(-2)}`;
}

export function CustomerPlaceOrderPage() {
  const { user } = useAuth();
  const [searchParameters] = useSearchParams();
  const requestedProductId = searchParameters.get("productId");
  const requestedVariantId = searchParameters.get("variantId");
  const nextLineKey = useRef(2);
  const hasAppliedCartSelection = useRef(false);
  const [products, setProducts] = useState<GarmentProductDetails[]>([]);
  const [lines, setLines] = useState<OrderLineDraft[]>([emptyLine(1)]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [savedOrder, setSavedOrder] = useState<CreateOrderResponse | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [cartMessage, setCartMessage] = useState<string | null>(null);
  const [isDownloadingReceipt, setIsDownloadingReceipt] = useState(false);
  const [receiptDownloadError, setReceiptDownloadError] = useState<string | null>(null);

  async function loadProducts(signal?: AbortSignal) {
    setIsLoading(true);
    setLoadError(null);
    try {
      setProducts(await getCatalogProducts({ availability: "AVAILABLE" }, signal));
    } catch (error: unknown) {
      if (signal?.aborted) return;
      setLoadError(getProductApiError(
        error,
        "Available garments could not be loaded. Please try again.",
      ).message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void loadProducts(controller.signal);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (
      hasAppliedCartSelection.current
      || products.length === 0
      || requestedProductId === null
      || requestedVariantId === null
    ) {
      return;
    }

    hasAppliedCartSelection.current = true;
    const productId = Number(requestedProductId);
    const variantId = Number(requestedVariantId);
    const product = Number.isSafeInteger(productId) && productId > 0
      ? products.find((candidate) => candidate.id === productId)
      : undefined;
    const variant = Number.isSafeInteger(variantId) && variantId > 0
      ? product?.variants.find((candidate) => candidate.id === variantId)
      : undefined;

    if (!product || !variant || variant.status !== "AVAILABLE") {
      setValidationError("The cart item is no longer available. Select another garment and size/colour.");
      return;
    }

    setLines([{
      key: 1,
      productId: String(product.id),
      variantId: String(variant.id),
      quantity: "1",
    }]);
    nextLineKey.current = 2;
    setCartMessage(`${product.name} was added from the garment catalog. Review the selection and quantity before placing your order.`);
  }, [products, requestedProductId, requestedVariantId]);

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
      setSavedOrder(await createMyOrder(items));
    } catch (error: unknown) {
      const orderError = getOrderApiError(
        error,
        "Your order could not be placed. Please review the selections and try again.",
      );
      setFieldErrors(orderError.fields);
      setSubmissionError(orderError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  function resetForm() {
    setLines([emptyLine(1)]);
    nextLineKey.current = 2;
    setSavedOrder(null);
    setSubmissionError(null);
    setValidationError(null);
    setFieldErrors({});
    setCartMessage(null);
    setReceiptDownloadError(null);
  }

  async function downloadReceipt() {
    if (!savedOrder) return;
    setIsDownloadingReceipt(true);
    setReceiptDownloadError(null);
    try {
      const { downloadOrderReceipt } = await import("./orderReceiptPdf");
      await downloadOrderReceipt(savedOrder);
    } catch {
      setReceiptDownloadError("The receipt PDF could not be prepared. Please try again.");
    } finally {
      setIsDownloadingReceipt(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          title="Loading garments…"
          message="Loading currently selectable garment variants for your order."
        />
      </div>
    );
  }

  if (loadError && products.length === 0) {
    return (
      <div className="mx-auto max-w-5xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          title="Order form unavailable"
          message={loadError}
          action={(
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground"
              onClick={() => void loadProducts()}
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
          title="No garments available to order"
          message="Product Management currently has no active garment with an available size/color variant."
        />
      </div>
    );
  }

  if (savedOrder) {
    return (
      <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <div className="mx-auto max-w-4xl rounded-3xl border border-success-border bg-success-soft p-7 sm:p-10">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-success">
            Order recorded
          </p>
          <h1 className="mt-3 text-3xl font-bold text-foreground">Order confirmed</h1>
          <p aria-live="polite" className="mt-3 text-success">
            Your order was recorded successfully for {savedOrder.customer.fullName}. Confirmation code: <strong>{savedOrder.orderNumber}</strong>.
          </p>
          <p className="mt-2 text-sm leading-6 text-muted">
            This confirms submission of the order. Its fulfilment status remains {savedOrder.status.toLowerCase()} until the responsible team records the next workflow step.
          </p>
          <dl className="mt-7 grid gap-4 rounded-2xl border border-success-border bg-background/60 p-5 sm:grid-cols-2 lg:grid-cols-4">
            <div><dt className="text-xs uppercase text-muted">Confirmation code</dt><dd className="mt-1 break-all font-semibold text-foreground">{savedOrder.orderNumber}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Status</dt><dd className="mt-1"><StatusBadge status={savedOrder.status} /></dd></div>
            <div><dt className="text-xs uppercase text-muted">Items</dt><dd className="mt-1 font-semibold text-foreground">{savedOrder.items.length}</dd></div>
            <div><dt className="text-xs uppercase text-muted">Order total</dt><dd className="mt-1 font-semibold text-foreground">LKR {savedOrder.totalAmount}</dd></div>
          </dl>

          <h2 className="mt-8 text-xl font-bold text-foreground">Items you selected</h2>
          <p className="mt-2 text-sm text-muted">Review the garments, variants, quantities, and confirmed order-time prices below.</p>
          <div className="mt-6 space-y-3">
            {savedOrder.items.map((item) => {
              const product = products.find((candidate) => candidate.id === item.productId);
              return (
                <article className="grid overflow-hidden rounded-2xl border border-border bg-background/60 sm:grid-cols-[9rem_1fr]" key={item.id}>
                  <ProductImage
                    alt={`${item.productName} selected garment`}
                    className="h-44 w-full border-b border-border sm:h-full sm:min-h-40 sm:border-b-0 sm:border-r"
                    productName={item.productName}
                    src={product?.imageUrl}
                  />
                  <div className="p-5">
                    {product?.category.name ? <p className="text-xs font-semibold uppercase tracking-[0.14em] text-primary">{product.category.name}</p> : null}
                    <h3 className="mt-1 text-lg font-bold text-foreground">{item.productName}</h3>
                    <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                      <div><dt className="text-muted">Size</dt><dd className="mt-1 font-semibold text-foreground">{item.selectedSize}</dd></div>
                      <div><dt className="text-muted">Colour</dt><dd className="mt-1 font-semibold text-foreground">{item.selectedColor}</dd></div>
                      <div><dt className="text-muted">Quantity</dt><dd className="mt-1 font-semibold text-foreground">{item.quantity}</dd></div>
                      <div><dt className="text-muted">Unit price</dt><dd className="mt-1 font-semibold text-foreground">LKR {item.unitPriceSnapshot}</dd></div>
                    </dl>
                    <p className="mt-4 border-t border-border pt-3 text-sm text-muted">
                      Line total: <strong className="text-foreground">LKR {item.lineTotal}</strong>
                    </p>
                  </div>
                </article>
              );
            })}
          </div>
          <div className="mt-8 flex flex-wrap gap-3">
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
              disabled={isDownloadingReceipt}
              onClick={() => void downloadReceipt()}
              type="button"
            >
              {isDownloadingReceipt ? "Preparing PDF…" : "Download receipt (PDF)"}
            </button>
            <button
              className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground transition hover:border-primary"
              onClick={resetForm}
              type="button"
            >
              Place another order
            </button>
          </div>
          {receiptDownloadError ? <p className="mt-4 text-sm text-danger" role="alert">{receiptDownloadError}</p> : null}
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
          Place an order
        </h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Signed in as <strong className="text-foreground">{user?.fullName}</strong>. Your account identity is taken from the authenticated session and cannot be changed in this form.
        </p>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Only active, available garment variants are offered. The server validates product availability and positive whole-number quantities again before saving and snapshots the approved size, color and price.
        </p>

        <form className="mt-8 space-y-8" noValidate onSubmit={handleSubmit}>
          {cartMessage ? (
            <p className="rounded-xl border border-primary/50 bg-primary-soft p-4 text-sm text-primary" role="status">
              {cartMessage}
            </p>
          ) : null}
          <section className="rounded-3xl border border-border bg-surface/70 p-6 sm:p-8">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-xl font-semibold text-foreground">Your order items</h2>
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
                      <label className="text-sm font-medium text-foreground" htmlFor={`customer-order-product-${line.key}`}>
                        Product
                        <select
                          aria-invalid={Boolean(fieldErrors[`items[${index}].productId`])}
                          className={inputClassName}
                          id={`customer-order-product-${line.key}`}
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
                        <label className="text-sm font-medium text-foreground" htmlFor={`customer-order-variant-${line.key}`}>Size / color</label>
                        <select
                          aria-invalid={Boolean(fieldErrors[`items[${index}].variantId`])}
                          className={inputClassName}
                          disabled={!product}
                          id={`customer-order-variant-${line.key}`}
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

                      <label className="text-sm font-medium text-foreground" htmlFor={`customer-order-quantity-${line.key}`}>
                        Quantity
                        <input
                          aria-invalid={Boolean(fieldErrors[`items[${index}].quantity`])}
                          className={inputClassName}
                          id={`customer-order-quantity-${line.key}`}
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
                    {product ? (
                      <div className="mt-5 grid overflow-hidden rounded-2xl border border-primary/60 bg-primary-soft sm:grid-cols-[8rem_1fr]">
                        <ProductImage
                          alt={`${product.name} selected item preview`}
                          className="h-40 w-full border-b border-primary/30 sm:h-full sm:min-h-36 sm:border-b-0 sm:border-r"
                          productName={product.name}
                          src={product.imageUrl}
                        />
                        <div className="p-4 sm:p-5">
                          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-primary">Selected item preview</p>
                          <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
                            <div>
                              <p className="font-bold text-foreground">{product.name}</p>
                              <p className="mt-1 text-sm text-muted">{product.category.name}</p>
                            </div>
                            {variant ? <StatusBadge status={variant.status} /> : null}
                          </div>
                          {variant ? (
                            <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                              <div><dt className="text-muted">Size</dt><dd className="mt-1 font-semibold text-foreground">{variant.size}</dd></div>
                              <div><dt className="text-muted">Colour</dt><dd className="mt-1 font-semibold text-foreground">{variant.color}</dd></div>
                              <div><dt className="text-muted">Quantity</dt><dd className="mt-1 font-semibold text-foreground">{line.quantity || "—"}</dd></div>
                              <div><dt className="text-muted">Line total</dt><dd className="mt-1 font-semibold text-foreground">{formatMoney(moneyTotal(variant.price, Number(line.quantity)))}</dd></div>
                            </dl>
                          ) : (
                            <p className="mt-4 text-sm text-primary">Select a size and colour to complete this item.</p>
                          )}
                        </div>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          </section>

          {loadError ? <p className="rounded-xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">{loadError}</p> : null}
          {fieldErrors.customerId ? <p role="alert" className="rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger">{fieldErrors.customerId}</p> : null}
          {validationError ? <p role="alert" className="rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger">{validationError}</p> : null}
          {submissionError ? <p role="alert" className="rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger">{submissionError}</p> : null}

          <button
            className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? "Placing order…" : "Place my order"}
          </button>
        </form>
      </div>
    </section>
  );
}

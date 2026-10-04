import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  createQuotation,
  getQuotationApiError,
  getQuotationCustomers,
  type CreateQuotationResponse,
  type QuotationCustomerOption,
} from "../api/quotations";
import { isApiRequestCanceled } from "../api/client";
import { getCatalogProducts, getProductApiError } from "../api/products";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { quotationListPagePath } from "../navigation/navigation";
import type { GarmentProductDetails, ProductVariant } from "../products/productTypes";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

interface LineDraft {
  key: number;
  productId: string;
  variantId: string;
  quantity: string;
}

function emptyLine(key: number): LineDraft {
  return { key, productId: "", variantId: "", quantity: "1" };
}

function productFor(products: GarmentProductDetails[], value: string) {
  return products.find((product) => product.id === Number(value));
}

function variantFor(product: GarmentProductDetails | undefined, value: string): ProductVariant | undefined {
  return product?.variants.find((variant) => variant.id === Number(value));
}

export function CreateQuotationPage() {
  const nextKey = useRef(2);
  const [customers, setCustomers] = useState<QuotationCustomerOption[]>([]);
  const [products, setProducts] = useState<GarmentProductDetails[]>([]);
  const [customerId, setCustomerId] = useState("");
  const [customerSearch, setCustomerSearch] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([emptyLine(1)]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [saved, setSaved] = useState<CreateQuotationResponse | null>(null);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setLoadError(null);
    try {
      const [customerOptions, catalog] = await Promise.all([
        getQuotationCustomers(undefined, signal),
        getCatalogProducts({}, signal),
      ]);
      if (!signal?.aborted) {
        setCustomers(customerOptions);
        setProducts(catalog);
      }
    } catch (error: unknown) {
      if (isApiRequestCanceled(error, signal)) return;
      const quoteError = getQuotationApiError(error, "Quotation form data could not be loaded.");
      setLoadError(getProductApiError(error, quoteError.message).message);
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, []);

  async function searchCustomers() {
    try {
      setCustomers(await getQuotationCustomers(customerSearch));
    } catch (error: unknown) {
      setLoadError(getQuotationApiError(error, "Customers could not be searched.").message);
    }
  }

  function updateLine(key: number, patch: Partial<LineDraft>) {
    setLines((current) => current.map((line) => line.key === key ? { ...line, ...patch } : line));
    setFieldErrors({});
    setSubmitError(null);
  }

  function addLine() {
    if (lines.length >= 50) {
      setSubmitError("A quotation may contain at most 50 items.");
      return;
    }
    setLines((current) => [...current, emptyLine(nextKey.current++)]);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFieldErrors({});
    setSubmitError(null);
    const errors: Record<string, string> = {};
    const parsedCustomer = Number(customerId);
    if (!Number.isSafeInteger(parsedCustomer) || parsedCustomer <= 0) {
      errors.customerId = "Select an active registered customer account.";
    }
    const items = lines.map((line, index) => {
      const productId = Number(line.productId);
      const variantId = Number(line.variantId);
      const quantity = Number(line.quantity);
      if (!Number.isSafeInteger(productId) || productId <= 0) errors[`items[${index}].productId`] = "Select a product.";
      if (!Number.isSafeInteger(variantId) || variantId <= 0) errors[`items[${index}].variantId`] = "Select an available size/color variant.";
      if (!Number.isSafeInteger(quantity) || quantity <= 0) errors[`items[${index}].quantity`] = "Quantity must be a positive whole number.";
      return { productId, variantId, quantity };
    });
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setSubmitError("Please correct the highlighted quotation fields.");
      return;
    }

    setSubmitting(true);
    try {
      setSaved(await createQuotation({ customerId: parsedCustomer, items }));
    } catch (error: unknown) {
      const apiError = getQuotationApiError(error, "The quotation could not be issued.");
      setFieldErrors(apiError.fields);
      setSubmitError(apiError.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><LoadingState title="Loading quotation form…" message="Loading active customers and current garment prices." /></div>;
  }
  if (loadError && customers.length === 0 && products.length === 0) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><ErrorState title="Quotation form unavailable" message={loadError} action={<button type="button" onClick={() => void load()} className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground">Retry</button>} /></div>;
  }
  if (products.length === 0) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><EmptyState title="No quotable garments" message="Product Management has no active garment variant available for a quotation." /></div>;
  }
  if (customers.length === 0 && !customerSearch.trim()) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><EmptyState title="No active customers" message="A quotation cannot be issued until an active registered customer account exists." /></div>;
  }
  if (saved) {
    return (
      <section className="mx-auto max-w-4xl px-5 py-10">
        <div className="rounded-3xl border border-success-border bg-success-soft p-8">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-success">Quotation issued</p>
          <h1 className="mt-3 text-3xl font-bold text-foreground">{saved.quotationNumber}</h1>
          <p className="mt-3 text-success">Quotation issued successfully for {saved.customer.fullName}.</p>
          <p className="mt-5 text-xl font-semibold text-foreground">Total: LKR {saved.totalAmount}</p>
          <div className="mt-6 space-y-3">
            {saved.items.map((item) => (
              <article key={item.id} className="rounded-2xl border border-border bg-background/60 p-4">
                <p className="font-semibold text-foreground">{item.productName}</p>
                <p className="mt-1 text-sm text-foreground-muted">{item.selectedSize} · {item.selectedColor} · Qty {item.quantity} · LKR {item.unitPriceSnapshot} each · Line LKR {item.lineTotal}</p>
              </article>
            ))}
          </div>
          <div className="mt-7 flex flex-wrap gap-3">
            <Link to={`${quotationListPagePath}/${saved.id}`} className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground">View quotation</Link>
            <button type="button" className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground" onClick={() => { setSaved(null); setCustomerId(""); setLines([emptyLine(1)]); nextKey.current = 2; }}>Prepare another quotation</button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="mx-auto max-w-5xl px-5 py-10">
      <div className="mb-8">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Order Management · TGMS-48</p>
        <h1 className="mt-2 text-3xl font-bold text-foreground">Prepare customer quotation</h1>
        <p className="mt-3 max-w-3xl text-foreground-muted">Issued quotations snapshot the current garment name, size, color and unit price. They are read-only after issue so later catalog changes cannot rewrite the quoted values.</p>
      </div>
      <form onSubmit={submit} className="space-y-7">
        <div className="rounded-3xl border border-border bg-surface/60 p-6">
          <h2 className="text-xl font-semibold text-foreground">Customer</h2>
          <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto]">
            <input aria-label="Customer search" className={inputClassName} value={customerSearch} onChange={(event) => setCustomerSearch(event.target.value)} placeholder="Search customer name or email" />
            <button type="button" onClick={() => void searchCustomers()} className="self-end rounded-xl border border-border px-5 py-3 font-semibold text-foreground">Search</button>
          </div>
          <select aria-invalid={Boolean(fieldErrors.customerId)} aria-label="Customer" className={inputClassName} value={customerId} onChange={(event) => { setCustomerId(event.target.value); setFieldErrors({}); }}>
            <option value="">Select customer</option>
            {customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.fullName} · {customer.email}</option>)}
          </select>
          {fieldErrors.customerId && <p className="mt-2 text-sm text-danger">{fieldErrors.customerId}</p>}
          {customers.length === 0 && customerSearch.trim() && <p className="mt-2 text-sm text-warning">No active customers match this search.</p>}
        </div>

        <div className="space-y-4">
          {lines.map((line, index) => {
            const product = productFor(products, line.productId);
            const variant = variantFor(product, line.variantId);
            const prefix = `items[${index}]`;
            const quantity = Number(line.quantity);
            const lineTotal = variant && Number.isFinite(quantity) && quantity > 0 ? (Number(variant.price) * quantity).toFixed(2) : null;
            return (
              <article key={line.key} className="rounded-3xl border border-border bg-surface/60 p-6">
                <div className="flex items-center justify-between"><h2 className="text-lg font-semibold text-foreground">Quoted item {index + 1}</h2>{lines.length > 1 && <button type="button" className="text-sm text-danger" onClick={() => setLines((current) => current.filter((entry) => entry.key !== line.key))}>Remove</button>}</div>
                <div className="mt-4 grid gap-4 md:grid-cols-3">
                  <label className="text-sm text-foreground-muted">Product<select aria-invalid={Boolean(fieldErrors[`${prefix}.productId`])} className={inputClassName} value={line.productId} onChange={(event) => updateLine(line.key, { productId: event.target.value, variantId: "" })}><option value="">Select garment</option>{products.map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select>{fieldErrors[`${prefix}.productId`] && <span className="mt-2 block text-danger">{fieldErrors[`${prefix}.productId`]}</span>}</label>
                  <div><label className="text-sm text-foreground-muted" htmlFor={`quotation-variant-${line.key}`}>Size / color</label><select aria-invalid={Boolean(fieldErrors[`${prefix}.variantId`])} className={inputClassName} id={`quotation-variant-${line.key}`} value={line.variantId} onChange={(event) => updateLine(line.key, { variantId: event.target.value })} disabled={!product}><option value="">Select variant</option>{product?.variants.filter((entry) => entry.status === "AVAILABLE").map((entry) => <option key={entry.id} value={entry.id}>{entry.size} · {entry.color} · LKR {entry.price}</option>)}</select>{fieldErrors[`${prefix}.variantId`] && <span className="mt-2 block text-danger">{fieldErrors[`${prefix}.variantId`]}</span>}</div>
                  <label className="text-sm text-foreground-muted">Quantity<input aria-invalid={Boolean(fieldErrors[`${prefix}.quantity`])} className={inputClassName} min="1" step="1" type="number" value={line.quantity} onChange={(event) => updateLine(line.key, { quantity: event.target.value })} />{fieldErrors[`${prefix}.quantity`] && <span className="mt-2 block text-danger">{fieldErrors[`${prefix}.quantity`]}</span>}</label>
                </div>
                {variant && <p className="mt-4 rounded-xl bg-background/70 p-3 text-sm text-foreground-muted">Current price review: LKR {variant.price} each{lineTotal ? ` · Line total LKR ${lineTotal}` : ""}. The server will snapshot this value when the quotation is issued.</p>}
              </article>
            );
          })}
        </div>
        <button type="button" onClick={addLine} className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground">Add quoted item</button>
        {submitError && <p role="alert" className="rounded-xl border border-danger-border bg-danger-soft p-4 text-danger">{submitError}</p>}
        <button type="submit" disabled={submitting} className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground disabled:opacity-60">{submitting ? "Issuing…" : "Issue quotation"}</button>
      </form>
    </section>
  );
}

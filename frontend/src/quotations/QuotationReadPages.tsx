import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import {
  getQuotation,
  getQuotationApiError,
  getQuotations,
  type QuotationDetail,
  type QuotationSummary,
} from "../api/quotations";
import { isApiRequestCanceled } from "../api/client";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import {
  getQuotationDetailPagePath,
  newQuotationPagePath,
  parseQuotationPageId,
  quotationListPagePath,
} from "../navigation/navigation";

export function QuotationListPage() {
  const [quotations, setQuotations] = useState<QuotationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError(null);
    try {
      const result = await getQuotations(signal);
      if (!signal?.aborted) {
        setQuotations(result);
      }
    } catch (failure: unknown) {
      if (isApiRequestCanceled(failure, signal)) return;
      setError(getQuotationApiError(failure, "Quotations could not be loaded.").message);
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

  if (loading) {
    return <div className="mx-auto max-w-6xl px-5 py-10"><LoadingState title="Loading quotations…" message="Loading issued customer quotations." /></div>;
  }
  if (error) {
    return <div className="mx-auto max-w-6xl px-5 py-10"><ErrorState title="Quotations unavailable" message={error} action={<button type="button" onClick={() => void load()} className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground">Retry</button>} /></div>;
  }
  return (
    <section className="mx-auto max-w-6xl px-5 py-10">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div><p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Order Management · TGMS-48</p><h1 className="mt-2 text-3xl font-bold text-foreground">Customer quotations</h1><p className="mt-3 text-foreground-muted">Issued quotations are read-only historical snapshots.</p></div>
        <Link to={newQuotationPagePath} className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground">Prepare quotation</Link>
      </div>
      {quotations.length === 0 ? (
        <div className="mt-8"><EmptyState title="No quotations issued" message="Prepare a quotation to create the first issued customer quotation record." /></div>
      ) : (
        <div className="mt-8 grid gap-4 lg:grid-cols-2">
          {quotations.map((quotation) => (
            <article key={quotation.id} className="motion-record rounded-3xl border border-border bg-surface/60 p-6">
              <p className="text-xs uppercase tracking-[0.16em] text-muted">Quotation #{quotation.id}</p>
              <h2 className="mt-2 text-xl font-semibold text-foreground">{quotation.quotationNumber}</h2>
              <p className="mt-3 text-foreground-muted">{quotation.customerName} · {quotation.customerEmail}</p>
              <div className="mt-4 grid grid-cols-2 gap-3 text-sm"><div><span className="text-muted">Items</span><p className="font-semibold text-foreground">{quotation.itemCount}</p></div><div><span className="text-muted">Total</span><p className="font-semibold text-foreground">LKR {quotation.totalAmount}</p></div></div>
              <p className="mt-4 text-xs text-muted">Issued {new Date(quotation.issuedAt).toLocaleString()}</p>
              <Link to={getQuotationDetailPagePath(quotation.id)} className="mt-5 inline-block rounded-xl border border-border px-4 py-2 font-semibold text-foreground">View quotation</Link>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

export function QuotationDetailPage() {
  const { quotationId } = useParams();
  const id = parseQuotationPageId(quotationId);
  const [quotation, setQuotation] = useState<QuotationDetail | null>(null);
  const [loading, setLoading] = useState(Boolean(id));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    setQuotation(null);
    void getQuotation(id, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setQuotation(result);
      })
      .catch((failure: unknown) => {
        if (isApiRequestCanceled(failure, controller.signal)) return;
        setError(getQuotationApiError(failure, "Quotation could not be loaded.").message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id]);

  if (!id) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><ErrorState title="Quotation unavailable" message="The quotation ID is invalid." /></div>;
  }
  if (loading) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><LoadingState title="Loading quotation…" message="Loading the immutable quotation snapshot." /></div>;
  }
  if (error || !quotation) {
    return <div className="mx-auto max-w-5xl px-5 py-10"><ErrorState title="Quotation unavailable" message={error ?? "Quotation was not found."} action={<Link to={quotationListPagePath} className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground">Back to quotations</Link>} /></div>;
  }

  return (
    <section className="mx-auto max-w-5xl px-5 py-10">
      <Link to={quotationListPagePath} className="text-sm font-semibold text-primary">← Customer quotations</Link>
      <div className="mt-5 rounded-3xl border border-border bg-surface/60 p-7">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Issued quotation · read only</p>
        <h1 className="mt-2 text-3xl font-bold text-foreground">{quotation.quotationNumber}</h1>
        <p className="mt-3 text-foreground-muted">{quotation.customerName} · {quotation.customerEmail}</p>
        <p className="mt-2 text-sm text-muted">Issued {new Date(quotation.issuedAt).toLocaleString()}</p>
        <div className="mt-7 space-y-4">
          {quotation.items.map((item) => (
            <article key={item.id} className="rounded-2xl border border-border bg-background/60 p-5">
              <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-semibold text-foreground">{item.productName}</h2><p className="mt-1 text-sm text-foreground-muted">{item.selectedSize} · {item.selectedColor} · Qty {item.quantity}</p></div><p className="font-semibold text-foreground">LKR {item.lineTotal}</p></div>
              <p className="mt-2 text-xs text-muted">Issued unit price snapshot: LKR {item.unitPriceSnapshot}</p>
            </article>
          ))}
        </div>
        <div className="mt-7 flex justify-between border-t border-border pt-5 text-xl font-bold text-foreground"><span>Quoted total</span><span>LKR {quotation.totalAmount}</span></div>
        <p className="mt-4 text-sm text-muted">This issued quotation is immutable. Product catalog changes do not rewrite the stored quoted values.</p>
      </div>
    </section>
  );
}

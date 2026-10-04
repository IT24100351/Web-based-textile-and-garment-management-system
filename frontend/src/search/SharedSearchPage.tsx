import axios from "axios";
import { FormEvent, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";

import { searchCoreRecords, type SharedSearchPage as SearchPage } from "../api/search";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { MotionSwap } from "../motion/MotionSwap";

const moduleLabels = {
  PRODUCT: "Garment product",
  SUPPLY: "Material supply",
  INVENTORY: "Inventory material",
  ORDER: "Order",
  DELIVERY: "Delivery",
} as const;

function safeErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const fields = error.response?.data?.error?.fields as Record<string, string> | undefined;
    return fields?.search ?? error.response?.data?.error?.message ?? "Search could not be completed.";
  }
  return "Search could not be completed.";
}

export function SharedSearchPage() {
  const [params, setParams] = useSearchParams();
  const query = params.get("search") ?? "";
  const page = Math.max(0, Number(params.get("page") ?? "0") || 0);
  const [draft, setDraft] = useState(query);
  const [result, setResult] = useState<SearchPage | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraft(query);
    if (query.trim().length < 2) {
      setResult(null);
      setError(null);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    setError(null);
    void searchCoreRecords(query, page, 10)
      .then((data) => {
        if (!cancelled) setResult(data);
      })
      .catch((requestError) => {
        if (!cancelled) {
          setResult(null);
          setError(safeErrorMessage(requestError));
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [query, page]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const normalized = draft.trim().replace(/\s+/g, " ");
    if (normalized.length < 2) {
      setError("Enter at least 2 characters to search.");
      return;
    }
    setParams({ search: normalized, page: "0" });
  }

  function goToPage(nextPage: number) {
    setParams({ search: query, page: String(nextPage) });
  }

  const hasVisibleResults = Boolean(result && result.results.length > 0);
  const resultStateKey = error
    ? "error"
    : query.trim().length < 2
      ? "prompt"
      : hasVisibleResults
        ? "content"
        : isLoading
          ? "loading"
          : result
            ? "empty"
            : "prompt";

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6 sm:p-10">
      <section className="rounded-3xl border border-border bg-surface/80 p-7 shadow-xl shadow-black/10">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Shared search</p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground">Search permitted core records</h1>
        <p className="mt-3 max-w-3xl leading-7 text-foreground-muted">
          Search uses each module&apos;s existing server-side rules. Results are limited to records your current role is already permitted to read.
        </p>
        <form className="mt-6 flex flex-col gap-3 sm:flex-row" onSubmit={submit}>
          <label className="sr-only" htmlFor="shared-search">Search records</label>
          <input
            autoComplete="off"
            className="min-w-0 flex-1 rounded-2xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
            id="shared-search"
            maxLength={120}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Order number, material code, product name…"
            value={draft}
          />
          <button className="rounded-2xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover" type="submit">
            Search
          </button>
        </form>
      </section>

      <div aria-busy={isLoading}>
        <MotionSwap stateKey={resultStateKey}>
          {error ? (
            <ErrorState message={error} title="Search unavailable" />
          ) : query.trim().length < 2 ? (
            <EmptyState message="Enter at least two characters. Search will run only after you submit the query." title="Start a search" />
          ) : hasVisibleResults && result ? (
            <section className="space-y-4" aria-live="polite">
              {isLoading ? <p className="text-sm text-muted" role="status">Updating search results…</p> : null}
              <div className="flex flex-wrap items-end justify-between gap-3">
                <div>
                  <p className="text-sm text-muted">{result.totalResults} permitted result{result.totalResults === 1 ? "" : "s"}</p>
                  <h2 className="text-xl font-semibold text-foreground">Results for “{result.search}”</h2>
                </div>
                <p className="text-sm text-muted">Page {result.page + 1} of {Math.max(result.totalPages, 1)}</p>
              </div>
              <div className="grid gap-3">
                {result.results.map((item) => (
                  <Link
                    className="motion-record motion-interactive-card rounded-2xl border border-border bg-surface/70 p-5 hover:border-primary/60 hover:bg-surface"
                    key={`${item.module}-${item.recordId}`}
                    to={item.path}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{moduleLabels[item.module]}</p>
                        <h3 className="mt-1 text-lg font-semibold text-foreground">{item.title}</h3>
                        <p className="mt-1 text-sm text-muted">{item.subtitle}</p>
                      </div>
                      <span className="rounded-full border border-border px-3 py-1 text-xs font-semibold text-foreground-muted">{item.status.replaceAll("_", " ")}</span>
                    </div>
                  </Link>
                ))}
              </div>
              <div className="flex justify-between gap-3">
                <button
                  className="rounded-xl border border-border px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={result.page === 0}
                  onClick={() => goToPage(result.page - 1)}
                  type="button"
                >Previous</button>
                <button
                  className="rounded-xl border border-border px-4 py-2 text-sm font-semibold text-foreground disabled:cursor-not-allowed disabled:opacity-40"
                  disabled={result.page + 1 >= result.totalPages}
                  onClick={() => goToPage(result.page + 1)}
                  type="button"
                >Next</button>
              </div>
            </section>
          ) : isLoading ? (
            <LoadingState message="Searching permitted module records…" title="Searching" />
          ) : result ? (
            <EmptyState message={`No permitted records matched “${result.search}”.`} title="No matching records" />
          ) : (
            <EmptyState message="Submit a search to load permitted records." title="Start a search" />
          )}
        </MotionSwap>
      </div>
    </div>
  );
}

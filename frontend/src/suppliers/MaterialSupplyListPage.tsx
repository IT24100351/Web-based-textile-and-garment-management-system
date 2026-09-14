import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  getMaterialSupplies,
  getMaterialSupplyApiError,
  type MaterialSupplyListFilters,
  type MaterialSupplyListItem,
  type MaterialSupplyStatus,
} from "../api/materialSupplies";
import { useAuth } from "../auth/useAuth";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  getEditMaterialSupplyPagePath,
  newMaterialSupplyPagePath,
} from "../navigation/navigation";

interface SupplyFilterForm {
  search: string;
  status: "" | MaterialSupplyStatus;
}

const emptyFilterForm: SupplyFilterForm = { search: "", status: "" };
const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20";

function buildFilters(form: SupplyFilterForm): MaterialSupplyListFilters {
  const filters: MaterialSupplyListFilters = {};
  const search = form.search.trim();
  if (search) filters.search = search;
  if (form.status) filters.status = form.status;
  return filters;
}

function hasFilters(filters: MaterialSupplyListFilters) {
  return Object.keys(filters).length > 0;
}

function optionalText(value: string | null) {
  return value ?? "Not provided";
}

function SupplyCard({ supply, showSupplier }: {
  supply: MaterialSupplyListItem;
  showSupplier: boolean;
}) {
  return (
    <li className="motion-record rounded-3xl border border-border bg-surface/75 p-6 shadow-xl shadow-black/10">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            {supply.materialCode}
          </p>
          <h2 className="mt-2 text-2xl font-bold text-foreground">{supply.materialName}</h2>
          {showSupplier ? (
            <p className="mt-2 text-sm text-muted">
              {supply.supplierBusinessName} · Supplier ID {supply.supplierId}
            </p>
          ) : null}
        </div>
        <StatusBadge status={supply.status} />
      </div>
      <p className="mt-4 leading-6 text-foreground-muted">{optionalText(supply.materialDescription)}</p>
      <dl className="mt-5 grid gap-4 border-t border-border pt-5 sm:grid-cols-3">
        <div>
          <dt className="text-xs uppercase tracking-wide text-muted">Quantity</dt>
          <dd className="mt-1 font-semibold text-foreground">
            {supply.quantity} {supply.unitOfMeasure}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-muted">Unit price</dt>
          <dd className="mt-1 font-semibold text-foreground">{supply.unitPrice}</dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-muted">Lead time</dt>
          <dd className="mt-1 font-semibold text-foreground">
            {supply.deliveryLeadTimeDays} days
          </dd>
        </div>
      </dl>
      <p className="mt-4 text-sm text-muted">
        Delivery: {optionalText(supply.deliveryNotes)}
      </p>
      {!showSupplier ? (
        <Link
          className="mt-5 inline-flex rounded-full border border-primary/50 px-4 py-2 text-sm font-semibold text-primary transition hover:border-primary hover:text-foreground"
          to={getEditMaterialSupplyPagePath(supply.id)}
        >
          {supply.status === "DISCONTINUED" ? "View archived supply" : "Edit supply details"}
        </Link>
      ) : null}
    </li>
  );
}

export function MaterialSupplyListPage() {
  const { user } = useAuth();
  const [filterForm, setFilterForm] = useState<SupplyFilterForm>(emptyFilterForm);
  const [appliedFilters, setAppliedFilters] = useState<MaterialSupplyListFilters>({});
  const [supplies, setSupplies] = useState<MaterialSupplyListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void getMaterialSupplies(appliedFilters, controller.signal)
      .then((records) => {
        if (!controller.signal.aborted) setSupplies(records);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setErrorMessage(getMaterialSupplyApiError(
            error,
            "Material supply records could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [appliedFilters, requestVersion]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setIsLoading(true);
    setAppliedFilters(buildFilters(filterForm));
  }

  function clearFilters() {
    setFilterForm(emptyFilterForm);
    setErrorMessage(null);
    setIsLoading(true);
    setAppliedFilters({});
  }

  function retry() {
    setErrorMessage(null);
    setIsLoading(true);
    setRequestVersion((current) => current + 1);
  }

  const supplierView = user?.role === "SUPPLIER";
  const filtersAreActive = hasFilters(appliedFilters);

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-6xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Supplier Management
        </p>
        <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
              {supplierView ? "My material supplies" : "Material supplies"}
            </h1>
            <p className="mt-3 max-w-3xl leading-7 text-muted">
              {supplierView
                ? "Verify the materials recorded under your authenticated supplier profile."
                : "Find supplier material, quantity, price, and delivery information."}
            </p>
          </div>
          {supplierView ? (
            <Link
              aria-label="Create a material supply"
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              to={newMaterialSupplyPagePath}
            >
              Add material supply
            </Link>
          ) : null}
        </div>

        <form
          className="mt-8 rounded-3xl border border-border bg-surface/75 p-5 sm:p-6"
          onSubmit={applyFilters}
        >
          <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_16rem]">
            <label className="text-sm font-medium text-foreground" htmlFor="supply-search">
              Search supply records
              <input
                className={inputClassName}
                id="supply-search"
                maxLength={160}
                onChange={(event) => setFilterForm((current) => ({
                  ...current,
                  search: event.target.value,
                }))}
                placeholder="Code, material, unit, or delivery notes"
                type="search"
                value={filterForm.search}
              />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="supply-status">
              Status
              <select
                className={inputClassName}
                id="supply-status"
                onChange={(event) => setFilterForm((current) => ({
                  ...current,
                  status: event.target.value as SupplyFilterForm["status"],
                }))}
                value={filterForm.status}
              >
                <option value="">Any status</option>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
                <option value="DISCONTINUED">Discontinued</option>
              </select>
            </label>
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <button
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              type="submit"
            >
              Search supplies
            </button>
            <button
              className="rounded-full border border-border px-5 py-2.5 text-sm font-semibold text-foreground transition hover:border-border hover:text-foreground"
              onClick={clearFilters}
              type="button"
            >
              Clear filters
            </button>
          </div>
        </form>

        <div className="mt-8">
          {isLoading ? (
            <LoadingState
              message="Fetching the material supplies permitted for your account."
              title="Loading material supplies"
            />
          ) : errorMessage ? (
            <ErrorState
              action={(
                <button
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  onClick={retry}
                  type="button"
                >
                  Retry
                </button>
              )}
              message={errorMessage}
              title="Material supplies unavailable"
            />
          ) : supplies.length === 0 ? (
            <EmptyState
              action={filtersAreActive ? (
                <button
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  onClick={clearFilters}
                  type="button"
                >
                  Clear filters
                </button>
              ) : supplierView ? (
                <Link
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  to={newMaterialSupplyPagePath}
                >
                  Add first supply
                </Link>
              ) : undefined}
              message={filtersAreActive
                ? "No permitted material supplies match both filters."
                : supplierView
                  ? "Your supplier profile has no material supply records yet."
                  : "No supplier material supply records have been created yet."}
              title={filtersAreActive ? "No matching supplies" : "No material supplies"}
            />
          ) : (
            <>
              <p aria-live="polite" className="text-sm text-muted">
                {supplies.length} {supplies.length === 1 ? "supply" : "supplies"} found
              </p>
              <ul className="mt-5 grid gap-5 lg:grid-cols-2">
                {supplies.map((supply) => (
                  <SupplyCard key={supply.id} showSupplier={!supplierView} supply={supply} />
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </section>
  );
}

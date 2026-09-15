import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  archiveInventoryMaterial,
  consumeInventoryMaterial,
  getInventoryMaterialApiError,
  getInventoryMaterials,
  getLowStockInventoryMaterials,
  type InventoryMaterial,
  type InventoryMaterialListFilters,
  type InventoryMaterialStatus,
  type InventoryMaterialType,
} from "../api/inventoryMaterials";
import {
  getMaterialSupplies,
  type MaterialSupplyListItem,
} from "../api/materialSupplies";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { Badge } from "../components/ui/Badge";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  getEditInventoryMaterialPagePath,
  newInventoryMaterialPagePath,
} from "../navigation/navigation";

function materialTypeLabel(type: InventoryMaterialType) {
  return type === "FABRIC" ? "Fabric" : "Raw material";
}

function stockStateLabel(material: InventoryMaterial) {
  return material.stockState === "LOW_STOCK" ? "Low stock" : "Sufficient stock";
}

function stockStateClassName(material: InventoryMaterial) {
  return material.stockState === "LOW_STOCK"
    ? "border-warning-border bg-warning-soft text-warning"
    : "border-success-border bg-success-soft text-success";
}

function optionalText(value: string | null) {
  return value ?? "Not provided";
}

function validateUsageQuantity(value: string) {
  const normalized = value.trim();
  if (!/^\d+(?:\.\d{1,3})?$/.test(normalized) || Number(normalized) <= 0) {
    return "Usage quantity must be greater than zero with at most 3 decimal places.";
  }
  return null;
}

function InventoryMaterialCard({
  material,
  sourceSupply,
  onConsumed,
  onArchived,
}: {
  material: InventoryMaterial;
  sourceSupply?: MaterialSupplyListItem;
  onConsumed: (material: InventoryMaterial) => void;
  onArchived: (material: InventoryMaterial) => void;
}) {
  const [usageQuantity, setUsageQuantity] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isArchiving, setIsArchiving] = useState(false);
  const [isArchiveConfirmOpen, setIsArchiveConfirmOpen] = useState(false);
  const canUseStock = material.status === "ACTIVE";

  async function archiveMaterial() {
    if (material.status === "DISCONTINUED") return;
    setRequestError(null);
    setSuccessMessage(null);
    setIsArchiving(true);
    try {
      const result = await archiveInventoryMaterial(material.id);
      onArchived(result.material);
      setSuccessMessage(result.message);
      setIsArchiveConfirmOpen(false);
    } catch (error: unknown) {
      setRequestError(getInventoryMaterialApiError(
        error,
        "Inventory material could not be archived. Please try again.",
      ).message);
    } finally {
      setIsArchiving(false);
    }
  }

  async function consumeStock(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validationError = validateUsageQuantity(usageQuantity);
    setFieldError(validationError);
    setRequestError(null);
    setSuccessMessage(null);
    if (validationError) return;

    setIsSubmitting(true);
    try {
      const result = await consumeInventoryMaterial(material.id, usageQuantity.trim());
      onConsumed(result.material);
      setUsageQuantity("");
      setSuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getInventoryMaterialApiError(
        error,
        "Stock usage could not be recorded. Please try again.",
      );
      const quantityError = apiError.fields.quantity ?? null;
      setFieldError(quantityError);
      setRequestError(quantityError ? null : apiError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <li className="motion-record rounded-3xl border border-border bg-surface/75 p-6 shadow-xl shadow-black/10">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
            {material.materialCode}
          </p>
          <h2 className="mt-2 text-2xl font-bold text-foreground">{material.materialName}</h2>
          <p className="mt-2 text-sm text-muted">
            Material ID {material.id} · {materialTypeLabel(material.materialType)}
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Badge className={stockStateClassName(material)}>{stockStateLabel(material)}</Badge>
          <StatusBadge label={material.status} status={material.status} />
        </div>
      </div>
      <p className="mt-4 leading-6 text-foreground-muted">
        {optionalText(material.materialDescription)}
      </p>
      <dl className="mt-5 grid gap-4 border-t border-border pt-5 sm:grid-cols-3">
        <div>
          <dt className="text-xs uppercase tracking-wide text-muted">Current quantity</dt>
          <dd className="mt-1 font-semibold text-foreground">
            {material.currentQuantity} {material.unitOfMeasure}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-muted">Low-stock threshold</dt>
          <dd className="mt-1 font-semibold text-foreground">
            {material.lowStockThreshold} {material.unitOfMeasure}
          </dd>
        </div>
        <div>
          <dt className="text-xs uppercase tracking-wide text-muted">Supplier source</dt>
          {sourceSupply ? (
            <dd className="mt-1 text-foreground">
              <span className="block font-semibold">{sourceSupply.supplierBusinessName}</span>
              <span className="mt-1 block text-sm text-muted">
                {sourceSupply.materialCode} · Supply ID {sourceSupply.id}
              </span>
            </dd>
          ) : material.sourceMaterialSupplyId ? (
            <dd className="mt-1 text-foreground">
              <span className="block font-semibold">Supply ID {material.sourceMaterialSupplyId}</span>
              <span className="mt-1 block text-sm text-warning">
                Supplier details unavailable
              </span>
            </dd>
          ) : (
            <dd className="mt-1 font-semibold text-foreground">No supplier link</dd>
          )}
        </div>
      </dl>

      <form className="mt-6 border-t border-border pt-5" noValidate onSubmit={consumeStock}>
        <div className="flex flex-wrap items-end gap-3">
          <label className="min-w-0 flex-1 text-sm font-medium text-foreground">
            Usage quantity
            <span className="mt-1 block text-xs font-normal text-muted">
              Record material used in {material.unitOfMeasure}.
            </span>
            <input
              aria-label={`Use quantity for ${material.materialName}`}
              className="mt-2 w-full rounded-xl border border-border bg-background px-3 py-2 text-foreground outline-none transition focus:border-primary"
              disabled={!canUseStock}
              inputMode="decimal"
              min="0.001"
              onChange={(event) => {
                setUsageQuantity(event.target.value);
                setFieldError(null);
                setRequestError(null);
                setSuccessMessage(null);
              }}
              placeholder="0.000"
              step="0.001"
              type="text"
              value={usageQuantity}
            />
          </label>
          <button
            aria-label={`Use stock for ${material.materialName}`}
            className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={isSubmitting || !canUseStock}
            type="submit"
          >
            {isSubmitting ? "Recording…" : "Use stock"}
          </button>
        </div>
        {fieldError ? (
          <p className="mt-2 text-sm text-danger" role="alert">{fieldError}</p>
        ) : null}
        {requestError ? (
          <p className="mt-2 text-sm text-danger" role="alert">{requestError}</p>
        ) : null}
        {!canUseStock ? (
          <p className="mt-2 text-sm text-warning">
            Stock usage is disabled while this material is {material.status.toLowerCase()}.
          </p>
        ) : null}
        {successMessage ? (
          <p className="mt-2 text-sm text-success" role="status">{successMessage}</p>
        ) : null}
      </form>

      <div className="mt-5 flex flex-wrap gap-3 border-t border-border pt-5">
        <Link
          className="rounded-full border border-border px-4 py-2 text-sm font-semibold text-foreground transition hover:border-border"
          to={getEditInventoryMaterialPagePath(material.id)}
        >
          {material.status === "DISCONTINUED" ? "View archived record" : "Edit metadata"}
        </Link>
        {material.status !== "DISCONTINUED" ? (
          <button
            className="rounded-full border border-danger-border px-4 py-2 text-sm font-semibold text-danger transition hover:bg-danger-soft disabled:opacity-50"
            disabled={isArchiving}
            onClick={() => setIsArchiveConfirmOpen(true)}
            type="button"
          >
            {isArchiving ? "Archiving…" : "Archive material"}
          </button>
        ) : (
          <span className="rounded-full border border-border px-4 py-2 text-sm font-semibold text-muted">Archived · history preserved</span>
        )}
      </div>
      <ConfirmDialog
        cancelLabel="Keep material active"
        confirmLabel="Archive material"
        description={`Archive ${material.materialName}? The record will remain available for historical reporting.`}
        isBusy={isArchiving}
        isOpen={isArchiveConfirmOpen}
        onCancel={() => setIsArchiveConfirmOpen(false)}
        onConfirm={() => void archiveMaterial()}
        title="Archive inventory material?"
      />
    </li>
  );
}

export function InventoryMaterialListPage() {
  const [materials, setMaterials] = useState<InventoryMaterial[]>([]);
  const [lowStockMaterials, setLowStockMaterials] = useState<InventoryMaterial[]>([]);
  const [sourceSupplies, setSourceSupplies] = useState<MaterialSupplyListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [requestVersion, setRequestVersion] = useState(0);
  const [searchInput, setSearchInput] = useState("");
  const [statusInput, setStatusInput] = useState<InventoryMaterialStatus | "">("");
  const [typeInput, setTypeInput] = useState<InventoryMaterialType | "">("");
  const [filters, setFilters] = useState<InventoryMaterialListFilters>({});

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setErrorMessage(null);
    void Promise.all([
      getInventoryMaterials(filters, controller.signal),
      getLowStockInventoryMaterials(controller.signal),
      getMaterialSupplies({}, controller.signal),
    ])
      .then(([records, lowStockResponse, supplies]) => {
        if (!controller.signal.aborted) {
          setMaterials(records);
          setLowStockMaterials(lowStockResponse.materials);
          setSourceSupplies(supplies);
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setErrorMessage(getInventoryMaterialApiError(
            error,
            "Inventory materials, low-stock state, or supplier references could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [filters, requestVersion]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const search = searchInput.trim();
    setFilters({
      search: search || undefined,
      status: statusInput || undefined,
      materialType: typeInput || undefined,
    });
  }

  function clearFilters() {
    setSearchInput("");
    setStatusInput("");
    setTypeInput("");
    setFilters({});
  }

  function retry() {
    setRequestVersion((current) => current + 1);
  }

  function updateConsumedMaterial(updatedMaterial: InventoryMaterial) {
    setMaterials((current) => current.map((material) => (
      material.id === updatedMaterial.id ? updatedMaterial : material
    )));
    setLowStockMaterials((current) => {
      const withoutUpdated = current.filter((material) => material.id !== updatedMaterial.id);
      return updatedMaterial.stockState === "LOW_STOCK"
        ? [updatedMaterial, ...withoutUpdated]
        : withoutUpdated;
    });
  }

  function updateArchivedMaterial(updatedMaterial: InventoryMaterial) {
    setMaterials((current) => current.map((material) => (
      material.id === updatedMaterial.id ? updatedMaterial : material
    )));
    setLowStockMaterials((current) => current.filter((material) => material.id !== updatedMaterial.id));
  }

  const supplyById = new Map(sourceSupplies.map((supply) => [supply.id, supply]));
  const hasFilters = Boolean(filters.search || filters.status || filters.materialType);

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-6xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Fabric Inventory Management
        </p>
        <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
              Inventory materials
            </h1>
            <p className="mt-3 max-w-3xl leading-7 text-muted">
              Find fabric and raw-material stock by material details, status, or type and review
              the exact current quantity stored for production use.
            </p>
          </div>
          <Link
            aria-label="Create inventory material"
            className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
            to={newInventoryMaterialPagePath}
          >
            Add inventory material
          </Link>
        </div>

        {!isLoading && !errorMessage ? (
          <section
            aria-label="Low-stock monitoring"
            className={`mt-8 rounded-3xl border p-5 ${
              lowStockMaterials.length > 0
                ? "border-warning-border bg-warning-soft"
                : "border-success-border bg-success-soft"
            }`}
          >
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted">
                  Replenishment monitoring
                </p>
                <h2 className="mt-2 text-xl font-bold text-foreground">Low-stock alert state</h2>
              </div>
              <span
                className={`rounded-full px-3 py-1 text-sm font-semibold ${
                  lowStockMaterials.length > 0
                    ? "bg-warning-soft text-warning"
                    : "bg-success-soft text-success"
                }`}
              >
                {lowStockMaterials.length} low-stock {
                  lowStockMaterials.length === 1 ? "material" : "materials"
                }
              </span>
            </div>
            {lowStockMaterials.length > 0 ? (
              <>
                <p className="mt-3 text-sm leading-6 text-warning/90">
                  These materials are at or below their configured low-stock threshold and need
                  replenishment review.
                </p>
                <ul className="mt-4 grid gap-2 sm:grid-cols-2">
                  {lowStockMaterials.map((material) => (
                    <li
                      className="rounded-2xl border border-warning-border bg-background/45 px-4 py-3"
                      key={material.id}
                    >
                      <span className="block font-semibold text-foreground">
                        {material.materialName}
                      </span>
                      <span className="mt-1 block text-sm text-warning">
                        {material.currentQuantity} / {material.lowStockThreshold}{" "}
                        {material.unitOfMeasure}
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            ) : (
              <p className="mt-3 text-sm leading-6 text-success">
                No low-stock alerts. All tracked materials are currently above their configured
                thresholds.
              </p>
            )}
          </section>
        ) : null}


        <form
          className="mt-8 grid gap-4 rounded-3xl border border-border bg-surface/70 p-5 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto] md:items-end"
          onSubmit={applyFilters}
          role="search"
        >
          <div>
            <label className="text-sm font-medium text-foreground" htmlFor="inventory-search">Search inventory</label>
            <span className="mt-1 block text-xs font-normal text-muted">
              Material code, name, description, or unit
            </span>
            <input
              className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20"
              id="inventory-search"
              maxLength={160}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="e.g. cotton or INV-FAB"
              type="search"
              value={searchInput}
            />
          </div>

          <label className="text-sm font-medium text-foreground" htmlFor="inventory-status-filter">
            Status
            <select
              className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
              id="inventory-status-filter"
              onChange={(event) => setStatusInput(event.target.value as InventoryMaterialStatus | "")}
              value={statusInput}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="DISCONTINUED">Discontinued</option>
            </select>
          </label>

          <label className="text-sm font-medium text-foreground" htmlFor="inventory-type-filter">
            Material type
            <select
              className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
              id="inventory-type-filter"
              onChange={(event) => setTypeInput(event.target.value as InventoryMaterialType | "")}
              value={typeInput}
            >
              <option value="">All types</option>
              <option value="FABRIC">Fabric</option>
              <option value="RAW_MATERIAL">Raw material</option>
            </select>
          </label>

          <div className="flex flex-wrap gap-2">
            <button
              className="rounded-xl bg-primary px-5 py-3 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              type="submit"
            >
              Apply filters
            </button>
            {hasFilters ? (
              <button
                className="rounded-xl border border-border px-4 py-3 text-sm font-semibold text-foreground transition hover:border-border hover:text-foreground"
                onClick={clearFilters}
                type="button"
              >
                Clear
              </button>
            ) : null}
          </div>
        </form>

        <div className="mt-8">
          {isLoading ? (
            <LoadingState
              message="Fetching inventory stock and linked supplier references."
              title="Loading inventory materials"
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
              title="Inventory materials unavailable"
            />
          ) : materials.length === 0 ? (
            <EmptyState
              action={hasFilters ? (
                <button
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  onClick={clearFilters}
                  type="button"
                >
                  Clear filters
                </button>
              ) : (
                <Link
                  className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
                  to={newInventoryMaterialPagePath}
                >
                  Add first material
                </Link>
              )}
              message={hasFilters
                ? "No inventory records match the current search and filters."
                : "No fabric or raw-material stock records have been created yet."}
              title={hasFilters ? "No matching inventory materials" : "No inventory materials"}
            />
          ) : (
            <>
              <p aria-live="polite" className="text-sm text-muted">
                {materials.length} {materials.length === 1 ? "material" : "materials"} found
                {hasFilters ? " for the current filters" : ""}
              </p>
              <ul className="mt-5 grid gap-5 lg:grid-cols-2">
                {materials.map((material) => (
                  <InventoryMaterialCard
                    key={material.id}
                    material={material}
                    onArchived={updateArchivedMaterial}
                    onConsumed={updateConsumedMaterial}
                    sourceSupply={material.sourceMaterialSupplyId
                      ? supplyById.get(material.sourceMaterialSupplyId)
                      : undefined}
                  />
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </section>
  );
}

import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  createInventoryMaterial,
  getInventoryMaterialApiError,
  type CreateInventoryMaterialInput,
  type InventoryMaterial,
  type InventoryMaterialType,
} from "../api/inventoryMaterials";
import {
  getMaterialSupplies,
  getMaterialSupplyApiError,
  type MaterialSupplyListItem,
} from "../api/materialSupplies";
import { inventoryMaterialListPagePath } from "../navigation/navigation";
import {
  emptyInventoryMaterialForm,
  normalizeInventoryMaterialForm,
  validateInventoryMaterialForm,
  type InventoryMaterialField,
  type InventoryMaterialFieldErrors,
} from "./inventoryMaterialForm";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

function FieldError({ id, message }: { id: string; message?: string }) {
  if (!message) return null;
  return (
    <span className="mt-2 block text-sm text-danger" id={id}>
      {message}
    </span>
  );
}

function supplyOptionLabel(supply: MaterialSupplyListItem) {
  return `${supply.supplierBusinessName} — ${supply.materialCode}: ${supply.materialName}`;
}

export function AddInventoryMaterialPage() {
  const [form, setForm] = useState<CreateInventoryMaterialInput>(
    emptyInventoryMaterialForm,
  );
  const [fieldErrors, setFieldErrors] = useState<InventoryMaterialFieldErrors>({});
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [savedMaterial, setSavedMaterial] = useState<InventoryMaterial | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [supplyOptions, setSupplyOptions] = useState<MaterialSupplyListItem[]>([]);
  const [isSupplyOptionsLoading, setIsSupplyOptionsLoading] = useState(true);
  const [supplyOptionsError, setSupplyOptionsError] = useState<string | null>(null);
  const [supplyRequestVersion, setSupplyRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setIsSupplyOptionsLoading(true);
    setSupplyOptionsError(null);

    void getMaterialSupplies({ status: "ACTIVE" }, controller.signal)
      .then((supplies) => {
        if (!controller.signal.aborted) setSupplyOptions(supplies);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setSupplyOptions([]);
          setSupplyOptionsError(getMaterialSupplyApiError(
            error,
            "Supplier supply records could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsSupplyOptionsLoading(false);
      });

    return () => controller.abort();
  }, [supplyRequestVersion]);

  const selectedSupply = supplyOptions.find(
    (supply) => String(supply.id) === form.sourceMaterialSupplyId,
  );

  function updateField<Field extends InventoryMaterialField>(
    field: Field,
    value: CreateInventoryMaterialInput[Field],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmissionError(null);

    const errors = validateInventoryMaterialForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      const result = await createInventoryMaterial(normalizeInventoryMaterialForm(form));
      setSavedMaterial(result.material);
    } catch (error: unknown) {
      const apiError = getInventoryMaterialApiError(
        error,
        "The inventory material could not be saved. Please try again.",
      );
      setSubmissionError(apiError.message);
      setFieldErrors(apiError.fields as InventoryMaterialFieldErrors);
    } finally {
      setIsSubmitting(false);
    }
  }

  function resetForm() {
    setForm(emptyInventoryMaterialForm());
    setFieldErrors({});
    setSubmissionError(null);
    setSavedMaterial(null);
  }

  function retrySupplyOptions() {
    setSupplyRequestVersion((current) => current + 1);
  }

  if (savedMaterial) {
    return (
      <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <div className="mx-auto max-w-3xl rounded-3xl border border-success-border bg-success-soft p-7 sm:p-10">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-success">
            Inventory material saved
          </p>
          <h1 className="mt-3 text-3xl font-bold text-foreground">{savedMaterial.materialName}</h1>
          <p aria-live="polite" className="mt-3 text-success">
            The opening stock record is now available in inventory.
          </p>
          <dl className="mt-8 grid gap-4 rounded-2xl border border-success-border bg-background/60 p-5 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Material ID</dt>
              <dd className="mt-1 font-semibold text-foreground">{savedMaterial.id}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Material code</dt>
              <dd className="mt-1 font-semibold text-foreground">{savedMaterial.materialCode}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Opening stock</dt>
              <dd className="mt-1 font-semibold text-foreground">
                {savedMaterial.currentQuantity} {savedMaterial.unitOfMeasure}
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Low-stock threshold</dt>
              <dd className="mt-1 font-semibold text-foreground">
                {savedMaterial.lowStockThreshold} {savedMaterial.unitOfMeasure}
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs uppercase tracking-wide text-muted">Supplier source</dt>
              <dd className="mt-1 font-semibold text-foreground">
                {selectedSupply
                  ? `${selectedSupply.supplierBusinessName} — ${selectedSupply.materialCode} (Supply ID ${selectedSupply.id})`
                  : "No supplier supply linked"}
              </dd>
            </div>
          </dl>
          <div className="mt-8 flex flex-wrap gap-3">
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover"
              onClick={resetForm}
              type="button"
            >
              Add another material
            </button>
            <Link
              className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground transition hover:border-border hover:text-foreground"
              to={inventoryMaterialListPagePath}
            >
              View inventory list
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-4xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Fabric Inventory Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Add inventory material
        </h1>
        <p className="mt-3 max-w-2xl leading-7 text-muted">
          Create the stock record used by production and inventory workflows. Link a Supplier
          Management supply record when the stock was received from a supplier.
        </p>

        <form
          className="mt-8 rounded-3xl border border-border bg-surface/70 p-6 sm:p-8"
          noValidate
          onSubmit={handleSubmit}
        >
          <div className="grid gap-6 sm:grid-cols-2">
            <label className="block text-sm font-medium text-foreground" htmlFor="inventory-code">
              Material code
              <input
                aria-describedby={fieldErrors.materialCode ? "inventory-code-error" : undefined}
                aria-invalid={Boolean(fieldErrors.materialCode)}
                autoComplete="off"
                className={inputClassName}
                id="inventory-code"
                maxLength={64}
                onChange={(event) => updateField("materialCode", event.target.value)}
                value={form.materialCode}
              />
              <FieldError id="inventory-code-error" message={fieldErrors.materialCode} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="inventory-name">
              Material name
              <input
                aria-describedby={fieldErrors.materialName ? "inventory-name-error" : undefined}
                aria-invalid={Boolean(fieldErrors.materialName)}
                autoComplete="off"
                className={inputClassName}
                id="inventory-name"
                maxLength={160}
                onChange={(event) => updateField("materialName", event.target.value)}
                value={form.materialName}
              />
              <FieldError id="inventory-name-error" message={fieldErrors.materialName} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="inventory-type">
              Material type
              <select
                className={inputClassName}
                id="inventory-type"
                onChange={(event) => updateField(
                  "materialType",
                  event.target.value as InventoryMaterialType,
                )}
                value={form.materialType}
              >
                <option value="FABRIC">Fabric</option>
                <option value="RAW_MATERIAL">Raw material</option>
              </select>
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="inventory-unit">
              Unit of measure
              <input
                aria-describedby={fieldErrors.unitOfMeasure ? "inventory-unit-error" : undefined}
                aria-invalid={Boolean(fieldErrors.unitOfMeasure)}
                autoComplete="off"
                className={inputClassName}
                id="inventory-unit"
                maxLength={32}
                onChange={(event) => updateField("unitOfMeasure", event.target.value)}
                value={form.unitOfMeasure}
              />
              <FieldError id="inventory-unit-error" message={fieldErrors.unitOfMeasure} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="inventory-quantity">
              Opening quantity
              <input
                aria-describedby={fieldErrors.currentQuantity ? "inventory-quantity-error" : undefined}
                aria-invalid={Boolean(fieldErrors.currentQuantity)}
                className={inputClassName}
                id="inventory-quantity"
                inputMode="decimal"
                onChange={(event) => updateField("currentQuantity", event.target.value)}
                placeholder="0.000"
                value={form.currentQuantity}
              />
              <FieldError id="inventory-quantity-error" message={fieldErrors.currentQuantity} />
            </label>

            <div>
              <label className="block text-sm font-medium text-foreground" htmlFor="inventory-threshold">Low-stock threshold</label>
              <input
                aria-describedby={fieldErrors.lowStockThreshold
                  ? "inventory-threshold-help inventory-threshold-error"
                  : "inventory-threshold-help"}
                aria-invalid={Boolean(fieldErrors.lowStockThreshold)}
                className={inputClassName}
                id="inventory-threshold"
                inputMode="decimal"
                onChange={(event) => updateField("lowStockThreshold", event.target.value)}
                placeholder="0.000"
                value={form.lowStockThreshold}
              />
              <span
                className="mt-2 block text-xs font-normal leading-5 text-muted"
                id="inventory-threshold-help"
              >
                Low stock starts when current quantity is at or below this value.
              </span>
              <FieldError id="inventory-threshold-error" message={fieldErrors.lowStockThreshold} />
            </div>

            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-foreground" htmlFor="inventory-source-supply">
                Supplier source (optional)
                <select
                  aria-describedby={fieldErrors.sourceMaterialSupplyId ? "inventory-source-error" : "inventory-source-help"}
                  aria-invalid={Boolean(fieldErrors.sourceMaterialSupplyId)}
                  className={inputClassName}
                  disabled={isSupplyOptionsLoading}
                  id="inventory-source-supply"
                  onChange={(event) => updateField("sourceMaterialSupplyId", event.target.value)}
                  value={form.sourceMaterialSupplyId}
                >
                  <option value="">
                    {isSupplyOptionsLoading ? "Loading active supplier supplies..." : "No supplier supply link"}
                  </option>
                  {supplyOptions.map((supply) => (
                    <option key={supply.id} value={String(supply.id)}>
                      {supplyOptionLabel(supply)}
                    </option>
                  ))}
                </select>
              </label>
              <p className="mt-2 text-sm text-muted" id="inventory-source-help">
                Supplier names and supply details are read from Supplier Management. They are not
                copied into inventory.
              </p>
              <FieldError
                id="inventory-source-error"
                message={fieldErrors.sourceMaterialSupplyId}
              />

              {supplyOptionsError ? (
                <div className="mt-3 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">
                  <p>{supplyOptionsError}</p>
                  <button
                    className="mt-3 rounded-lg border border-danger-border px-3 py-2 font-semibold transition hover:border-danger-border"
                    onClick={retrySupplyOptions}
                    type="button"
                  >
                    Retry supplier supplies
                  </button>
                </div>
              ) : null}

              {!isSupplyOptionsLoading && !supplyOptionsError && supplyOptions.length === 0 ? (
                <p className="mt-3 rounded-xl border border-border bg-background/50 p-4 text-sm text-muted">
                  No active supplier supply records are currently available. You can create an
                  unlinked inventory material and link only when a valid supply record exists.
                </p>
              ) : null}

              {selectedSupply ? (
                <dl className="mt-4 grid gap-3 rounded-xl border border-primary/70 bg-primary-soft p-4 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs uppercase tracking-wide text-muted">Supplier</dt>
                    <dd className="mt-1 font-semibold text-foreground">
                      {selectedSupply.supplierBusinessName}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs uppercase tracking-wide text-muted">Supply record</dt>
                    <dd className="mt-1 font-semibold text-foreground">
                      {selectedSupply.materialCode} · ID {selectedSupply.id}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs uppercase tracking-wide text-muted">Supplier quantity</dt>
                    <dd className="mt-1 text-foreground">
                      {selectedSupply.quantity} {selectedSupply.unitOfMeasure}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs uppercase tracking-wide text-muted">Supply status</dt>
                    <dd className="mt-1 text-foreground">{selectedSupply.status}</dd>
                  </div>
                </dl>
              ) : null}
            </div>

            <label className="block text-sm font-medium text-foreground sm:col-span-2" htmlFor="inventory-description">
              Material description (optional)
              <textarea
                aria-describedby={fieldErrors.materialDescription ? "inventory-description-error" : undefined}
                aria-invalid={Boolean(fieldErrors.materialDescription)}
                className={`${inputClassName} min-h-28 resize-y`}
                id="inventory-description"
                maxLength={500}
                onChange={(event) => updateField("materialDescription", event.target.value)}
                value={form.materialDescription}
              />
              <FieldError
                id="inventory-description-error"
                message={fieldErrors.materialDescription}
              />
            </label>
          </div>

          {submissionError ? (
            <p
              aria-live="polite"
              className="mt-6 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger"
              role="alert"
            >
              {submissionError}
            </p>
          ) : null}

          <div className="mt-8 flex flex-wrap items-center gap-4">
            <button
              className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
              disabled={isSubmitting}
              type="submit"
            >
              {isSubmitting ? "Saving material..." : "Save inventory material"}
            </button>
            <Link
              className="rounded-xl border border-border px-6 py-3 font-semibold text-foreground transition hover:border-border hover:text-foreground"
              to={inventoryMaterialListPagePath}
            >
              Back to inventory list
            </Link>
          </div>
        </form>
      </div>
    </section>
  );
}

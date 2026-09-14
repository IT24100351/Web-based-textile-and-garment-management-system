import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";

import {
  getInventoryMaterial,
  getInventoryMaterialApiError,
  updateInventoryMaterial,
  type InventoryMaterial,
  type InventoryMaterialType,
  type UpdateInventoryMaterialInput,
} from "../api/inventoryMaterials";
import { ErrorState, LoadingState } from "../components/AppStates";
import {
  inventoryMaterialListPagePath,
  parseInventoryMaterialPageId,
} from "../navigation/navigation";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border disabled:cursor-not-allowed disabled:opacity-60";

type EditableField = keyof UpdateInventoryMaterialInput;
type FieldErrors = Partial<Record<EditableField, string>>;

function normalizedInput(material: InventoryMaterial): UpdateInventoryMaterialInput {
  return {
    materialCode: material.materialCode,
    materialName: material.materialName,
    materialDescription: material.materialDescription ?? "",
    materialType: material.materialType,
    unitOfMeasure: material.unitOfMeasure,
    lowStockThreshold: material.lowStockThreshold,
    status: material.status === "INACTIVE" ? "INACTIVE" : "ACTIVE",
  };
}

function normalize(form: UpdateInventoryMaterialInput): UpdateInventoryMaterialInput {
  return {
    ...form,
    materialCode: form.materialCode.trim().replace(/\s+/g, " "),
    materialName: form.materialName.trim().replace(/\s+/g, " "),
    materialDescription: form.materialDescription.trim().replace(/\s+/g, " "),
    unitOfMeasure: form.unitOfMeasure.trim().replace(/\s+/g, " "),
    lowStockThreshold: form.lowStockThreshold.trim(),
  };
}

function validate(form: UpdateInventoryMaterialInput): FieldErrors {
  const value = normalize(form);
  const errors: FieldErrors = {};
  if (!value.materialCode) errors.materialCode = "Material code is required.";
  else if (value.materialCode.length > 64) errors.materialCode = "Material code must not exceed 64 characters.";
  if (!value.materialName) errors.materialName = "Material name is required.";
  else if (value.materialName.length > 160) errors.materialName = "Material name must not exceed 160 characters.";
  if (value.materialDescription.length > 500) errors.materialDescription = "Description must not exceed 500 characters.";
  if (!value.unitOfMeasure) errors.unitOfMeasure = "Unit of measure is required.";
  else if (value.unitOfMeasure.length > 32) errors.unitOfMeasure = "Unit of measure must not exceed 32 characters.";
  if (!/^\d{1,11}(?:\.\d{1,3})?$/.test(value.lowStockThreshold)) {
    errors.lowStockThreshold = "Low-stock threshold must be zero or greater with at most 11 digits and 3 decimals.";
  }
  return errors;
}

function FieldError({ message }: { message?: string }) {
  return message ? <span className="mt-2 block text-sm text-danger">{message}</span> : null;
}

export function EditInventoryMaterialPage() {
  const { materialId: materialIdParam } = useParams();
  const materialId = parseInventoryMaterialPageId(materialIdParam);
  const [material, setMaterial] = useState<InventoryMaterial | null>(null);
  const [form, setForm] = useState<UpdateInventoryMaterialInput | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    if (materialId === null) {
      setIsLoading(false);
      setLoadError("Inventory material ID is invalid.");
      return;
    }
    const controller = new AbortController();
    setIsLoading(true);
    setLoadError(null);
    void getInventoryMaterial(materialId, controller.signal)
      .then((record) => {
        if (!controller.signal.aborted) {
          setMaterial(record);
          setForm(normalizedInput(record));
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError(getInventoryMaterialApiError(
            error,
            "Inventory material could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [materialId, requestVersion]);

  function updateField<Field extends EditableField>(field: Field, value: UpdateInventoryMaterialInput[Field]) {
    setForm((current) => current ? { ...current, [field]: value } : current);
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSubmissionError(null);
    setSuccessMessage(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (materialId === null || !form || !material || material.status === "DISCONTINUED") return;
    const errors = validate(form);
    setFieldErrors(errors);
    setSubmissionError(null);
    setSuccessMessage(null);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      const result = await updateInventoryMaterial(materialId, normalize(form));
      setMaterial(result.material);
      setForm(normalizedInput(result.material));
      setSuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getInventoryMaterialApiError(
        error,
        "Inventory material could not be updated. Please try again.",
      );
      setFieldErrors(apiError.fields as FieldErrors);
      setSubmissionError(apiError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return <div className="mx-auto max-w-4xl p-6 sm:p-10"><LoadingState title="Loading inventory material…" /></div>;
  }
  if (loadError || !material || !form) {
    return (
      <div className="mx-auto max-w-4xl p-6 sm:p-10">
        <ErrorState
          message={loadError ?? "Inventory material was not found."}
          action={materialId !== null ? (
            <button className="rounded-full bg-primary px-5 py-2.5 font-semibold text-primary-foreground" onClick={() => setRequestVersion((value) => value + 1)} type="button">Retry</button>
          ) : undefined}
        />
      </div>
    );
  }

  const archived = material.status === "DISCONTINUED";

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-4xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Fabric Inventory Management</p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Edit inventory material</h1>
        <p className="mt-3 max-w-3xl leading-7 text-muted">
          Update descriptive metadata, status, and the replenishment threshold. Current quantity is read-only here and can change only through the stock-use operation.
        </p>

        <div className="mt-6 grid gap-4 rounded-2xl border border-border bg-surface/65 p-5 sm:grid-cols-3">
          <div><p className="text-xs uppercase tracking-wide text-muted">Material ID</p><p className="mt-1 font-semibold text-foreground">{material.id}</p></div>
          <div><p className="text-xs uppercase tracking-wide text-muted">Current quantity</p><p className="mt-1 font-semibold text-foreground">{material.currentQuantity} {material.unitOfMeasure}</p></div>
          <div><p className="text-xs uppercase tracking-wide text-muted">Supplier source</p><p className="mt-1 font-semibold text-foreground">{material.sourceMaterialSupplyId ? `Supply ID ${material.sourceMaterialSupplyId}` : "No supplier link"}</p></div>
        </div>

        {archived ? (
          <div className="mt-6 rounded-2xl border border-warning-border bg-warning-soft p-5 text-warning" role="status">
            This material is archived. Its historical record is preserved and metadata can no longer be edited.
          </div>
        ) : null}

        <form className="mt-8 rounded-3xl border border-border bg-surface/70 p-6 sm:p-8" noValidate onSubmit={handleSubmit}>
          <div className="grid gap-6 sm:grid-cols-2">
            <label className="text-sm font-medium text-foreground">Material code
              <input className={inputClassName} disabled={archived} maxLength={64} onChange={(event) => updateField("materialCode", event.target.value)} value={form.materialCode} />
              <FieldError message={fieldErrors.materialCode} />
            </label>
            <label className="text-sm font-medium text-foreground">Material name
              <input className={inputClassName} disabled={archived} maxLength={160} onChange={(event) => updateField("materialName", event.target.value)} value={form.materialName} />
              <FieldError message={fieldErrors.materialName} />
            </label>
            <label className="text-sm font-medium text-foreground">Material type
              <select className={inputClassName} disabled={archived} onChange={(event) => updateField("materialType", event.target.value as InventoryMaterialType)} value={form.materialType}>
                <option value="FABRIC">Fabric</option><option value="RAW_MATERIAL">Raw material</option>
              </select>
            </label>
            <label className="text-sm font-medium text-foreground">Unit of measure
              <input className={inputClassName} disabled={archived} maxLength={32} onChange={(event) => updateField("unitOfMeasure", event.target.value)} value={form.unitOfMeasure} />
              <FieldError message={fieldErrors.unitOfMeasure} />
            </label>
            <div>
              <label className="text-sm font-medium text-foreground" htmlFor="edit-inventory-threshold">Low-stock threshold</label>
              <input className={inputClassName} disabled={archived} id="edit-inventory-threshold" inputMode="decimal" onChange={(event) => updateField("lowStockThreshold", event.target.value)} value={form.lowStockThreshold} />
              <span className="mt-2 block text-xs text-muted">Current quantity is intentionally not editable on this page.</span>
              <FieldError message={fieldErrors.lowStockThreshold} />
            </div>
            <div>
              <label className="text-sm font-medium text-foreground" htmlFor="edit-inventory-status">Status</label>
              <select className={inputClassName} disabled={archived} id="edit-inventory-status" onChange={(event) => updateField("status", event.target.value as "ACTIVE" | "INACTIVE")} value={archived ? "DISCONTINUED" : form.status}>
                <option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option disabled value="DISCONTINUED">Discontinued</option>
              </select>
              <span className="mt-2 block text-xs text-muted">Use Archive from the inventory list for permanent discontinuation.</span>
            </div>
            <label className="sm:col-span-2 text-sm font-medium text-foreground">Description
              <textarea className={`${inputClassName} min-h-32 resize-y`} disabled={archived} maxLength={500} onChange={(event) => updateField("materialDescription", event.target.value)} value={form.materialDescription} />
              <FieldError message={fieldErrors.materialDescription} />
            </label>
          </div>

          {submissionError ? <p className="mt-5 rounded-xl border border-danger-border bg-danger-soft p-4 text-danger" role="alert">{submissionError}</p> : null}
          {successMessage ? <p className="mt-5 rounded-xl border border-success-border bg-success-soft p-4 text-success" role="status">{successMessage}</p> : null}

          <div className="mt-7 flex flex-wrap gap-3">
            <button className="rounded-full bg-primary px-5 py-2.5 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50" disabled={archived || isSubmitting} type="submit">{isSubmitting ? "Saving…" : "Save changes"}</button>
            <Link className="rounded-full border border-border px-5 py-2.5 font-semibold text-foreground" to={inventoryMaterialListPagePath}>Back to inventory</Link>
          </div>
        </form>
      </div>
    </section>
  );
}

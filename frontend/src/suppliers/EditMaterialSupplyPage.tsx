import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import {
  deleteMaterialSupply,
  getMaterialSupply,
  getMaterialSupplyApiError,
  updateMaterialSupply,
  type MaterialSupply,
  type UpdateMaterialSupplyInput,
} from "../api/materialSupplies";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { ConfirmDialog } from "../components/ui/ConfirmDialog";
import {
  materialSupplyListPagePath,
  parseMaterialSupplyPageId,
} from "../navigation/navigation";
import {
  normalizeMaterialSupplyDetails,
  validateMaterialSupplyDetails,
  type MaterialSupplyDetailField,
  type MaterialSupplyDetailFieldErrors,
} from "./materialSupplyForm";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

function formFromSupply(supply: MaterialSupply): UpdateMaterialSupplyInput {
  return {
    quantity: supply.quantity,
    unitPrice: supply.unitPrice,
    deliveryLeadTimeDays: String(supply.deliveryLeadTimeDays),
    deliveryNotes: supply.deliveryNotes ?? "",
    status: supply.status,
  };
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span className="mt-2 block text-sm text-danger" id={id}>
      {message}
    </span>
  ) : null;
}

function SupplyNotFoundState() {
  return (
    <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <EmptyState
        action={(
          <Link
            className="rounded-full border border-border px-5 py-2.5 text-sm font-semibold text-foreground transition hover:border-primary hover:text-foreground"
            to={materialSupplyListPagePath}
          >
            Back to my supplies
          </Link>
        )}
        message="The requested material supply was not found under your supplier profile."
        title="Material supply not found"
      />
    </div>
  );
}

function EditMaterialSupplyContent({ supplyId }: { supplyId: number }) {
  const navigate = useNavigate();
  const [supply, setSupply] = useState<MaterialSupply | null>(null);
  const [form, setForm] = useState<UpdateMaterialSupplyInput | null>(null);
  const [fieldErrors, setFieldErrors] = useState<MaterialSupplyDetailFieldErrors>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isNotFound, setIsNotFound] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [navigateAfterDelete, setNavigateAfterDelete] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void getMaterialSupply(supplyId, controller.signal)
      .then((record) => {
        if (!controller.signal.aborted) {
          setSupply(record);
          setForm(formFromSupply(record));
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const apiError = getMaterialSupplyApiError(
          error,
          "The material supply could not be loaded for editing. Please try again.",
        );
        if (apiError.status === 404 || apiError.code === "MATERIAL_SUPPLY_NOT_FOUND") {
          setIsNotFound(true);
        } else {
          setLoadError(apiError.message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [supplyId, requestVersion]);

  function updateField<Field extends MaterialSupplyDetailField>(
    field: Field,
    value: UpdateMaterialSupplyInput[Field],
  ) {
    setForm((current) => current ? { ...current, [field]: value } : current);
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSubmissionError(null);
    setSuccessMessage(null);
  }

  function retryLoad() {
    setIsLoading(true);
    setLoadError(null);
    setIsNotFound(false);
    setRequestVersion((current) => current + 1);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form) return;
    setSubmissionError(null);
    setSuccessMessage(null);
    const errors = validateMaterialSupplyDetails(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      const result = await updateMaterialSupply(
        supplyId,
        normalizeMaterialSupplyDetails(form),
      );
      setSupply(result.supply);
      setForm(formFromSupply(result.supply));
      setSuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getMaterialSupplyApiError(
        error,
        "The material supply could not be updated. Please try again.",
      );
      setSubmissionError(apiError.message);
      setFieldErrors(apiError.fields as MaterialSupplyDetailFieldErrors);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function confirmDelete() {
    if (isDeleting) return;
    setDeleteError(null);
    setIsDeleting(true);
    try {
      await deleteMaterialSupply(supplyId);
      setNavigateAfterDelete(true);
      setIsConfirmingDelete(false);
    } catch (error: unknown) {
      const apiError = getMaterialSupplyApiError(
        error,
        "The material supply could not be deleted. Please try again.",
      );
      setDeleteError(apiError.status === 404
        ? "Material supply no longer exists."
        : apiError.message);
    } finally {
      setIsDeleting(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          message="Fetching the current quantity, price, and delivery values."
          title="Loading supply editor"
        />
      </div>
    );
  }
  if (isNotFound) return <SupplyNotFoundState />;
  if (loadError || !supply || !form) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          action={(
            <button
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              onClick={retryLoad}
              type="button"
            >
              Retry
            </button>
          )}
          message={loadError ?? "The material supply could not be loaded for editing."}
          title="Supply editor unavailable"
        />
      </div>
    );
  }
  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-4xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Supplier Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Edit material supply
        </h1>
        <p className="mt-3 leading-7 text-muted">
          Update future quantity, unit price, delivery information, and operational availability.
          Material identity, ownership, unit, and stable ID remain unchanged.
        </p>

        <dl className="mt-6 grid gap-4 rounded-2xl border border-border bg-surface/60 p-5 sm:grid-cols-3">
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Material</dt>
            <dd className="mt-1 font-semibold text-foreground">{supply.materialName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Code</dt>
            <dd className="mt-1 font-semibold text-foreground">{supply.materialCode}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Unit</dt>
            <dd className="mt-1 font-semibold text-foreground">{supply.unitOfMeasure}</dd>
          </div>
        </dl>

        <form
          className="mt-6 rounded-3xl border border-border bg-surface/70 p-6 sm:p-8"
          noValidate
          onSubmit={handleSubmit}
        >
          <div className="grid gap-6 sm:grid-cols-2">
            <label className="text-sm font-medium text-foreground" htmlFor="edit-supply-quantity">
              Quantity ({supply.unitOfMeasure})
              <input
                aria-describedby={fieldErrors.quantity ? "edit-supply-quantity-error" : undefined}
                aria-invalid={Boolean(fieldErrors.quantity)}
                aria-label="Quantity"
                className={inputClassName}
                id="edit-supply-quantity"
                inputMode="decimal"
                onChange={(event) => updateField("quantity", event.target.value)}
                value={form.quantity}
              />
              <FieldError id="edit-supply-quantity-error" message={fieldErrors.quantity} />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="edit-supply-price">
              Unit price
              <input
                aria-describedby={fieldErrors.unitPrice ? "edit-supply-price-error" : undefined}
                aria-invalid={Boolean(fieldErrors.unitPrice)}
                className={inputClassName}
                id="edit-supply-price"
                inputMode="decimal"
                onChange={(event) => updateField("unitPrice", event.target.value)}
                value={form.unitPrice}
              />
              <FieldError id="edit-supply-price-error" message={fieldErrors.unitPrice} />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="edit-supply-days">
              Delivery lead time (days)
              <input
                aria-describedby={fieldErrors.deliveryLeadTimeDays ? "edit-supply-days-error" : undefined}
                aria-invalid={Boolean(fieldErrors.deliveryLeadTimeDays)}
                className={inputClassName}
                id="edit-supply-days"
                inputMode="numeric"
                onChange={(event) => updateField("deliveryLeadTimeDays", event.target.value)}
                value={form.deliveryLeadTimeDays}
              />
              <FieldError id="edit-supply-days-error" message={fieldErrors.deliveryLeadTimeDays} />
            </label>
            <label className="text-sm font-medium text-foreground" htmlFor="edit-supply-status">
              Status
              <select
                aria-describedby={fieldErrors.status ? "edit-supply-status-error" : undefined}
                aria-invalid={Boolean(fieldErrors.status)}
                className={inputClassName}
                id="edit-supply-status"
                onChange={(event) => updateField(
                  "status",
                  event.target.value as UpdateMaterialSupplyInput["status"],
                )}
                value={form.status}
              >
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
              <FieldError id="edit-supply-status-error" message={fieldErrors.status} />
            </label>
            <label className="text-sm font-medium text-foreground sm:col-span-2" htmlFor="edit-supply-notes">
              Delivery notes (optional)
              <textarea
                aria-describedby={fieldErrors.deliveryNotes ? "edit-supply-notes-error" : undefined}
                aria-invalid={Boolean(fieldErrors.deliveryNotes)}
                className={`${inputClassName} min-h-28 resize-y`}
                id="edit-supply-notes"
                maxLength={500}
                onChange={(event) => updateField("deliveryNotes", event.target.value)}
                value={form.deliveryNotes}
              />
              <FieldError id="edit-supply-notes-error" message={fieldErrors.deliveryNotes} />
            </label>
          </div>

          {submissionError ? (
            <p className="mt-6 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">
              {submissionError}
            </p>
          ) : null}
          {successMessage ? (
            <p aria-live="polite" className="mt-6 rounded-xl border border-success-border bg-success-soft p-4 text-sm text-success">
              {successMessage} Updated at {new Date(supply.updatedAt).toLocaleString()}.
            </p>
          ) : null}

          <div className="mt-7 flex flex-wrap gap-3">
            <button
              className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
              disabled={isSubmitting}
              type="submit"
            >
              {isSubmitting ? "Updating supply…" : "Update supply details"}
            </button>
            <Link
              className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground transition hover:border-border hover:text-foreground"
              to={materialSupplyListPagePath}
            >
              Back to my supplies
            </Link>
          </div>
        </form>

        <section className="mt-8 rounded-3xl border border-danger-border bg-danger-soft p-6 sm:p-8">
          <p className="text-sm font-semibold uppercase tracking-[0.16em] text-danger">
            Permanent action
          </p>
          <h2 className="mt-2 text-2xl font-bold text-foreground">Delete material supply</h2>
          <p className="mt-3 max-w-2xl leading-7 text-foreground-muted">
            Delete permanently removes an unused supply. Supplies already used by Inventory
            records are protected and cannot be deleted.
          </p>

          <button
            className="mt-6 rounded-xl border border-danger-border px-5 py-3 font-semibold text-danger transition hover:bg-danger-soft"
            onClick={() => {
              setDeleteError(null);
              setNavigateAfterDelete(false);
              setIsConfirmingDelete(true);
            }}
            type="button"
          >
            Delete material supply
          </button>
        </section>
        <ConfirmDialog
          cancelLabel="Cancel"
          confirmLabel="Delete"
          description="Are you sure you want to delete this material supply?"
          error={deleteError}
          isBusy={isDeleting}
          isOpen={isConfirmingDelete}
          onAfterClose={() => {
            if (navigateAfterDelete) navigate(materialSupplyListPagePath, { replace: true });
          }}
          onCancel={() => {
            setNavigateAfterDelete(false);
            setIsConfirmingDelete(false);
            setDeleteError(null);
          }}
          onConfirm={() => void confirmDelete()}
          title="Delete material supply?"
        />
      </div>
    </section>
  );
}

export function EditMaterialSupplyPage() {
  const { supplyId } = useParams<{ supplyId: string }>();
  const parsedSupplyId = parseMaterialSupplyPageId(supplyId);
  return parsedSupplyId === null
    ? <SupplyNotFoundState />
    : <EditMaterialSupplyContent supplyId={parsedSupplyId} />;
}

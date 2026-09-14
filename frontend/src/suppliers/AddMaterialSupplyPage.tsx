import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  createMaterialSupply,
  getMaterialSupplyApiError,
  type CreateMaterialSupplyInput,
  type MaterialSupply,
} from "../api/materialSupplies";
import {
  getSupplierProfile,
  getSupplierProfileApiError,
  type SupplierProfile,
} from "../api/supplierProfile";
import { ErrorState, LoadingState } from "../components/AppStates";
import { supplierProfilePagePath } from "../navigation/navigation";
import {
  emptyMaterialSupplyForm,
  normalizeMaterialSupplyForm,
  validateMaterialSupplyForm,
  type MaterialSupplyField,
  type MaterialSupplyFieldErrors,
} from "./materialSupplyForm";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span className="mt-2 block text-sm text-danger" id={id}>
      {message}
    </span>
  ) : null;
}

function optionalValue(value: string | null) {
  return value ?? "Not provided";
}

export function AddMaterialSupplyPage() {
  const [supplier, setSupplier] = useState<SupplierProfile | null>(null);
  const [form, setForm] = useState<CreateMaterialSupplyInput>(emptyMaterialSupplyForm);
  const [fieldErrors, setFieldErrors] = useState<MaterialSupplyFieldErrors>({});
  const [loadError, setLoadError] = useState<{ code?: string; message: string } | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [savedSupply, setSavedSupply] = useState<MaterialSupply | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void getSupplierProfile(controller.signal)
      .then((profile) => {
        if (!controller.signal.aborted) {
          setSupplier(profile);
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        const apiError = getSupplierProfileApiError(
          error,
          "Your supplier identity could not be loaded. Please try again.",
        );
        setLoadError({ code: apiError.code, message: apiError.message });
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [requestVersion]);

  function updateField<Field extends MaterialSupplyField>(
    field: Field,
    value: CreateMaterialSupplyInput[Field],
  ) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSubmissionError(null);
  }

  function retryLoad() {
    setIsLoading(true);
    setLoadError(null);
    setRequestVersion((current) => current + 1);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmissionError(null);
    const errors = validateMaterialSupplyForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await createMaterialSupply(normalizeMaterialSupplyForm(form));
      setSavedSupply(result.supply);
    } catch (error: unknown) {
      const apiError = getMaterialSupplyApiError(
        error,
        "The material supply could not be saved. Please try again.",
      );
      setSubmissionError(apiError.message);
      setFieldErrors(apiError.fields as MaterialSupplyFieldErrors);
    } finally {
      setIsSubmitting(false);
    }
  }

  function resetForm() {
    setForm(emptyMaterialSupplyForm());
    setFieldErrors({});
    setSubmissionError(null);
    setSavedSupply(null);
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          message="Confirming the supplier profile linked to your account."
          title="Loading supplier identity"
        />
      </div>
    );
  }

  if (loadError) {
    const profileRequired = loadError.code === "SUPPLIER_PROFILE_NOT_FOUND";
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <ErrorState
          action={profileRequired ? (
            <Link
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              to={supplierProfilePagePath}
            >
              Create supplier profile
            </Link>
          ) : (
            <button
              className="rounded-full bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover"
              onClick={retryLoad}
              type="button"
            >
              Retry
            </button>
          )}
          message={profileRequired
            ? "Create your supplier profile before adding material supplies."
            : loadError.message}
          title={profileRequired ? "Supplier profile required" : "Supplier identity unavailable"}
        />
      </div>
    );
  }

  if (savedSupply) {
    return (
      <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <div className="mx-auto max-w-4xl rounded-3xl border border-success-border bg-success-soft p-7 sm:p-10">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-success">
            Material supply saved
          </p>
          <h1 className="mt-3 text-3xl font-bold text-foreground">{savedSupply.materialName}</h1>
          <p aria-live="polite" className="mt-3 text-success">
            Material supply created successfully under {supplier?.businessName}.
          </p>
          <dl className="mt-8 grid gap-4 rounded-2xl border border-success-border bg-background/60 p-5 sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Supply ID</dt>
              <dd className="mt-1 font-semibold text-foreground">{savedSupply.id}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Supplier ID</dt>
              <dd className="mt-1 font-semibold text-foreground">{savedSupply.supplierId}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Material code</dt>
              <dd className="mt-1 font-semibold text-foreground">{savedSupply.materialCode}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Quantity</dt>
              <dd className="mt-1 font-semibold text-foreground">
                {savedSupply.quantity} {savedSupply.unitOfMeasure}
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Unit price</dt>
              <dd className="mt-1 font-semibold text-foreground">{savedSupply.unitPrice}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Delivery lead time</dt>
              <dd className="mt-1 font-semibold text-foreground">
                {savedSupply.deliveryLeadTimeDays} days
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Description</dt>
              <dd className="mt-1 text-foreground">
                {optionalValue(savedSupply.materialDescription)}
              </dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-muted">Delivery notes</dt>
              <dd className="mt-1 text-foreground">
                {optionalValue(savedSupply.deliveryNotes)}
              </dd>
            </div>
          </dl>
          <button
            className="mt-8 rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover"
            onClick={resetForm}
            type="button"
          >
            Add another material supply
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-4xl">
        <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">
          Supplier Management
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">
          Add material supply
        </h1>
        <p className="mt-3 max-w-2xl leading-7 text-muted">
          Record a material your business can provide. The server links it to your
          authenticated supplier profile and creates it as active.
        </p>

        <dl className="mt-6 grid gap-4 rounded-2xl border border-border bg-surface/60 p-5 sm:grid-cols-2">
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Supplier</dt>
            <dd className="mt-1 font-medium text-foreground">{supplier?.businessName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Supplier ID</dt>
            <dd className="mt-1 font-medium text-foreground">{supplier?.id}</dd>
          </div>
        </dl>

        <form
          className="mt-6 rounded-3xl border border-border bg-surface/70 p-6 sm:p-8"
          noValidate
          onSubmit={handleSubmit}
        >
          <div className="grid gap-6 sm:grid-cols-2">
            <label className="block text-sm font-medium text-foreground" htmlFor="supply-code">
              Material code
              <input
                aria-describedby={fieldErrors.materialCode ? "supply-code-error" : undefined}
                aria-invalid={Boolean(fieldErrors.materialCode)}
                autoComplete="off"
                className={inputClassName}
                id="supply-code"
                maxLength={64}
                onChange={(event) => updateField("materialCode", event.target.value)}
                value={form.materialCode}
              />
              <FieldError id="supply-code-error" message={fieldErrors.materialCode} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="supply-name">
              Material name
              <input
                aria-describedby={fieldErrors.materialName ? "supply-name-error" : undefined}
                aria-invalid={Boolean(fieldErrors.materialName)}
                autoComplete="off"
                className={inputClassName}
                id="supply-name"
                maxLength={160}
                onChange={(event) => updateField("materialName", event.target.value)}
                value={form.materialName}
              />
              <FieldError id="supply-name-error" message={fieldErrors.materialName} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="supply-quantity">
              Quantity
              <input
                aria-label="Quantity"
                aria-describedby={fieldErrors.quantity ? "supply-quantity-error" : "supply-quantity-help"}
                aria-invalid={Boolean(fieldErrors.quantity)}
                className={inputClassName}
                id="supply-quantity"
                inputMode="decimal"
                onChange={(event) => updateField("quantity", event.target.value)}
                placeholder="0.000"
                value={form.quantity}
              />
              <span className="mt-2 block text-xs text-muted" id="supply-quantity-help">
                Positive amount with up to three decimal places.
              </span>
              <FieldError id="supply-quantity-error" message={fieldErrors.quantity} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="supply-unit">
              Unit of measure
              <input
                aria-describedby={fieldErrors.unitOfMeasure ? "supply-unit-error" : undefined}
                aria-invalid={Boolean(fieldErrors.unitOfMeasure)}
                autoComplete="off"
                className={inputClassName}
                id="supply-unit"
                maxLength={32}
                onChange={(event) => updateField("unitOfMeasure", event.target.value)}
                placeholder="metre, kilogram, cone…"
                value={form.unitOfMeasure}
              />
              <FieldError id="supply-unit-error" message={fieldErrors.unitOfMeasure} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="supply-price">
              Unit price
              <input
                aria-label="Unit price"
                aria-describedby={fieldErrors.unitPrice ? "supply-price-error" : "supply-price-help"}
                aria-invalid={Boolean(fieldErrors.unitPrice)}
                className={inputClassName}
                id="supply-price"
                inputMode="decimal"
                onChange={(event) => updateField("unitPrice", event.target.value)}
                placeholder="0.00"
                value={form.unitPrice}
              />
              <span className="mt-2 block text-xs text-muted" id="supply-price-help">
                Positive amount with up to two decimal places.
              </span>
              <FieldError id="supply-price-error" message={fieldErrors.unitPrice} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="supply-lead-time">
              Delivery lead time (days)
              <input
                aria-describedby={fieldErrors.deliveryLeadTimeDays ? "supply-lead-time-error" : undefined}
                aria-invalid={Boolean(fieldErrors.deliveryLeadTimeDays)}
                className={inputClassName}
                id="supply-lead-time"
                inputMode="numeric"
                onChange={(event) => updateField("deliveryLeadTimeDays", event.target.value)}
                placeholder="0"
                value={form.deliveryLeadTimeDays}
              />
              <FieldError
                id="supply-lead-time-error"
                message={fieldErrors.deliveryLeadTimeDays}
              />
            </label>
          </div>

          <label className="mt-6 block text-sm font-medium text-foreground" htmlFor="supply-description">
            Material description (optional)
            <textarea
              aria-describedby={fieldErrors.materialDescription ? "supply-description-error" : undefined}
              aria-invalid={Boolean(fieldErrors.materialDescription)}
              className={`${inputClassName} min-h-28 resize-y`}
              id="supply-description"
              maxLength={500}
              onChange={(event) => updateField("materialDescription", event.target.value)}
              value={form.materialDescription}
            />
            <FieldError
              id="supply-description-error"
              message={fieldErrors.materialDescription}
            />
          </label>

          <label className="mt-6 block text-sm font-medium text-foreground" htmlFor="supply-delivery-notes">
            Delivery notes (optional)
            <textarea
              aria-describedby={fieldErrors.deliveryNotes ? "supply-delivery-notes-error" : undefined}
              aria-invalid={Boolean(fieldErrors.deliveryNotes)}
              className={`${inputClassName} min-h-28 resize-y`}
              id="supply-delivery-notes"
              maxLength={500}
              onChange={(event) => updateField("deliveryNotes", event.target.value)}
              value={form.deliveryNotes}
            />
            <FieldError id="supply-delivery-notes-error" message={fieldErrors.deliveryNotes} />
          </label>

          {submissionError ? (
            <p
              className="mt-6 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger"
              role="alert"
            >
              {submissionError}
            </p>
          ) : null}

          <button
            className="mt-8 rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? "Saving material supply…" : "Save material supply"}
          </button>
        </form>
      </div>
    </section>
  );
}

import { useEffect, useState, type FormEvent } from "react";

import {
  getSupplierProfile,
  getSupplierProfileApiError,
  saveSupplierProfile,
  type SupplierProfile,
  type SupplierProfileInput,
} from "../api/supplierProfile";
import { useAuth } from "../auth/useAuth";
import { ErrorState, LoadingState } from "../components/AppStates";

type SupplierProfileField = keyof SupplierProfileInput;
type SupplierProfileFieldErrors = Partial<Record<SupplierProfileField, string>>;

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/20 aria-invalid:border-danger-border";

function emptyProfileForm(): SupplierProfileInput {
  return { businessName: "", contactPhone: "", address: "" };
}

function formFromProfile(profile: SupplierProfile): SupplierProfileInput {
  return {
    businessName: profile.businessName,
    contactPhone: profile.contactPhone,
    address: profile.address,
  };
}

function normalizeProfileForm(form: SupplierProfileInput): SupplierProfileInput {
  return {
    businessName: form.businessName.trim().replace(/\s+/g, " "),
    contactPhone: form.contactPhone.trim().replace(/\s+/g, " "),
    address: form.address.trim().replace(/\s+/g, " "),
  };
}

function validateProfileForm(form: SupplierProfileInput): SupplierProfileFieldErrors {
  const normalized = normalizeProfileForm(form);
  const errors: SupplierProfileFieldErrors = {};
  if (!normalized.businessName) {
    errors.businessName = "Business name is required.";
  } else if (normalized.businessName.length > 160) {
    errors.businessName = "Business name must not exceed 160 characters.";
  }
  if (!normalized.contactPhone) {
    errors.contactPhone = "Contact phone is required.";
  } else if (normalized.contactPhone.length > 32) {
    errors.contactPhone = "Contact phone must not exceed 32 characters.";
  }
  if (!normalized.address) {
    errors.address = "Address is required.";
  } else if (normalized.address.length > 500) {
    errors.address = "Address must not exceed 500 characters.";
  }
  return errors;
}

function submitButtonLabel(isSubmitting: boolean, hasProfile: boolean) {
  if (isSubmitting) {
    return "Saving supplier profile…";
  }
  return hasProfile ? "Update supplier profile" : "Create supplier profile";
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <span className="mt-2 block text-sm text-danger" id={id}>
      {message}
    </span>
  ) : null;
}

export function SupplierProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState<SupplierProfile | null>(null);
  const [form, setForm] = useState<SupplierProfileInput>(emptyProfileForm);
  const [fieldErrors, setFieldErrors] = useState<SupplierProfileFieldErrors>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submissionError, setSubmissionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void getSupplierProfile(controller.signal)
      .then((storedProfile) => {
        if (!controller.signal.aborted) {
          setProfile(storedProfile);
          setForm(formFromProfile(storedProfile));
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        const apiError = getSupplierProfileApiError(
          error,
          "The supplier profile could not be loaded. Please try again.",
        );
        if (apiError.status !== 404 && apiError.code !== "SUPPLIER_PROFILE_NOT_FOUND") {
          setLoadError(apiError.message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [requestVersion]);

  function updateField(field: SupplierProfileField, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSubmissionError(null);
    setSuccessMessage(null);
  }

  function retryLoad() {
    setIsLoading(true);
    setLoadError(null);
    setRequestVersion((current) => current + 1);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmissionError(null);
    setSuccessMessage(null);
    const errors = validateProfileForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await saveSupplierProfile(normalizeProfileForm(form));
      setProfile(result.profile);
      setForm(formFromProfile(result.profile));
      setSuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getSupplierProfileApiError(
        error,
        "The supplier profile could not be saved. Please try again.",
      );
      setSubmissionError(apiError.message);
      setFieldErrors(apiError.fields as SupplierProfileFieldErrors);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
        <LoadingState
          message="Fetching the supplier information linked to your account."
          title="Loading supplier profile"
        />
      </div>
    );
  }

  if (loadError) {
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
          message={loadError}
          title="Supplier profile unavailable"
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
          {profile ? "Edit supplier profile" : "Create supplier profile"}
        </h1>
        <p className="mt-3 max-w-2xl leading-7 text-muted">
          Maintain the business and contact information used by Supplier and Inventory
          Management. Your authenticated account owns this profile.
        </p>

        <dl className="mt-6 grid gap-4 rounded-2xl border border-border bg-surface/60 p-5 sm:grid-cols-2">
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Account holder</dt>
            <dd className="mt-1 font-medium text-foreground">{user?.fullName}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase tracking-wide text-muted">Login email</dt>
            <dd className="mt-1 font-medium text-foreground">{user?.email}</dd>
          </div>
        </dl>

        {!profile ? (
          <p className="mt-6 rounded-xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">
            No supplier profile exists yet. Complete the required fields to create one.
          </p>
        ) : null}

        <form
          className="mt-6 rounded-3xl border border-border bg-surface/70 p-6 sm:p-8"
          noValidate
          onSubmit={handleSubmit}
        >
          <div className="grid gap-6 sm:grid-cols-2">
            <label className="block text-sm font-medium text-foreground" htmlFor="supplier-business-name">
              Business name
              <input
                aria-describedby={fieldErrors.businessName ? "supplier-business-name-error" : undefined}
                aria-invalid={Boolean(fieldErrors.businessName)}
                autoComplete="organization"
                className={inputClassName}
                id="supplier-business-name"
                maxLength={160}
                onChange={(event) => updateField("businessName", event.target.value)}
                value={form.businessName}
              />
              <FieldError id="supplier-business-name-error" message={fieldErrors.businessName} />
            </label>

            <label className="block text-sm font-medium text-foreground" htmlFor="supplier-contact-phone">
              Contact phone
              <input
                aria-describedby={fieldErrors.contactPhone ? "supplier-contact-phone-error" : undefined}
                aria-invalid={Boolean(fieldErrors.contactPhone)}
                autoComplete="tel"
                className={inputClassName}
                id="supplier-contact-phone"
                inputMode="tel"
                maxLength={32}
                onChange={(event) => updateField("contactPhone", event.target.value)}
                value={form.contactPhone}
              />
              <FieldError id="supplier-contact-phone-error" message={fieldErrors.contactPhone} />
            </label>
          </div>

          <label className="mt-6 block text-sm font-medium text-foreground" htmlFor="supplier-address">
            Address
            <textarea
              aria-describedby={fieldErrors.address ? "supplier-address-error" : undefined}
              aria-invalid={Boolean(fieldErrors.address)}
              autoComplete="street-address"
              className={`${inputClassName} min-h-32 resize-y`}
              id="supplier-address"
              maxLength={500}
              onChange={(event) => updateField("address", event.target.value)}
              value={form.address}
            />
            <FieldError id="supplier-address-error" message={fieldErrors.address} />
          </label>

          {submissionError ? (
            <p
              className="mt-6 rounded-xl border border-danger-border bg-danger-soft p-4 text-sm text-danger"
              role="alert"
            >
              {submissionError}
            </p>
          ) : null}
          {successMessage ? (
            <p
              aria-live="polite"
              className="mt-6 rounded-xl border border-success-border bg-success-soft p-4 text-sm text-success"
            >
              {successMessage} Supplier ID {profile?.id} remains linked only to your account.
            </p>
          ) : null}

          <button
            className="mt-8 rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
            disabled={isSubmitting}
            type="submit"
          >
            {submitButtonLabel(isSubmitting, profile !== null)}
          </button>
        </form>
      </div>
    </section>
  );
}

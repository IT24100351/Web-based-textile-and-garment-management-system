import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { ErrorState, LoadingState } from "../components/AppStates";
import {
  changePassword,
  getProfile,
  getProfileApiError,
  updateProfile,
  type ChangePasswordInput,
  type ProfileInput,
} from "../api/profile";
import type { AuthUser } from "../api/auth";
import { roleLabels } from "../navigation/navigation";
import { useAuth } from "../auth/useAuth";

type EditableField = keyof ProfileInput;
type FieldErrors = Partial<Record<EditableField, string>>;
type PasswordForm = ChangePasswordInput & { confirmPassword: string };
type PasswordField = keyof PasswordForm;
type PasswordFieldErrors = Partial<Record<PasswordField, string>>;

const emptyPasswordForm: PasswordForm = {
  currentPassword: "",
  newPassword: "",
  confirmPassword: "",
};

function formFromProfile(profile: AuthUser): ProfileInput {
  return { fullName: profile.fullName };
}

function normalizeForm(form: ProfileInput): ProfileInput {
  return { fullName: form.fullName.trim().replace(/\s+/g, " ") };
}

function validateForm(form: ProfileInput): FieldErrors {
  const normalized = normalizeForm(form);
  const errors: FieldErrors = {};
  if (!normalized.fullName) {
    errors.fullName = "Full name is required.";
  } else if (normalized.fullName.length > 120) {
    errors.fullName = "Full name must not exceed 120 characters.";
  }
  return errors;
}

function validatePasswordForm(form: PasswordForm): PasswordFieldErrors {
  const errors: PasswordFieldErrors = {};
  const newPasswordBytes = new TextEncoder().encode(form.newPassword).length;

  if (!form.currentPassword) {
    errors.currentPassword = "Current password is required.";
  }
  if (!form.newPassword) {
    errors.newPassword = "New password is required.";
  } else if (newPasswordBytes < 8 || newPasswordBytes > 72) {
    errors.newPassword = "Password must contain between 8 and 72 UTF-8 bytes.";
  } else if (form.newPassword === form.currentPassword) {
    errors.newPassword = "New password must be different from the current password.";
  }
  if (!form.confirmPassword) {
    errors.confirmPassword = "Confirm your new password.";
  } else if (form.confirmPassword !== form.newPassword) {
    errors.confirmPassword = "Passwords do not match.";
  }

  return errors;
}

export function ProfileSettingsPage() {
  const navigate = useNavigate();
  const { refreshUser, logout } = useAuth();
  const [profile, setProfile] = useState<AuthUser | null>(null);
  const [form, setForm] = useState<ProfileInput>({ fullName: "" });
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [passwordForm, setPasswordForm] = useState<PasswordForm>(emptyPasswordForm);
  const [passwordFieldErrors, setPasswordFieldErrors] = useState<PasswordFieldErrors>({});
  const [passwordSaveError, setPasswordSaveError] = useState<string | null>(null);
  const [passwordSuccessMessage, setPasswordSuccessMessage] = useState<string | null>(null);
  const [isChangingPassword, setIsChangingPassword] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setLoadError(null);

    void getProfile(controller.signal)
      .then((storedProfile) => {
        setProfile(storedProfile);
        setForm(formFromProfile(storedProfile));
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError(getProfileApiError(
            error,
            "Your profile could not be loaded. Please try again.",
          ).message);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [reloadKey]);

  function updateField(field: EditableField, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setSaveError(null);
    setSuccessMessage(null);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validateForm(form);
    setFieldErrors(errors);
    setSaveError(null);
    setSuccessMessage(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSaving(true);
    try {
      const updated = await updateProfile(normalizeForm(form));
      setProfile(updated);
      setForm(formFromProfile(updated));
      setSuccessMessage("Profile settings updated successfully.");
      await refreshUser().catch(() => null);
    } catch (error: unknown) {
      const apiError = getProfileApiError(
        error,
        "Your profile could not be saved. Please try again.",
      );
      setFieldErrors(apiError.fields as FieldErrors);
      setSaveError(apiError.message);
    } finally {
      setIsSaving(false);
    }
  }

  function updatePasswordField(field: PasswordField, value: string) {
    setPasswordForm((current) => ({ ...current, [field]: value }));
    setPasswordFieldErrors((current) => ({ ...current, [field]: undefined }));
    setPasswordSaveError(null);
    setPasswordSuccessMessage(null);
  }

  async function handlePasswordSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validatePasswordForm(passwordForm);
    setPasswordFieldErrors(errors);
    setPasswordSaveError(null);
    setPasswordSuccessMessage(null);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsChangingPassword(true);
    try {
      const message = await changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });
      setPasswordForm(emptyPasswordForm);
      await logout().catch(() => undefined);
      navigate("/login", {
        replace: true,
        state: {
          message: message || "Password changed successfully. Please sign in again.",
        },
      });
    } catch (error: unknown) {
      const apiError = getProfileApiError(
        error,
        "Your password could not be changed. Please try again.",
      );
      setPasswordFieldErrors(apiError.fields as PasswordFieldErrors);
      setPasswordSaveError(apiError.message);
    } finally {
      setIsChangingPassword(false);
    }
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-4xl p-6 sm:p-10 lg:p-12">
        <LoadingState
          message="Loading the account information owned by your authenticated user ID."
          title="Loading profile settings…"
        />
      </div>
    );
  }

  if (loadError || !profile) {
    return (
      <div className="mx-auto max-w-4xl p-6 sm:p-10 lg:p-12">
        <ErrorState
          action={(
            <button
              className="rounded-full bg-primary px-5 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover"
              onClick={() => setReloadKey((key) => key + 1)}
              type="button"
            >
              Retry
            </button>
          )}
          message={loadError ?? "Your profile could not be loaded."}
          title="Profile settings unavailable"
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl p-6 sm:p-10 lg:p-12">
      <section className="rounded-3xl border border-border bg-surface p-6 shadow-2xl shadow-black/20 sm:p-10">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">
          Account settings
        </p>
        <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground">Profile settings</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-muted">
          Maintain the personal name shown across LankaWear Apparel and securely change your own
          sign-in password. Your email, role, and access status remain protected.
        </p>

        <div className="mt-8 grid gap-4 rounded-2xl border border-border bg-background/60 p-5 sm:grid-cols-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.15em] text-muted">Account ID</p>
            <p className="mt-2 font-mono text-sm text-foreground">{profile.id}</p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.15em] text-muted">Email</p>
            <p className="mt-2 break-all text-sm text-foreground">{profile.email}</p>
            <p className="mt-1 text-xs text-muted">Protected sign-in and recovery identity</p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.15em] text-muted">Role</p>
            <p className="mt-2 text-sm font-semibold text-foreground">{roleLabels[profile.role]}</p>
          </div>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <label className="block text-sm font-medium text-foreground-muted">
            Full name
            <input
              aria-invalid={Boolean(fieldErrors.fullName)}
              autoComplete="name"
              className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
              maxLength={120}
              onChange={(event) => updateField("fullName", event.target.value)}
              value={form.fullName}
            />
            {fieldErrors.fullName ? (
              <span className="mt-2 block text-sm text-danger">{fieldErrors.fullName}</span>
            ) : null}
          </label>

          {saveError ? (
            <p className="rounded-2xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">
              {saveError}
            </p>
          ) : null}
          {successMessage ? (
            <p className="rounded-2xl border border-success-border bg-success-soft p-4 text-sm text-success" role="status">
              {successMessage}
            </p>
          ) : null}

          <button
            className="rounded-full bg-primary px-6 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
            disabled={isSaving}
            type="submit"
          >
            {isSaving ? "Saving profile…" : "Save profile settings"}
          </button>
        </form>

        <div className="my-10 border-t border-border" />

        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.15em] text-primary">
            Account security
          </p>
          <h2 className="mt-2 text-2xl font-bold tracking-tight text-foreground">Change password</h2>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
            Enter your current password to confirm it is you. Your new password must contain
            between 8 and 72 UTF-8 bytes.
          </p>

          <form className="mt-6 space-y-5" onSubmit={handlePasswordSubmit}>
            <label className="block text-sm font-medium text-foreground-muted">
              Current password
              <input
                aria-invalid={Boolean(passwordFieldErrors.currentPassword)}
                autoComplete="current-password"
                className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
                maxLength={72}
                onChange={(event) => updatePasswordField("currentPassword", event.target.value)}
                type="password"
                value={passwordForm.currentPassword}
              />
              {passwordFieldErrors.currentPassword ? (
                <span className="mt-2 block text-sm text-danger">
                  {passwordFieldErrors.currentPassword}
                </span>
              ) : null}
            </label>

            <div className="grid gap-5 sm:grid-cols-2">
              <label className="block text-sm font-medium text-foreground-muted">
                New password
                <input
                  aria-invalid={Boolean(passwordFieldErrors.newPassword)}
                  autoComplete="new-password"
                  className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
                  maxLength={72}
                  onChange={(event) => updatePasswordField("newPassword", event.target.value)}
                  type="password"
                  value={passwordForm.newPassword}
                />
                {passwordFieldErrors.newPassword ? (
                  <span className="mt-2 block text-sm text-danger">
                    {passwordFieldErrors.newPassword}
                  </span>
                ) : null}
              </label>

              <label className="block text-sm font-medium text-foreground-muted">
                Confirm new password
                <input
                  aria-invalid={Boolean(passwordFieldErrors.confirmPassword)}
                  autoComplete="new-password"
                  className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none transition focus:border-primary"
                  maxLength={72}
                  onChange={(event) => updatePasswordField("confirmPassword", event.target.value)}
                  type="password"
                  value={passwordForm.confirmPassword}
                />
                {passwordFieldErrors.confirmPassword ? (
                  <span className="mt-2 block text-sm text-danger">
                    {passwordFieldErrors.confirmPassword}
                  </span>
                ) : null}
              </label>
            </div>

            {passwordSaveError ? (
              <p className="rounded-2xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">
                {passwordSaveError}
              </p>
            ) : null}
            {passwordSuccessMessage ? (
              <p className="rounded-2xl border border-success-border bg-success-soft p-4 text-sm text-success" role="status">
                {passwordSuccessMessage}
              </p>
            ) : null}

            <button
              className="rounded-full bg-primary px-6 py-2.5 text-sm font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-wait disabled:opacity-60"
              disabled={isChangingPassword}
              type="submit"
            >
              {isChangingPassword ? "Changing password…" : "Change password"}
            </button>
          </form>
        </div>
      </section>
    </div>
  );
}

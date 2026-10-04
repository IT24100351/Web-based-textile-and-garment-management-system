import { Layers3, ShieldCheck, Shirt } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";

import {
  getApiErrorMessage,
  registerCustomer,
  resendEmailVerification,
  requestPasswordReset,
  resetPassword,
  verifyCustomerEmail,
} from "../api/auth";
import { BrandIdentity, BrandLockup } from "../components/BrandLogo";
import { WorkspaceBackground } from "../components/WorkspaceBackground";
import { buttonStyles } from "../components/ui/buttonStyles";
import { ThemeToggle } from "../components/ui/ThemeToggle";
import { emailVerificationPagePath } from "../navigation/navigation";
import { useAuth } from "./useAuth";

function AuthCard({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <WorkspaceBackground className="min-h-screen">
      <main className="relative flex min-h-screen items-center justify-center px-4 py-8 text-foreground sm:px-6 lg:py-12">
        <div className="absolute right-4 top-4 z-20 sm:right-6 sm:top-6"><ThemeToggle compact /></div>
        <section className="motion-auth-card app-shadow grid w-full max-w-5xl overflow-hidden rounded-3xl border border-border bg-surface/95 backdrop-blur-md lg:grid-cols-[1.05fr_.95fr]">
          <aside className="motion-auth-visual textile-grid relative hidden min-h-[44rem] overflow-hidden bg-primary-soft p-10 lg:flex lg:flex-col lg:justify-between">
            <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full border-[3rem] border-primary/10" />
            <Link aria-label="LankaWear Apparel home" className="relative w-fit" to="/"><BrandIdentity /></Link>
            <div className="relative">
              <BrandLockup className="mb-7 w-full max-w-sm" />
              <span className="grid h-14 w-14 place-items-center rounded-2xl border border-primary/20 bg-surface/80 text-primary"><Shirt aria-hidden="true" className="h-7 w-7" /></span>
              <h2 className="mt-6 max-w-md text-3xl font-bold tracking-tight text-foreground">One secure workspace for connected garment operations.</h2>
              <p className="mt-4 max-w-md leading-7 text-foreground-muted">Access the products, materials, orders, production and delivery tools permitted for your account.</p>
            </div>
            <div className="relative grid gap-3 text-sm text-foreground-muted">
              <span className="flex items-center gap-3"><ShieldCheck aria-hidden="true" className="h-5 w-5 text-primary" />Role-based, server-enforced access</span>
              <span className="flex items-center gap-3"><Layers3 aria-hidden="true" className="h-5 w-5 text-primary" />Connected operational records</span>
            </div>
          </aside>
          <div className="flex min-h-[36rem] items-center p-6 sm:p-10 lg:p-12">
            <div className="w-full">
              <Link className="text-sm font-semibold text-primary hover:text-primary-hover" to="/">← Public home</Link>
              <p className="mt-8 text-sm font-semibold tracking-wide text-primary">{eyebrow}</p>
              <h1 className="mt-2 text-3xl font-bold tracking-tight text-foreground">{title}</h1>
              {children}
            </div>
          </div>
        </section>
      </main>
    </WorkspaceBackground>
  );
}

const fieldClassName =
  "mt-2 min-h-12 w-full rounded-xl border border-border-strong bg-surface-elevated px-4 py-3 text-foreground outline-none transition focus:border-primary focus:ring-3 focus:ring-primary/15";

const buttonClassName =
  buttonStyles({ className: "mt-6 w-full", size: "lg" });

export function LoginPage() {
  const { user, isLoading: isSessionLoading, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const notice = typeof location.state === "object"
    && location.state !== null
    && "message" in location.state
    && typeof location.state.message === "string"
    ? location.state.message
    : null;
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isSessionLoading && user) {
    return <Navigate replace to="/" />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await login(email, password);
      navigate("/", { replace: true });
    } catch (loginError: unknown) {
      setError(getApiErrorMessage(loginError, "Login failed. Please try again."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthCard eyebrow="Secure access" title="Sign in">
      {notice ? (
        <p className="mt-6 rounded-xl border border-success-border bg-success-soft p-3 text-sm text-success" role="status">
          {notice}
        </p>
      ) : null}
      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        <label className="block text-sm font-medium" htmlFor="login-email">
          Email address
          <input
            autoComplete="email"
            className={fieldClassName}
            id="login-email"
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />
        </label>
        <div>
          <div className="flex items-center justify-between gap-3">
            <label className="block text-sm font-medium" htmlFor="login-password">Password</label>
            <Link
              className="text-xs font-medium text-primary hover:text-primary"
              to="/forgot-password"
            >
              Forgot password?
            </Link>
          </div>
          <input
            autoComplete="current-password"
            className={fieldClassName}
            id="login-password"
            minLength={8}
            name="password"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />
        </div>
        {error ? (
          <p aria-live="polite" className="rounded-xl bg-danger-soft p-3 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        <button className={buttonClassName} disabled={isSubmitting} type="submit">
          {isSubmitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        New customer?{" "}
        <Link className="font-medium text-primary hover:text-primary" to="/register">
          Create an account
        </Link>
      </p>
      <p className="mt-3 text-center text-sm text-muted">
        Already registered but not verified?{" "}
        <Link className="font-medium text-primary hover:text-primary" to={emailVerificationPagePath}>
          Verify account
        </Link>
      </p>
    </AuthCard>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isComplete, setIsComplete] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await requestPasswordReset({ email });
      setIsComplete(true);
    } catch (requestError: unknown) {
      setError(getApiErrorMessage(
        requestError,
        "We could not process the reset request. Please try again.",
      ));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isComplete) {
    return (
      <AuthCard eyebrow="Account recovery" title="Check your email">
        <p
          aria-live="polite"
          className="mt-6 rounded-xl bg-success-soft p-4 text-sm text-success"
        >
          If an active account matches that email, a password reset link will be sent.
        </p>
        <p className="mt-4 text-sm text-muted">
          For security, the reset link is temporary and can only be used once.
        </p>
        <Link className={`${buttonClassName} block text-center`} to="/login">
          Return to sign in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard eyebrow="Account recovery" title="Reset your password">
      <p className="mt-4 text-sm leading-6 text-muted">
        Enter the email address for your account. We will send reset instructions when the account
        is eligible for recovery.
      </p>
      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        <label className="block text-sm font-medium" htmlFor="forgot-email">
          Email address
          <input
            autoComplete="email"
            className={fieldClassName}
            id="forgot-email"
            maxLength={254}
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />
        </label>
        {error ? (
          <p
            aria-live="polite"
            className="rounded-xl bg-danger-soft p-3 text-sm text-danger"
            role="alert"
          >
            {error}
          </p>
        ) : null}
        <button className={buttonClassName} disabled={isSubmitting} type="submit">
          {isSubmitting ? "Sending instructions…" : "Send reset instructions"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Remembered your password?{" "}
        <Link className="font-medium text-primary hover:text-primary" to="/login">
          Sign in
        </Link>
      </p>
    </AuthCard>
  );
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isComplete, setIsComplete] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (password !== confirmation) {
      setError("Passwords do not match.");
      return;
    }

    setIsSubmitting(true);
    try {
      await resetPassword({ token, password });
      setIsComplete(true);
    } catch (resetError: unknown) {
      setError(getApiErrorMessage(
        resetError,
        "This password reset link is invalid or has expired. Request a new reset link.",
      ));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!token) {
    return (
      <AuthCard eyebrow="Account recovery" title="Invalid reset link">
        <p className="mt-6 rounded-xl bg-danger-soft p-4 text-sm text-danger" role="alert">
          This password reset link is invalid or incomplete. Request a new reset link to continue.
        </p>
        <Link className={`${buttonClassName} block text-center`} to="/forgot-password">
          Request a new reset link
        </Link>
      </AuthCard>
    );
  }

  if (isComplete) {
    return (
      <AuthCard eyebrow="Account recovery" title="Password updated">
        <p
          aria-live="polite"
          className="mt-6 rounded-xl bg-success-soft p-4 text-sm text-success"
        >
          Your password has been updated successfully. The reset link cannot be used again.
        </p>
        <Link className={`${buttonClassName} block text-center`} to="/login">
          Sign in with new password
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard eyebrow="Account recovery" title="Choose a new password">
      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        <div>
          <label className="block text-sm font-medium" htmlFor="reset-password">New password</label>
          <input
            aria-describedby="reset-password-help"
            autoComplete="new-password"
            className={fieldClassName}
            id="reset-password"
            maxLength={72}
            minLength={8}
            name="password"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />
          <span className="mt-2 block text-xs text-muted" id="reset-password-help">
            Use 8–72 characters.
          </span>
        </div>
        <label className="block text-sm font-medium" htmlFor="reset-confirmation">
          Confirm new password
          <input
            autoComplete="new-password"
            className={fieldClassName}
            id="reset-confirmation"
            maxLength={72}
            minLength={8}
            name="passwordConfirmation"
            onChange={(event) => setConfirmation(event.target.value)}
            required
            type="password"
            value={confirmation}
          />
        </label>
        {error ? (
          <p
            aria-live="polite"
            className="rounded-xl bg-danger-soft p-3 text-sm text-danger"
            role="alert"
          >
            {error}
          </p>
        ) : null}
        <button className={buttonClassName} disabled={isSubmitting} type="submit">
          {isSubmitting ? "Updating password…" : "Update password"}
        </button>
      </form>
    </AuthCard>
  );
}

export function RegistrationPage() {
  const { user, isLoading: isSessionLoading } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!isSessionLoading && user) {
    return <Navigate replace to="/" />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (password !== confirmation) {
      setError("Passwords do not match.");
      return;
    }

    setIsSubmitting(true);
    try {
      await registerCustomer({ fullName, email, password });
      const parameters = new URLSearchParams({
        email: email.trim().toLowerCase(),
        sent: "1",
      });
      navigate(`${emailVerificationPagePath}?${parameters.toString()}`, { replace: true });
    } catch (registrationError: unknown) {
      setError(getApiErrorMessage(registrationError, "Account creation failed. Please try again."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthCard eyebrow="Customer registration" title="Create an account">
      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        <label className="block text-sm font-medium" htmlFor="register-name">
          Full name
          <input
            autoComplete="name"
            className={fieldClassName}
            id="register-name"
            maxLength={120}
            name="fullName"
            onChange={(event) => setFullName(event.target.value)}
            required
            value={fullName}
          />
        </label>
        <label className="block text-sm font-medium" htmlFor="register-email">
          Email address
          <input
            autoComplete="email"
            className={fieldClassName}
            id="register-email"
            maxLength={254}
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />
        </label>
        <div>
          <label className="block text-sm font-medium" htmlFor="register-password">Password</label>
          <input
            aria-describedby="password-help"
            autoComplete="new-password"
            className={fieldClassName}
            id="register-password"
            maxLength={72}
            minLength={8}
            name="password"
            onChange={(event) => setPassword(event.target.value)}
            required
            type="password"
            value={password}
          />
          <span className="mt-2 block text-xs text-muted" id="password-help">
            Use 8–72 characters.
          </span>
        </div>
        <label className="block text-sm font-medium" htmlFor="register-confirmation">
          Confirm password
          <input
            autoComplete="new-password"
            className={fieldClassName}
            id="register-confirmation"
            maxLength={72}
            minLength={8}
            name="passwordConfirmation"
            onChange={(event) => setConfirmation(event.target.value)}
            required
            type="password"
            value={confirmation}
          />
        </label>
        {error ? (
          <p aria-live="polite" className="rounded-xl bg-danger-soft p-3 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        <button className={buttonClassName} disabled={isSubmitting} type="submit">
          {isSubmitting ? "Creating account…" : "Create customer account"}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Already registered?{" "}
        <Link className="font-medium text-primary hover:text-primary" to="/login">
          Sign in
        </Link>
      </p>
    </AuthCard>
  );
}

export function EmailVerificationPage() {
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState(() => searchParams.get("email")?.trim() ?? "");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const codeWasJustSent = searchParams.get("sent") === "1";
  const [statusMessage, setStatusMessage] = useState<string | null>(() =>
    codeWasJustSent && email
      ? `A verification code has been sent to ${email}. Check that account's Inbox and Spam folder.`
      : null
  );
  const [resendCooldown, setResendCooldown] = useState(codeWasJustSent ? 60 : 0);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [isVerified, setIsVerified] = useState(false);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = window.setTimeout(
      () => setResendCooldown((seconds) => Math.max(0, seconds - 1)),
      1_000,
    );
    return () => window.clearTimeout(timer);
  }, [resendCooldown]);

  async function handleVerification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setStatusMessage(null);
    setIsSubmitting(true);
    try {
      const response = await verifyCustomerEmail({ email, code });
      setStatusMessage(response.message);
      setIsVerified(true);
    } catch (verificationError: unknown) {
      setError(getApiErrorMessage(
        verificationError,
        "The verification code is invalid or expired. Request a new code and try again.",
      ));
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleResend() {
    if (!email.trim() || isResending) return;
    setError(null);
    setStatusMessage(null);
    setIsResending(true);
    try {
      const response = await resendEmailVerification({ email });
      setStatusMessage(`${response.message} Check ${email.trim()}'s Inbox and Spam folder.`);
      setResendCooldown(60);
    } catch (resendError: unknown) {
      setError(getApiErrorMessage(
        resendError,
        "A new verification code could not be requested. Please try again.",
      ));
    } finally {
      setIsResending(false);
    }
  }

  if (isVerified) {
    return (
      <AuthCard eyebrow="Account verified" title="Email verified">
        <p aria-live="polite" className="mt-6 rounded-xl bg-success-soft p-4 text-success">
          {statusMessage ?? "Email verified successfully. You can now sign in."}
        </p>
        <Link className={`${buttonClassName} block text-center`} to="/login">
          Continue to sign in
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard eyebrow="Customer verification" title="Verify your email">
      <p className="mt-4 text-sm leading-6 text-muted">
        Enter the six-digit code sent to{" "}
        <strong className="break-all text-foreground">
          {email || "the email address used during registration"}
        </strong>
        . The code expires after 15 minutes. This may be different from the mailbox configured to
        send LankaWear emails.
      </p>
      <form className="mt-8 space-y-5" onSubmit={handleVerification}>
        <label className="block text-sm font-medium" htmlFor="verification-email">
          Email address
          <input
            autoComplete="email"
            className={fieldClassName}
            id="verification-email"
            maxLength={254}
            name="email"
            onChange={(event) => setEmail(event.target.value)}
            required
            type="email"
            value={email}
          />
        </label>
        <label className="block text-sm font-medium" htmlFor="verification-code">
          Verification code
          <input
            autoComplete="one-time-code"
            className={`${fieldClassName} text-center text-xl tracking-[0.35em]`}
            id="verification-code"
            inputMode="numeric"
            maxLength={6}
            name="code"
            onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            pattern="[0-9]{6}"
            placeholder="000000"
            required
            value={code}
          />
        </label>
        {error ? (
          <p aria-live="polite" className="rounded-xl bg-danger-soft p-3 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        {statusMessage ? (
          <p aria-live="polite" className="rounded-xl bg-info-soft p-3 text-sm text-info">
            {statusMessage}
          </p>
        ) : null}
        <button className={buttonClassName} disabled={isSubmitting || code.length !== 6} type="submit">
          {isSubmitting ? "Verifying…" : "Verify email"}
        </button>
      </form>
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
        <button
          className="font-medium text-primary hover:text-primary-hover disabled:opacity-50"
          disabled={isResending || resendCooldown > 0 || !email.trim()}
          onClick={() => void handleResend()}
          type="button"
        >
          {isResending
            ? "Requesting…"
            : resendCooldown > 0
              ? `Send again in ${resendCooldown}s`
              : "Send a new code"}
        </button>
        <Link className="font-medium text-primary hover:text-primary-hover" to="/login">
          Return to sign in
        </Link>
      </div>
    </AuthCard>
  );
}

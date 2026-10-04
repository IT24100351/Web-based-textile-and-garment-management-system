import { AlertCircle, LoaderCircle, PackageOpen, SearchX, type LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";

import { buttonStyles } from "./ui/buttonStyles";
import { cn } from "./ui/cn";

interface StatePanelProps {
  eyebrow: string;
  title: string;
  message: string;
  tone?: "neutral" | "danger";
  action?: React.ReactNode;
  Icon?: LucideIcon;
  isLoading?: boolean;
}

function StatePanel({
  eyebrow,
  title,
  message,
  tone = "neutral",
  action,
  Icon = PackageOpen,
  isLoading = false,
}: StatePanelProps) {
  const accentClass = tone === "danger" ? "bg-danger-soft text-danger" : "bg-primary-soft text-primary";

  return (
    <section className="motion-state-panel app-shadow rounded-2xl border border-border bg-surface p-7 sm:p-9">
      <span className={`grid h-12 w-12 place-items-center rounded-2xl ${accentClass}`}>
        <Icon aria-hidden="true" className={cn("h-6 w-6", isLoading && "animate-spin motion-reduce:animate-none")} />
      </span>
      <p className={`mt-5 text-sm font-semibold ${tone === "danger" ? "text-danger" : "text-primary"}`}>{eyebrow}</p>
      <h1 className="mt-2 text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{title}</h1>
      <p className="mt-3 max-w-2xl leading-7 text-foreground-muted">{message}</p>
      {isLoading ? (
        <div aria-hidden="true" className="mt-6 grid max-w-xl gap-3">
          <span className="motion-skeleton h-3 w-full rounded-full" />
          <span className="motion-skeleton h-3 w-4/5 rounded-full" />
          <span className="motion-skeleton h-3 w-3/5 rounded-full" />
        </div>
      ) : null}
      {action ? <div className="mt-7">{action}</div> : null}
    </section>
  );
}

export function LoadingState({
  title = "Loading…",
  message = "Please wait while the requested information is prepared.",
}: {
  title?: string;
  message?: string;
}) {
  return (
    <div aria-live="polite" role="status">
      <StatePanel Icon={LoaderCircle} eyebrow="Loading" isLoading message={message} title={title} />
    </div>
  );
}

export function EmptyState({
  title = "Nothing here yet",
  message,
  action,
}: {
  title?: string;
  message: string;
  action?: React.ReactNode;
}) {
  return <StatePanel action={action} Icon={SearchX} eyebrow="No results" message={message} title={title} />;
}

export function ErrorState({
  title = "Something went wrong",
  message,
  action,
}: {
  title?: string;
  message: string;
  action?: React.ReactNode;
}) {
  return (
    <div role="alert">
      <StatePanel action={action} Icon={AlertCircle} eyebrow="Unable to continue" message={message} title={title} tone="danger" />
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-3xl p-6 sm:p-10">
      <StatePanel
        action={<Link className={buttonStyles()} to="/">Return to overview</Link>}
        Icon={SearchX}
        eyebrow="404 · Not found"
        message="The requested page does not exist or may have moved. Use the navigation to continue safely."
        title="Page not found"
      />
    </div>
  );
}

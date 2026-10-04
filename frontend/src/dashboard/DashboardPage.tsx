import { BarChart3, CalendarDays, Factory, PackageSearch, ShoppingBag, Truck, Users, Warehouse, type LucideIcon } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";

import { getApiErrorMessage, type UserRole } from "../api/auth";
import { getDashboardReport, type DashboardReport } from "../api/reports";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { fieldStyles } from "../components/ui/fieldStyles";
import { PageHeader } from "../components/ui/PageHeader";
import { roleLabels } from "../navigation/navigation";
import { MotionSwap } from "../motion/MotionSwap";

const dashboardDescriptions: Record<UserRole, string> = {
  ADMINISTRATOR: "System-wide operational monitoring from stored Order, Inventory, Production and Delivery records.",
  SUPPLIER: "Supplier access remains limited to Supplier Management; unrelated operational reports are not exposed.",
  INVENTORY_MANAGER: "Current inventory health and low-stock visibility from Fabric Inventory Management.",
  PRODUCTION_MANAGER: "Production task activity from the Production Management source of truth.",
  SALES_OFFICER: "Order and Delivery activity relevant to sales operations.",
  CUSTOMER: "A private summary of only your own orders.",
};

const roleIcons: Record<UserRole, LucideIcon> = {
  ADMINISTRATOR: Users,
  SUPPLIER: PackageSearch,
  INVENTORY_MANAGER: Warehouse,
  PRODUCTION_MANAGER: Factory,
  SALES_OFFICER: Truck,
  CUSTOMER: ShoppingBag,
};

function isoUtcDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

function defaultDates() {
  const to = new Date();
  const from = new Date(to);
  from.setUTCDate(from.getUTCDate() - 29);
  return { from: isoUtcDate(from), to: isoUtcDate(to) };
}

function MetricCard({ label, value }: { label: string; value: number }) {
  return (
    <article className="motion-interactive-card rounded-2xl border border-border bg-surface-elevated p-5 hover:border-primary/35">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-medium leading-5 text-foreground-muted">{label}</p>
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary-soft text-primary"><BarChart3 aria-hidden="true" className="h-4 w-4" /></span>
      </div>
      <p className="mt-5 text-3xl font-bold tracking-tight text-foreground">{value.toLocaleString()}</p>
    </article>
  );
}

type ReportState =
  | { status: "loading" }
  | { status: "success"; report: DashboardReport }
  | { status: "error"; message: string };

export function DashboardPage({ role }: { role: UserRole }) {
  const initialDates = useMemo(() => defaultDates(), []);
  const [from, setFrom] = useState(initialDates.from);
  const [to, setTo] = useState(initialDates.to);
  const [appliedFrom, setAppliedFrom] = useState(initialDates.from);
  const [appliedTo, setAppliedTo] = useState(initialDates.to);
  const [state, setState] = useState<ReportState>({ status: "loading" });
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  const load = useCallback((nextFrom: string, nextTo: string, signal?: AbortSignal) => {
    setState({ status: "loading" });
    void getDashboardReport(nextFrom, nextTo, signal)
      .then((report) => setState({ status: "success", report }))
      .catch((error: unknown) => {
        if (signal?.aborted) return;
        setState({ status: "error", message: getApiErrorMessage(error, "The dashboard report could not be loaded. Please try again.") });
      });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    load(appliedFrom, appliedTo, controller.signal);
    return () => controller.abort();
  }, [appliedFrom, appliedTo, load, role]);

  function applyFilters(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!from || !to) {
      setValidationMessage("Choose both a from date and a to date.");
      return;
    }
    if (from > to) {
      setValidationMessage("From date must be on or before the to date.");
      return;
    }
    setValidationMessage(null);
    setAppliedFrom(from);
    setAppliedTo(to);
  }

  const roleLabel = roleLabels[role];
  const RoleIcon = roleIcons[role];
  const report = state.status === "success" ? state.report : null;
  const allMetricsAreZero = report !== null
    && report.sections.length > 0
    && report.sections.every((section) => section.metrics.every((metric) => metric.value === 0));
  const reportStateKey = state.status !== "success"
    ? state.status
    : state.report.sections.length === 0
      ? "empty"
      : "content";

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-5 sm:p-8 lg:p-10">
      <Card className="textile-grid overflow-hidden p-6 sm:p-8">
        <div className="flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
          <PageHeader
            description={dashboardDescriptions[role]}
            eyebrow="Role dashboard · Real operational data"
            title={`${roleLabel} dashboard`}
          />
          <span className="grid h-16 w-16 shrink-0 place-items-center rounded-2xl bg-primary text-primary-foreground shadow-lg shadow-primary/15"><RoleIcon aria-hidden="true" className="h-7 w-7" /></span>
        </div>
      </Card>

      <Card>
        <form className="flex flex-col gap-5 p-5 lg:flex-row lg:items-end lg:justify-between sm:p-6" onSubmit={applyFilters}>
          <div><div className="flex items-center gap-2"><CalendarDays aria-hidden="true" className="h-5 w-5 text-primary" /><h2 className="text-lg font-bold text-foreground">Reporting period</h2></div><p className="mt-1 text-sm text-muted">Inclusive UTC calendar dates.</p></div>
          <div className="flex flex-col gap-3 xl:flex-row xl:items-end">
            <label className="text-sm font-semibold text-foreground">From<input className={`${fieldStyles} sm:w-40`} max={to || undefined} onChange={(event) => setFrom(event.target.value)} type="date" value={from} /></label>
            <label className="text-sm font-semibold text-foreground">To<input className={`${fieldStyles} sm:w-40`} min={from || undefined} onChange={(event) => setTo(event.target.value)} type="date" value={to} /></label>
            <Button type="submit">Apply filters</Button>
          </div>
        </form>
        {validationMessage ? <p className="border-t border-danger-border bg-danger-soft px-6 py-3 text-sm text-danger" role="alert">{validationMessage}</p> : null}
      </Card>

      <MotionSwap stateKey={reportStateKey}>
        {state.status === "loading" ? (
          <LoadingState title="Loading report…" message="Aggregating permitted operational records from the server." />
        ) : state.status === "error" ? (
          <ErrorState action={<Button onClick={() => load(appliedFrom, appliedTo)} variant="outline">Retry report</Button>} message={state.message} title="Report unavailable" />
        ) : state.report.sections.length === 0 ? (
          <EmptyState message={state.report.notes[0] ?? "No operational report scope is available for this role."} title="No permitted operational report sections" />
        ) : (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-surface px-5 py-4 text-sm text-foreground-muted">
              <p><span className="font-semibold text-foreground">Applied filters:</span> {state.report.from} through {state.report.to} · {state.report.timeZone}</p>
            </div>

            {allMetricsAreZero ? <EmptyState message="The permitted source modules contain no matching report activity for this view. Zero values are shown below rather than fabricated analytics." title="No matching report activity" /> : null}

            {state.report.sections.map((section) => (
              <Card className="overflow-hidden" key={section.key}>
                <div className="flex flex-col justify-between gap-3 border-b border-border p-5 sm:flex-row sm:items-start sm:p-6">
                  <div><h2 className="text-xl font-bold text-foreground">{section.title}</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-foreground-muted">{section.basis}</p></div>
                  <Badge tone="primary">Stored data</Badge>
                </div>
                <div className="motion-stagger grid gap-3 p-5 sm:grid-cols-2 sm:p-6 lg:grid-cols-4">{section.metrics.map((metric) => <MetricCard key={metric.key} label={metric.label} value={metric.value} />)}</div>
              </Card>
            ))}

            <Card className="p-5 sm:p-6"><h2 className="font-bold text-foreground">Report notes</h2><ul className="mt-3 list-disc space-y-2 pl-5 text-sm leading-6 text-foreground-muted">{state.report.notes.map((note) => <li key={note}>{note}</li>)}</ul><p className="mt-4 text-xs text-muted">Generated {new Date(state.report.generatedAt).toLocaleString()}</p></Card>
          </div>
        )}
      </MotionSwap>
    </div>
  );
}

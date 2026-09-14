import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
  getProductionTaskApiError,
  getProductionTaskRecords,
  type ProductionTaskRecordSummary,
  type ProductionTaskRecordView,
} from "../api/productionTasks";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { getProductionTaskDetailPagePath } from "../navigation/navigation";

export function ProductionTaskRecordsPage() {
  const [view, setView] = useState<ProductionTaskRecordView>("ACTIVE");
  const [records, setRecords] = useState<ProductionTaskRecordSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setError(null);
    getProductionTaskRecords(view, controller.signal)
      .then(setRecords)
      .catch((reason: unknown) => {
        if (controller.signal.aborted) return;
        setError(getProductionTaskApiError(reason, "Production records could not be loaded.").message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [view]);

  return (
    <section className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">Production Management</p>
          <h1 className="mt-2 text-3xl font-bold text-foreground">Production records</h1>
          <p className="mt-2 max-w-3xl text-muted">Review active and completed manufacturing tasks, quality-control state, and the linked Order readiness signal.</p>
        </div>
        <label className="text-sm font-medium text-foreground">
          Records
          <select className="ml-3 rounded-xl border border-border bg-background px-4 py-2 text-foreground" onChange={(event) => setView(event.target.value as ProductionTaskRecordView)} value={view}>
            <option value="ACTIVE">Active</option>
            <option value="COMPLETED">Completed</option>
            <option value="ALL">All</option>
          </select>
        </label>
      </div>

      {isLoading ? <LoadingState title="Loading production records…" message="Reading active and completed Production tasks." /> : null}
      {!isLoading && error ? <ErrorState message={error} /> : null}
      {!isLoading && !error && records.length === 0 ? (
        <EmptyState title={view === "COMPLETED" ? "No completed production records" : "No production records found"} message="Production tasks matching this view will appear here." />
      ) : null}

      {!isLoading && !error && records.length > 0 ? (
        <div className="mt-8 grid gap-4 lg:grid-cols-2" aria-label="Production records">
          {records.map((record) => (
            <article className="motion-record rounded-3xl border border-border bg-surface/70 p-6" key={record.task.id}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted">Task #{record.task.id}</p>
                  <h2 className="mt-1 text-xl font-bold text-foreground">{record.task.taskNumber}</h2>
                  <p className="mt-1 text-sm text-muted">{record.orderNumber} · Customer #{record.customerId}</p>
                </div>
                <span className="rounded-full border border-border px-3 py-1 text-xs font-semibold text-foreground">{record.task.status.replaceAll("_", " ")}</span>
              </div>
              <dl className="mt-5 grid grid-cols-2 gap-4 text-sm">
                <div><dt className="text-muted">QC</dt><dd className="mt-1 font-semibold text-foreground">{record.task.qualityControlResult}</dd></div>
                <div><dt className="text-muted">Order items</dt><dd className="mt-1 font-semibold text-foreground">{record.orderItemCount}</dd></div>
                <div><dt className="text-muted">Order status</dt><dd className="mt-1 font-semibold text-foreground">{record.orderStatus.replaceAll("_", " ")}</dd></div>
                <div><dt className="text-muted">Delivery signal</dt><dd className="mt-1 font-semibold text-foreground">{record.readyForDelivery ? "READY" : "NOT READY"}</dd></div>
              </dl>
              {record.task.completedAt ? <p className="mt-4 text-xs text-muted">Completed {new Date(record.task.completedAt).toLocaleString()}</p> : null}
              <Link className="mt-5 inline-flex rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" to={getProductionTaskDetailPagePath(record.task.id)}>View production record</Link>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}

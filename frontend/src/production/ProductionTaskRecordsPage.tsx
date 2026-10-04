import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";

import {
  getProductionTaskApiError,
  getProductionTaskRecords,
  type ProductionTaskRecordSummary,
  type ProductionTaskRecordView,
} from "../api/productionTasks";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { MotionSwap } from "../motion/MotionSwap";
import { getProductionTaskDetailPagePath } from "../navigation/navigation";

interface ProductionRecordAction {
  label: string;
  nextStep: string;
}

function getProductionRecordAction(record: ProductionTaskRecordSummary): ProductionRecordAction {
  if (record.task.status === "COMPLETED") {
    return {
      label: "View completed record",
      nextStep: record.readyForDelivery
        ? "Production is complete and the order is ready for delivery."
        : record.orderStatus === "IN_PRODUCTION"
          ? "This task is complete; other production tasks for the order are still pending."
          : "Production is complete; check the linked order's current status.",
    };
  }

  if (record.task.status === "PENDING") {
    return {
      label: "Prepare production",
      nextStep: "Assign required materials and start production when stock is available.",
    };
  }

  if (record.task.status === "IN_PROGRESS" && record.task.qualityControlResult === "PASSED") {
    return {
      label: "Complete production",
      nextStep: "Quality control has passed. Open the record to mark production completed.",
    };
  }

  return {
    label: "Update production",
    nextStep: "Record material usage and pass quality control before completion.",
  };
}

export function ProductionTaskRecordsPage() {
  const location = useLocation();
  const deletedTaskNumber = (location.state as { deletedTaskNumber?: string } | null)?.deletedTaskNumber;
  const [view, setView] = useState<ProductionTaskRecordView>("ACTIVE");
  const [records, setRecords] = useState<ProductionTaskRecordSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
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
        if (!controller.signal.aborted) {
          setIsLoading(false);
          setHasLoaded(true);
        }
      });
    return () => controller.abort();
  }, [view]);

  return (
    <section className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      {deletedTaskNumber ? <p className="mb-5 rounded-xl border border-success-border bg-success-soft p-4 text-sm text-success" role="status">Pending production task {deletedTaskNumber} deleted successfully.</p> : null}
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

      <div aria-busy={isLoading} className="mt-8">
        <MotionSwap stateKey={!hasLoaded ? "loading" : error && records.length === 0 ? "error" : records.length === 0 ? "empty" : "content"}>
          {!hasLoaded ? (
            <LoadingState title="Loading production records…" message="Reading active and completed Production tasks." />
          ) : error && records.length === 0 ? (
            <ErrorState message={error} />
          ) : records.length === 0 ? (
            <EmptyState title={view === "COMPLETED" ? "No completed production records" : "No production records found"} message="Production tasks matching this view will appear here." />
          ) : (
            <div>
              {isLoading ? <p className="mb-3 text-sm text-muted" role="status">Updating production records…</p> : null}
              {error ? <p className="mb-3 rounded-xl border border-warning-border bg-warning-soft p-3 text-sm text-warning" role="alert">{error}</p> : null}
              <div className="grid gap-4 lg:grid-cols-2" aria-label="Production records">
          {records.map((record) => {
            const action = getProductionRecordAction(record);

            return (
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
                <div className="mt-5 border-t border-border pt-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted">Next step</p>
                  <p className="mt-1 text-sm text-foreground">{action.nextStep}</p>
                </div>
                {record.task.completedAt ? <p className="mt-4 text-xs text-muted">Completed {new Date(record.task.completedAt).toLocaleString()}</p> : null}
                <Link className="mt-5 inline-flex rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" to={getProductionTaskDetailPagePath(record.task.id)}>{action.label}</Link>
              </article>
            );
          })}
              </div>
            </div>
          )}
        </MotionSwap>
      </div>
    </section>
  );
}

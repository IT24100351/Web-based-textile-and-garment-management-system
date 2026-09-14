import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";

import {
  getProductionTaskApiError,
  getProductionTaskDetail,
  getProductionTaskMaterialAvailability,
  getProductionTaskMaterialOptions,
  getProductionTaskMaterialUsage,
  recordProductionTaskMaterialUsage,
  startProductionTask,
  updateProductionTaskStatus,
  updateProductionTaskDetails,
  updateProductionTaskMaterials,
  updateProductionQualityControl,
  type ProductionMaterialOption,
  type ProductionTaskDetail,
  type ProductionTaskMaterialAvailabilityReport,
  type ProductionTaskMaterialUsageReport,
} from "../api/productionTasks";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { parseProductionTaskPageId } from "../navigation/navigation";

const inputClassName =
  "mt-2 w-full rounded-xl border border-border bg-background/70 px-4 py-3 text-foreground outline-none transition focus:border-primary";

interface FieldErrors {
  workDetails?: string;
  workAssignment?: string;
  workNotes?: string;
}

interface MaterialRow {
  inventoryMaterialId: string;
  requiredQuantity: string;
}

const blankMaterialRow = (): MaterialRow => ({ inventoryMaterialId: "", requiredQuantity: "" });

export function ProductionTaskDetailPage() {
  const { taskId: taskIdParam } = useParams();
  const taskId = parseProductionTaskPageId(taskIdParam);
  const [detail, setDetail] = useState<ProductionTaskDetail | null>(null);
  const [materialOptions, setMaterialOptions] = useState<ProductionMaterialOption[]>([]);
  const [materialAvailability, setMaterialAvailability] = useState<ProductionTaskMaterialAvailabilityReport | null>(null);
  const [materialUsage, setMaterialUsage] = useState<ProductionTaskMaterialUsageReport | null>(null);
  const [materialRows, setMaterialRows] = useState<MaterialRow[]>([blankMaterialRow()]);
  const [workDetails, setWorkDetails] = useState("");
  const [workAssignment, setWorkAssignment] = useState("");
  const [workNotes, setWorkNotes] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [materialErrors, setMaterialErrors] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(taskId !== null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [materialSubmitError, setMaterialSubmitError] = useState<string | null>(null);
  const [materialSuccessMessage, setMaterialSuccessMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSavingMaterials, setIsSavingMaterials] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  const [startSuccessMessage, setStartSuccessMessage] = useState<string | null>(null);
  const [isRecordingUsage, setIsRecordingUsage] = useState(false);
  const [usageError, setUsageError] = useState<string | null>(null);
  const [usageSuccessMessage, setUsageSuccessMessage] = useState<string | null>(null);
  const [isCompleting, setIsCompleting] = useState(false);
  const [progressError, setProgressError] = useState<string | null>(null);
  const [progressSuccessMessage, setProgressSuccessMessage] = useState<string | null>(null);
  const [isSavingQuality, setIsSavingQuality] = useState(false);
  const [qualityError, setQualityError] = useState<string | null>(null);
  const [qualitySuccessMessage, setQualitySuccessMessage] = useState<string | null>(null);

  function applyLoadedDetail(loaded: ProductionTaskDetail) {
    setDetail(loaded);
    setWorkDetails(loaded.workDetails?.workDetails ?? "");
    setWorkAssignment(loaded.workDetails?.workAssignment ?? "");
    setWorkNotes(loaded.workDetails?.workNotes ?? "");
    setMaterialRows(
      loaded.materialRequirements.length > 0
        ? loaded.materialRequirements.map((item) => ({
            inventoryMaterialId: String(item.inventoryMaterialId),
            requiredQuantity: item.requiredQuantity,
          }))
        : [blankMaterialRow()],
    );
  }

  async function load(signal?: AbortSignal) {
    if (taskId === null) return;
    setIsLoading(true);
    setLoadError(null);
    try {
      const [loaded, options, availability, usage] = await Promise.all([
        getProductionTaskDetail(taskId, signal),
        getProductionTaskMaterialOptions(signal),
        getProductionTaskMaterialAvailability(taskId, signal),
        getProductionTaskMaterialUsage(taskId, signal),
      ]);
      applyLoadedDetail(loaded);
      setMaterialOptions(options);
      setMaterialAvailability(availability);
      setMaterialUsage(usage);
    } catch (error: unknown) {
      if (signal?.aborted) return;
      const apiError = getProductionTaskApiError(
        error,
        "The production task details could not be loaded. Please try again.",
      );
      setLoadError(apiError.message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }

  useEffect(() => {
    if (taskId === null) return;
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
    // The route task ID is the complete lookup key; load also initializes
    // multiple related API resources and must not retrigger on state changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  function validateWorkDetails() {
    const errors: FieldErrors = {};
    if (!workDetails.trim()) {
      errors.workDetails = "Enter the manufacturing work details for this task.";
    } else if (workDetails.trim().length > 1000) {
      errors.workDetails = "Work details must be 1000 characters or fewer.";
    }
    if (!workAssignment.trim()) {
      errors.workAssignment = "Enter a work assignment, team, line, or responsible work unit.";
    } else if (workAssignment.trim().length > 255) {
      errors.workAssignment = "Work assignment must be 255 characters or fewer.";
    }
    if (workNotes.trim().length > 2000) {
      errors.workNotes = "Work notes must be 2000 characters or fewer.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  function validateMaterials() {
    const errors: Record<string, string> = {};
    const seen = new Set<string>();
    if (materialRows.length === 0) {
      errors.materials = "Assign at least one required inventory material.";
    }
    materialRows.forEach((row, index) => {
      const idKey = `materials[${index}].inventoryMaterialId`;
      const quantityKey = `materials[${index}].requiredQuantity`;
      if (!row.inventoryMaterialId) {
        errors[idKey] = "Select a valid inventory material.";
      } else if (seen.has(row.inventoryMaterialId)) {
        errors[idKey] = "This inventory material is already assigned to the production task.";
      } else {
        seen.add(row.inventoryMaterialId);
      }
      const quantity = row.requiredQuantity.trim();
      if (!quantity) {
        errors[quantityKey] = "Required quantity is required.";
      } else if (!/^\d+(?:\.\d{1,3})?$/.test(quantity) || Number(quantity) <= 0) {
        errors[quantityKey] = "Enter a positive quantity with up to 3 decimal places.";
      }
    });
    setMaterialErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setSuccessMessage(null);
    if (taskId === null || !validateWorkDetails()) return;
    setIsSubmitting(true);
    try {
      const result = await updateProductionTaskDetails(taskId, {
        workDetails: workDetails.trim(),
        workAssignment: workAssignment.trim(),
        workNotes: workNotes.trim() || null,
      });
      applyLoadedDetail(result.task);
      setFieldErrors({});
      setSuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "The production task details could not be saved. Please try again.",
      );
      setSubmitError(apiError.message);
      setFieldErrors({
        workDetails: apiError.fields.workDetails,
        workAssignment: apiError.fields.workAssignment,
        workNotes: apiError.fields.workNotes,
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function submitMaterials(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMaterialSubmitError(null);
    setMaterialSuccessMessage(null);
    if (taskId === null || !validateMaterials()) return;
    setIsSavingMaterials(true);
    try {
      const result = await updateProductionTaskMaterials(
        taskId,
        materialRows.map((row) => ({
          inventoryMaterialId: Number(row.inventoryMaterialId),
          requiredQuantity: row.requiredQuantity.trim(),
        })),
      );
      applyLoadedDetail(result.task);
      setMaterialErrors({});
      setMaterialSuccessMessage(result.message);
      setMaterialAvailability(await getProductionTaskMaterialAvailability(taskId));
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "The production material requirements could not be saved. Please try again.",
      );
      setMaterialSubmitError(apiError.message);
      setMaterialErrors(apiError.fields);
    } finally {
      setIsSavingMaterials(false);
    }
  }

  async function startTask() {
    if (taskId === null) return;
    setStartError(null);
    setStartSuccessMessage(null);
    setIsStarting(true);
    try {
      const result = await startProductionTask(taskId);
      applyLoadedDetail(result.task);
      setStartSuccessMessage(result.message);
      setMaterialAvailability(await getProductionTaskMaterialAvailability(taskId));
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "Production could not be started. Check the required materials and try again.",
      );
      setStartError(apiError.message);
      try {
        setMaterialAvailability(await getProductionTaskMaterialAvailability(taskId));
      } catch {
        // Keep the actionable start error when the readiness refresh also fails.
      }
    } finally {
      setIsStarting(false);
    }
  }

  async function completeTask() {
    if (taskId === null) return;
    setProgressError(null);
    setProgressSuccessMessage(null);
    setIsCompleting(true);
    try {
      const result = await updateProductionTaskStatus(taskId, "COMPLETED");
      applyLoadedDetail(result.task);
      setProgressSuccessMessage(result.message);
      setMaterialAvailability(await getProductionTaskMaterialAvailability(taskId));
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "Production status could not be updated. Check the completion requirements and try again.",
      );
      setProgressError(apiError.fields.materialUsage ?? apiError.fields.qualityControl ?? apiError.fields.status ?? apiError.message);
    } finally {
      setIsCompleting(false);
    }
  }

  async function recordQualityControl(resultValue: "PASSED" | "FAILED") {
    if (taskId === null) return;
    setQualityError(null);
    setQualitySuccessMessage(null);
    setIsSavingQuality(true);
    try {
      const result = await updateProductionQualityControl(taskId, resultValue);
      applyLoadedDetail(result.task);
      setQualitySuccessMessage(result.message);
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "Quality control could not be recorded. Check the task state and material usage.",
      );
      setQualityError(apiError.fields.materialUsage ?? apiError.fields.qualityControl ?? apiError.fields.status ?? apiError.message);
    } finally {
      setIsSavingQuality(false);
    }
  }

  async function recordMaterialUsage() {
    if (taskId === null) return;
    setUsageError(null);
    setUsageSuccessMessage(null);
    setIsRecordingUsage(true);
    try {
      const result = await recordProductionTaskMaterialUsage(taskId);
      setMaterialUsage(result.usage);
      setUsageSuccessMessage(result.message);
      const [loaded, availability] = await Promise.all([
        getProductionTaskDetail(taskId),
        getProductionTaskMaterialAvailability(taskId),
      ]);
      applyLoadedDetail(loaded);
      setMaterialAvailability(availability);
    } catch (error: unknown) {
      const apiError = getProductionTaskApiError(
        error,
        "Production material usage could not be recorded. Inventory was not partially updated.",
      );
      setUsageError(apiError.message);
    } finally {
      setIsRecordingUsage(false);
    }
  }

  if (taskId === null) {
    return <div className="mx-auto max-w-5xl p-6 sm:p-10"><EmptyState title="Invalid production task" message="Use a valid positive production task ID to view work details." /></div>;
  }
  if (isLoading) {
    return <div className="mx-auto max-w-5xl p-6 sm:p-10"><LoadingState title="Loading production task…" message="Reading the Production task and its linked Order and Inventory contracts." /></div>;
  }
  if (loadError || !detail) {
    return (
      <div className="mx-auto max-w-5xl p-6 sm:p-10">
        <ErrorState title="Production task unavailable" message={loadError ?? "The production task was not found."} action={<button className="rounded-xl bg-primary px-5 py-3 font-semibold text-primary-foreground" onClick={() => void load()} type="button">Retry</button>} />
      </div>
    );
  }

  return (
    <section className="px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <div className="mx-auto max-w-5xl space-y-7">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-primary">Production Management</p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground sm:text-4xl">Production task details</h1>
          <p className="mt-3 text-muted">Organize manufacturing work and required Inventory materials without duplicating Order or Inventory source-of-truth data.</p>
        </div>

        <section className="rounded-3xl border border-border bg-surface/70 p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Task</p>
              <h2 className="mt-2 text-2xl font-bold text-foreground">{detail.task.taskNumber}</h2>
              <p className="mt-1 text-sm text-muted">Task ID #{detail.task.id} · Order {detail.order.orderNumber} · Order ID #{detail.order.orderId}</p>
              <p className="mt-1 text-sm text-muted">Order progress: <strong className="text-foreground">{detail.order.currentStatus.replaceAll("_", " ")}</strong></p>
            </div>
            <span className="rounded-full border border-border px-4 py-2 text-sm font-semibold text-foreground">{detail.task.status.replaceAll("_", " ")}</span>
          </div>
          <div className="mt-6 space-y-3" aria-label="Linked order items">
            {detail.order.items.map((item) => (
              <article className="rounded-2xl border border-border bg-background/50 p-4" key={item.orderItemId}>
                <p className="font-semibold text-foreground">Order item #{item.orderItemId}</p>
                <p className="mt-2 text-sm text-foreground-muted">Product #{item.productId} · Variant #{item.variantId} · {item.selectedSize} · {item.selectedColor}</p>
                <p className="mt-1 text-sm text-muted">Required quantity: {item.quantity}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="rounded-3xl border border-border bg-surface/70 p-6" aria-labelledby="production-readiness-heading">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-foreground" id="production-readiness-heading">Production start readiness</h2>
              <p className="mt-2 text-sm text-muted">Inventory availability is checked from the Inventory source of truth. This check does not reserve or consume stock.</p>
            </div>
            {materialAvailability ? (
              <span className="rounded-full border border-border px-3 py-1 text-xs font-semibold text-foreground">
                {materialAvailability.allMaterialsAvailable ? "MATERIALS AVAILABLE" : "ACTION REQUIRED"}
              </span>
            ) : null}
          </div>

          {!materialAvailability?.hasMaterialRequirements ? (
            <p className="mt-5 rounded-2xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">Assign at least one required Inventory material before starting production.</p>
          ) : materialAvailability.allMaterialsAvailable ? (
            <p className="mt-5 rounded-2xl border border-success-border bg-success-soft p-4 text-sm text-success">All required material quantities are currently available.</p>
          ) : (
            <div className="mt-5 space-y-3" aria-label="Material shortages">
              <p className="text-sm font-semibold text-danger">Production cannot start until these shortages are resolved:</p>
              {materialAvailability.materials.filter((item) => item.availabilityState !== "AVAILABLE").map((item) => (
                <article className="motion-record rounded-2xl border border-danger-border bg-danger-soft p-4" key={item.inventoryMaterialId}>
                  <p className="font-semibold text-foreground">{item.materialCode} · {item.materialName}</p>
                  <p className="mt-2 text-sm text-danger">Required: {item.requiredQuantity} {item.unitOfMeasure} · Available now: {item.currentQuantity} {item.unitOfMeasure}</p>
                  <p className="mt-1 text-xs font-semibold uppercase tracking-wide text-danger">{item.availabilityState.replaceAll("_", " ")}</p>
                </article>
              ))}
            </div>
          )}

          {detail.task.status === "PENDING" ? (
            <button
              className="mt-5 rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50"
              disabled={isStarting || !materialAvailability?.canStart}
              onClick={() => void startTask()}
              type="button"
            >
              {isStarting ? "Starting production…" : "Start production"}
            </button>
          ) : detail.task.status === "IN_PROGRESS" ? (
            <div className="mt-5">
              <p className="text-sm text-foreground-muted">Production status: <strong>IN PROGRESS</strong>{detail.task.startedAt ? ` · Started ${new Date(detail.task.startedAt).toLocaleString()}` : ""}</p>
              {!materialUsage?.usageRecorded ? (
                <p className="mt-4 rounded-2xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">Record the complete approved material usage before marking production completed.</p>
              ) : detail.task.qualityControlResult !== "PASSED" ? (
                <p className="mt-4 rounded-2xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">Record a PASSED quality-control result before marking production completed.</p>
              ) : (
                <button className="mt-4 rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50" disabled={isCompleting} onClick={() => void completeTask()} type="button">{isCompleting ? "Completing production…" : "Mark production completed"}</button>
              )}
            </div>
          ) : (
            <p className="mt-5 text-sm text-foreground-muted">Production status: <strong>COMPLETED</strong>{detail.task.completedAt ? ` · Completed ${new Date(detail.task.completedAt).toLocaleString()}` : ""}</p>
          )}
          {startSuccessMessage ? <p className="mt-4 text-sm text-success" role="status">{startSuccessMessage}</p> : null}
          {startError ? <p className="mt-4 text-sm text-danger" role="alert">{startError}</p> : null}
          {progressSuccessMessage ? <p className="mt-4 text-sm text-success" role="status">{progressSuccessMessage}</p> : null}
          {progressError ? <p className="mt-4 text-sm text-danger" role="alert">{progressError}</p> : null}
        </section>

        <section className="rounded-3xl border border-border bg-surface/70 p-6" aria-labelledby="production-usage-heading">
          <h2 className="text-xl font-bold text-foreground" id="production-usage-heading">Production material usage</h2>
          <p className="mt-2 text-sm text-muted">Usage deducts the complete approved material-requirement quantities through Inventory Management exactly once. Retrying this action does not deduct stock twice.</p>
          {materialUsage?.usageRecorded ? (
            <div className="mt-5 space-y-3" aria-label="Recorded production material usage">
              <p className="rounded-2xl border border-success-border bg-success-soft p-4 text-sm text-success">Material usage has been recorded for this production task.</p>
              {materialUsage.materials.map((item) => (
                <article className="rounded-2xl border border-border bg-background/50 p-4" key={item.id}>
                  <p className="font-semibold text-foreground">{item.materialCode} · {item.materialName}</p>
                  <p className="mt-2 text-sm text-foreground-muted">Used: {item.quantityUsed} {item.unitOfMeasure} · Inventory remaining: {item.remainingQuantity} {item.unitOfMeasure}</p>
                  <p className="mt-1 text-xs text-muted">Inventory ID #{item.inventoryMaterialId} · Recorded by user #{item.recordedByUserId} · {new Date(item.recordedAt).toLocaleString()}</p>
                </article>
              ))}
            </div>
          ) : detail.task.status === "IN_PROGRESS" ? (
            <div className="mt-5">
              <p className="text-sm text-foreground-muted">This will consume exactly the saved required quantities for every assigned material. The operation is transactional and idempotent.</p>
              <button className="mt-4 rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50" disabled={isRecordingUsage || detail.materialRequirements.length === 0} onClick={() => void recordMaterialUsage()} type="button">{isRecordingUsage ? "Recording usage…" : "Record material usage"}</button>
            </div>
          ) : (
            <p className="mt-5 rounded-2xl border border-border p-4 text-sm text-muted">Start production before recording material usage.</p>
          )}
          {usageSuccessMessage ? <p className="mt-4 text-sm text-success" role="status">{usageSuccessMessage}</p> : null}
          {usageError ? <p className="mt-4 text-sm text-danger" role="alert">{usageError}</p> : null}
        </section>

        <section className="rounded-3xl border border-border bg-surface/70 p-6" aria-labelledby="production-quality-heading">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-foreground" id="production-quality-heading">Quality control</h2>
              <p className="mt-2 text-sm text-muted">Simple task-level pass/fail quality control. This is not a separate quality-management system.</p>
            </div>
            <span className="rounded-full border border-border px-3 py-1 text-xs font-semibold text-foreground">{detail.task.qualityControlResult}</span>
          </div>
          {detail.task.qualityCheckedAt ? <p className="mt-4 text-sm text-muted">Last checked {new Date(detail.task.qualityCheckedAt).toLocaleString()}{detail.task.qualityCheckedByUserId ? ` by user #${detail.task.qualityCheckedByUserId}` : ""}.</p> : null}
          {detail.task.status === "IN_PROGRESS" && materialUsage?.usageRecorded ? (
            <div className="mt-5 flex flex-wrap gap-3">
              <button className="rounded-xl bg-success px-5 py-3 font-semibold text-primary-foreground disabled:opacity-50" disabled={isSavingQuality} onClick={() => void recordQualityControl("PASSED")} type="button">Mark QC passed</button>
              <button className="rounded-xl border border-danger-border px-5 py-3 font-semibold text-danger disabled:opacity-50" disabled={isSavingQuality} onClick={() => void recordQualityControl("FAILED")} type="button">Mark QC failed</button>
            </div>
          ) : detail.task.status === "COMPLETED" ? (
            detail.task.qualityControlResult === "PASSED" ? (
              <p className="mt-5 rounded-2xl border border-success-border bg-success-soft p-4 text-sm text-success">Quality control passed before completion. Order/Delivery readiness is determined by the Order handoff after all sibling Production tasks complete.</p>
            ) : (
              <p className="mt-5 rounded-2xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">This completed record predates the current QC gate or has no recorded QC result. No new completion can bypass QC.</p>
            )
          ) : (
            <p className="mt-5 rounded-2xl border border-border p-4 text-sm text-muted">Record approved material usage before quality control.</p>
          )}
          {qualitySuccessMessage ? <p className="mt-4 text-sm text-success" role="status">{qualitySuccessMessage}</p> : null}
          {qualityError ? <p className="mt-4 text-sm text-danger" role="alert">{qualityError}</p> : null}
        </section>

        <section className="rounded-3xl border border-border bg-surface/70 p-6">
          <h2 className="text-xl font-bold text-foreground">Required materials</h2>
          <p className="mt-2 text-sm text-muted">These requirements reference Inventory IDs. Current stock is informational only; saving requirements does not deduct stock.</p>
          {detail.materialRequirements.length === 0 ? (
            <p className="mt-5 rounded-2xl border border-dashed border-border p-4 text-sm text-muted">No material requirements have been assigned yet.</p>
          ) : (
            <div className="mt-5 space-y-3" aria-label="Saved material requirements">
              {detail.materialRequirements.map((item) => (
                <article className="rounded-2xl border border-border bg-background/50 p-4" key={item.inventoryMaterialId}>
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-foreground">{item.materialCode} · {item.materialName}</p>
                      <p className="mt-1 text-sm text-muted">Inventory ID #{item.inventoryMaterialId} · {item.materialType.replaceAll("_", " ")}</p>
                    </div>
                    <span className="rounded-full border border-border px-3 py-1 text-xs font-semibold text-foreground">{item.availabilityState.replaceAll("_", " ")}</span>
                  </div>
                  <p className="mt-3 text-sm text-foreground-muted">Required: {item.requiredQuantity} {item.unitOfMeasure}</p>
                  <p className="mt-1 text-sm text-muted">Current Inventory stock: {item.currentQuantity} {item.unitOfMeasure}</p>
                </article>
              ))}
            </div>
          )}
        </section>

        {materialSuccessMessage ? <p className="rounded-2xl border border-success-border bg-success-soft p-4 text-success" role="status">{materialSuccessMessage}</p> : null}
        {materialSubmitError ? <p className="text-sm text-danger" role="alert">{materialSubmitError}</p> : null}

        <form className="rounded-3xl border border-border bg-surface/70 p-6" onSubmit={submitMaterials}>
          <h2 className="text-xl font-bold text-foreground">Assign required fabric/raw materials</h2>
          <p className="mt-2 text-sm text-muted">Choose active Inventory records and enter the total quantity required for this production task. Requirements are locked after production starts so recorded usage remains explainable.</p>
          {detail.task.status !== "PENDING" ? <p className="mt-4 rounded-2xl border border-warning-border bg-warning-soft p-4 text-sm text-warning">Material requirements are locked because production has already started.</p> : null}
          <fieldset disabled={detail.task.status !== "PENDING"}>
          {materialOptions.length === 0 ? (
            <p className="mt-5 text-sm text-warning">No active Inventory materials are currently available for assignment.</p>
          ) : null}
          {materialErrors.materials ? <p className="mt-4 text-sm text-danger">{materialErrors.materials}</p> : null}
          <div className="mt-5 space-y-4">
            {materialRows.map((row, index) => {
              const selectedSaved = detail.materialRequirements.find((item) => String(item.inventoryMaterialId) === row.inventoryMaterialId);
              const activeOptionExists = materialOptions.some((item) => String(item.inventoryMaterialId) === row.inventoryMaterialId);
              const idError = materialErrors[`materials[${index}].inventoryMaterialId`];
              const quantityError = materialErrors[`materials[${index}].requiredQuantity`];
              const option = materialOptions.find((item) => String(item.inventoryMaterialId) === row.inventoryMaterialId);
              return (
                <div className="grid gap-4 rounded-2xl border border-border bg-background/40 p-4 md:grid-cols-[1fr_220px_auto]" key={`${index}-${row.inventoryMaterialId}`}>
                  <label className="text-sm font-medium text-foreground">
                    Inventory material {index + 1}
                    <select
                      aria-invalid={idError ? "true" : undefined}
                      className={inputClassName}
                      onChange={(event) => {
                        const next = [...materialRows];
                        next[index] = { ...next[index], inventoryMaterialId: event.target.value };
                        setMaterialRows(next);
                        setMaterialErrors((current) => ({ ...current, [`materials[${index}].inventoryMaterialId`]: "" }));
                      }}
                      value={row.inventoryMaterialId}
                    >
                      <option value="">Select material</option>
                      {!activeOptionExists && selectedSaved ? <option value={selectedSaved.inventoryMaterialId}>{selectedSaved.materialCode} · {selectedSaved.materialName} (not active)</option> : null}
                      {materialOptions.map((item) => <option key={item.inventoryMaterialId} value={item.inventoryMaterialId}>{item.materialCode} · {item.materialName}</option>)}
                    </select>
                    {option ? <span className="mt-2 block text-xs text-muted">Current: {option.currentQuantity} {option.unitOfMeasure} · {option.stockState.replaceAll("_", " ")}</span> : null}
                    {idError ? <span className="mt-2 block text-sm text-danger">{idError}</span> : null}
                  </label>
                  <label className="text-sm font-medium text-foreground">
                    Required quantity {index + 1}
                    <input
                      aria-invalid={quantityError ? "true" : undefined}
                      className={inputClassName}
                      inputMode="decimal"
                      onChange={(event) => {
                        const next = [...materialRows];
                        next[index] = { ...next[index], requiredQuantity: event.target.value };
                        setMaterialRows(next);
                        setMaterialErrors((current) => ({ ...current, [`materials[${index}].requiredQuantity`]: "" }));
                      }}
                      placeholder="0.000"
                      value={row.requiredQuantity}
                    />
                    {quantityError ? <span className="mt-2 block text-sm text-danger">{quantityError}</span> : null}
                  </label>
                  <button className="self-end rounded-xl border border-border px-4 py-3 text-sm font-semibold text-foreground disabled:opacity-40" disabled={materialRows.length === 1} onClick={() => setMaterialRows((rows) => rows.filter((_, rowIndex) => rowIndex !== index))} type="button">Remove</button>
                </div>
              );
            })}
          </div>
          <div className="mt-5 flex flex-wrap gap-3">
            <button className="rounded-xl border border-border px-5 py-3 font-semibold text-foreground" disabled={materialRows.length >= 50} onClick={() => setMaterialRows((rows) => [...rows, blankMaterialRow()])} type="button">Add material</button>
            <button className="rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50" disabled={isSavingMaterials || materialOptions.length === 0} type="submit">{isSavingMaterials ? "Saving materials…" : "Save material requirements"}</button>
          </div>
          </fieldset>
        </form>

        {successMessage ? <p className="rounded-2xl border border-success-border bg-success-soft p-4 text-success" role="status">{successMessage}</p> : null}
        {submitError ? <p className="text-sm text-danger" role="alert">{submitError}</p> : null}

        <form className="rounded-3xl border border-border bg-surface/70 p-6" onSubmit={submit}>
          <h2 className="text-xl font-bold text-foreground">{detail.workDetails ? "Edit work details" : "Add work details"}</h2>
          <p className="mt-2 text-sm text-muted">Work details and assignment are required. Notes are optional.</p>
          <label className="mt-6 block text-sm font-medium text-foreground">Manufacturing work details
            <textarea aria-invalid={fieldErrors.workDetails ? "true" : undefined} className={`${inputClassName} min-h-32`} maxLength={1000} onChange={(event) => { setWorkDetails(event.target.value); setFieldErrors((current) => ({ ...current, workDetails: undefined })); }} placeholder="Example: Cut, stitch and finish the linked order items according to the selected sizes and colors." value={workDetails} />
            {fieldErrors.workDetails ? <span className="mt-2 block text-sm text-danger">{fieldErrors.workDetails}</span> : null}
          </label>
          <label className="mt-5 block text-sm font-medium text-foreground">Work assignment
            <input aria-invalid={fieldErrors.workAssignment ? "true" : undefined} className={inputClassName} maxLength={255} onChange={(event) => { setWorkAssignment(event.target.value); setFieldErrors((current) => ({ ...current, workAssignment: undefined })); }} placeholder="Example: Sewing Line A or Finishing Team 2" value={workAssignment} />
            {fieldErrors.workAssignment ? <span className="mt-2 block text-sm text-danger">{fieldErrors.workAssignment}</span> : null}
          </label>
          <label className="mt-5 block text-sm font-medium text-foreground">Work notes <span className="text-muted">(optional)</span>
            <textarea aria-invalid={fieldErrors.workNotes ? "true" : undefined} className={`${inputClassName} min-h-24`} maxLength={2000} onChange={(event) => { setWorkNotes(event.target.value); setFieldErrors((current) => ({ ...current, workNotes: undefined })); }} placeholder="Add manufacturing notes that do not belong to another module's source of truth." value={workNotes} />
            {fieldErrors.workNotes ? <span className="mt-2 block text-sm text-danger">{fieldErrors.workNotes}</span> : null}
          </label>
          <button className="mt-6 rounded-xl bg-primary px-6 py-3 font-semibold text-primary-foreground transition hover:bg-primary-hover disabled:cursor-not-allowed disabled:opacity-50" disabled={isSubmitting} type="submit">{isSubmitting ? "Saving details…" : "Save production details"}</button>
          {detail.workDetails ? <p className="mt-4 text-xs text-muted">Saved details last updated {new Date(detail.workDetails.updatedAt).toLocaleString()}.</p> : null}
        </form>
      </div>
    </section>
  );
}

import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";

import {
  getDeliveryApiError,
  getDeliveryRecords,
  type DeliveryStatus,
  type StaffDeliveryRecord,
} from "../api/deliveries";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { StatusBadge } from "../components/ui/StatusBadge";
import {
  deliveryOrderSelectionPagePath,
  getDeliveryDetailPagePath,
} from "../navigation/navigation";

const statuses: Array<{ value: "" | DeliveryStatus; label: string }> = [
  { value: "", label: "All statuses" },
  { value: "SCHEDULED", label: "Scheduled" },
  { value: "OUT_FOR_DELIVERY", label: "Out for delivery" },
  { value: "DELIVERED", label: "Delivered" },
  { value: "CANCELLED", label: "Cancelled" },
];

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function DeliveryRecordsPage() {
  const [records, setRecords] = useState<StaffDeliveryRecord[]>([]);
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<"" | DeliveryStatus>("");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setError(null);
    getDeliveryRecords(search, status, controller.signal)
      .then(setRecords)
      .catch((caught: unknown) => {
        if (controller.signal.aborted) return;
        setError(getDeliveryApiError(caught, "Delivery records could not be loaded.").message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, [search, status]);

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setSearch(searchInput.trim());
  }

  return (
    <section className="mx-auto w-full max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="rounded-3xl border border-border bg-surface/70 p-6 app-shadow">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-primary">Delivery Management</p>
            <h1 className="mt-2 text-3xl font-bold text-foreground">Delivery records</h1>
            <p className="mt-2 max-w-3xl text-muted">
              Review scheduled, dispatched, delivered, and cancelled Delivery history. Order and customer details are resolved from Order Management at read time.
            </p>
          </div>
          <Link
            className="rounded-xl bg-primary px-5 py-3 text-center font-semibold text-primary-foreground hover:bg-primary-hover"
            to={deliveryOrderSelectionPagePath}
          >
            Schedule delivery
          </Link>
        </div>

        <form className="mt-7 grid gap-4 md:grid-cols-[1fr_14rem_auto]" onSubmit={submitSearch}>
          <label className="text-sm font-medium text-foreground">
            Search delivery number, order ID, or address
            <input
              className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none focus:border-primary"
              maxLength={120}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder="DLV-…, 1042, Colombo"
              value={searchInput}
            />
          </label>
          <label className="text-sm font-medium text-foreground">
            Status
            <select
              className="mt-2 w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground outline-none focus:border-primary"
              onChange={(event) => setStatus(event.target.value as "" | DeliveryStatus)}
              value={status}
            >
              {statuses.map((option) => <option key={option.value || "ALL"} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <button className="self-end rounded-xl border border-border px-5 py-3 font-semibold text-foreground hover:border-border" type="submit">
            Search
          </button>
        </form>

        <div className="mt-7">
          {isLoading ? <LoadingState message="Loading delivery records…" /> : null}
          {!isLoading && error ? <ErrorState message={error} /> : null}
          {!isLoading && !error && records.length === 0 ? (
            <EmptyState
              title="No delivery records found"
              message="Try a different search or status, or schedule a delivery-ready order."
            />
          ) : null}

          {!isLoading && !error && records.length > 0 ? (
            <div className="overflow-x-auto rounded-2xl border border-border">
              <table className="min-w-full divide-y divide-border text-left text-sm">
                <thead className="bg-background/70 text-muted">
                  <tr>
                    <th className="px-4 py-3 font-medium">Delivery</th>
                    <th className="px-4 py-3 font-medium">Order / customer</th>
                    <th className="px-4 py-3 font-medium">Scheduled</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface/40">
                  {records.map((record) => (
                    <tr key={record.delivery.id}>
                      <td className="px-4 py-4">
                        <p className="font-semibold text-foreground">{record.delivery.deliveryNumber}</p>
                        <p className="mt-1 text-muted">ID #{record.delivery.id}</p>
                      </td>
                      <td className="px-4 py-4">
                        <p className="font-medium text-foreground">{record.orderNumber}</p>
                        <p className="mt-1 text-muted">{record.customerName}</p>
                        <p className="text-muted">{record.customerEmail}</p>
                      </td>
                      <td className="px-4 py-4 text-foreground-muted">{formatDateTime(record.delivery.scheduledAt)}</td>
                      <td className="px-4 py-4">
                        <StatusBadge label={record.delivery.status.replaceAll("_", " ")} status={record.delivery.status} />
                      </td>
                      <td className="px-4 py-4">
                        <Link className="font-semibold text-primary hover:text-primary" to={getDeliveryDetailPagePath(record.delivery.id)}>
                          View details
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </div>
      </div>
    </section>
  );
}

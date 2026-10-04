import { ArrowUpRight, BellRing, RotateCcw } from "lucide-react";
import { useEffect, useState, type CSSProperties } from "react";

import {
  getNotificationApiError,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type OperationalNotification,
} from "../api/notifications";
import { EmptyState, ErrorState, LoadingState } from "../components/AppStates";
import { MotionSwap } from "../motion/MotionSwap";
import { motionDurations } from "../motion/motionTokens";
import { usePresence } from "../motion/usePresence";

const kindLabels: Record<OperationalNotification["kind"], string> = {
  LOW_STOCK: "Inventory alert",
  ORDER_STATUS: "Order update",
  PRODUCTION_STATUS: "Production update",
  DELIVERY_STATUS: "Delivery update",
};

function formatTimestamp(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "Time unavailable";
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(parsed);
}

interface NotificationCardProps {
  busyNotificationId: number | null;
  notification: OperationalNotification;
  isRemoving?: boolean;
  onMarkRead: (notification: OperationalNotification) => void;
  onRemoved?: () => void;
  stackIndex?: number;
  stackSize?: number;
}

function NotificationCard({
  busyNotificationId,
  isRemoving = false,
  notification,
  onMarkRead,
  onRemoved,
  stackIndex,
  stackSize = 0,
}: NotificationCardProps) {
  const presence = usePresence(!isRemoving, {
    exitDuration: motionDurations.smooth,
    onExitComplete: onRemoved,
  });
  const isStacked = stackIndex !== undefined;
  const stackStyle = isStacked
    ? ({
        "--notification-stack-offset": `${Math.min(stackIndex, 2) * 4}px`,
        "--notification-stack-scale": Math.max(0.94, 1 - stackIndex * 0.025),
        zIndex: stackSize - stackIndex,
      } as CSSProperties)
    : undefined;

  if (!presence.isMounted) return null;

  return (
    <li
      className="motion-removable"
      data-motion-state={presence.motionState}
      inert={presence.motionState === "closed"}
      onTransitionEnd={presence.onTransitionEnd}
    >
      <div
        className={[
          "motion-removable-inner motion-record rounded-2xl border p-5 shadow-sm transition-colors duration-[var(--motion-normal)] ease-[var(--ease-standard)] sm:p-6",
          isStacked ? "notification-stack-card" : "",
          notification.unread
            ? "border-primary/30 bg-primary-soft"
            : "border-border bg-surface-elevated",
        ].join(" ")}
        style={stackStyle}
      >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-background px-3 py-1 text-xs font-semibold text-foreground-muted">
              {kindLabels[notification.kind]}
            </span>
            <span
              className={[
                "motion-status-badge rounded-full px-2.5 py-1 text-xs",
                notification.unread
                  ? "bg-primary font-bold text-primary-foreground"
                  : "bg-surface-muted font-medium text-muted",
              ].join(" ")}
            >
              {notification.unread ? "Unread" : "Read"}
            </span>
          </div>
          <h2 className="mt-3 text-lg font-semibold text-foreground">{notification.title}</h2>
          <p className="mt-2 text-sm leading-6 text-foreground-muted">{notification.message}</p>
          <p className="mt-3 text-xs text-muted">
            {formatTimestamp(notification.createdAt)} · {notification.sourceModule.toLowerCase()} record #{notification.sourceRecordId}
          </p>
        </div>
        {notification.unread ? (
          <button
            className="shrink-0 rounded-full border border-border px-4 py-2 text-sm font-semibold text-foreground transition hover:border-primary hover:text-foreground disabled:cursor-wait disabled:opacity-50"
            disabled={busyNotificationId !== null}
            onClick={() => onMarkRead(notification)}
            type="button"
          >
            {busyNotificationId === notification.id ? "Updating…" : "Mark read"}
          </button>
        ) : null}
      </div>
      </div>
    </li>
  );
}

function NotificationStack({
  busyNotificationId,
  notifications,
  onMarkRead,
  onRemoved,
  removingNotificationIds,
  unreadCount,
}: {
  busyNotificationId: number | null;
  notifications: OperationalNotification[];
  onMarkRead: (notification: OperationalNotification) => void;
  onRemoved: (notificationId: number) => void;
  removingNotificationIds: Set<number>;
  unreadCount: number;
}) {
  return (
    <section aria-labelledby="latest-notifications" className="notification-stack-preview">
      <div className="mb-4 flex items-center justify-between gap-3 px-1">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-primary">Live operational feed</p>
          <h2 className="mt-1 text-lg font-bold text-foreground" id="latest-notifications">Latest notifications</h2>
        </div>
        <BellRing aria-hidden="true" className="h-5 w-5 text-primary" />
      </div>
      <ol className="notification-stack-list">
        {notifications.map((notification, index) => (
          <NotificationCard
            busyNotificationId={busyNotificationId}
            isRemoving={removingNotificationIds.has(notification.id)}
            key={notification.id}
            notification={notification}
            onMarkRead={onMarkRead}
            onRemoved={() => onRemoved(notification.id)}
            stackIndex={index}
            stackSize={notifications.length}
          />
        ))}
      </ol>
      <div className="mt-4 flex items-center gap-2 px-1">
        <span className="grid h-6 min-w-6 place-items-center rounded-full bg-primary px-1.5 text-xs font-bold text-primary-foreground">{unreadCount}</span>
        <span className="notification-stack-footer-text">
          <span className="notification-stack-summary"><RotateCcw aria-hidden="true" className="h-3.5 w-3.5" />Unread notifications</span>
          <a className="notification-stack-view-all" href="#notification-inbox">View inbox <ArrowUpRight aria-hidden="true" className="h-4 w-4" /></a>
        </span>
      </div>
    </section>
  );
}

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<OperationalNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [busyNotificationId, setBusyNotificationId] = useState<number | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);
  const [removingNotificationIds, setRemovingNotificationIds] = useState<Set<number>>(() => new Set());
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setLoadError(null);
    setMutationError(null);
    setSuccessMessage(null);

    void getNotifications(unreadOnly, controller.signal)
      .then((inbox) => {
        setNotifications(inbox.notifications);
        setUnreadCount(inbox.unreadCount);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setLoadError(
            getNotificationApiError(
              error,
              "Notifications could not be loaded. Please try again.",
            ).message,
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
          setHasLoaded(true);
        }
      });

    return () => controller.abort();
  }, [reloadKey, unreadOnly]);

  async function handleMarkRead(notification: OperationalNotification) {
    if (!notification.unread || busyNotificationId !== null) {
      return;
    }
    setBusyNotificationId(notification.id);
    setMutationError(null);
    setSuccessMessage(null);
    try {
      await markNotificationRead(notification.id);
      setUnreadCount((count) => Math.max(0, count - 1));
      if (unreadOnly) {
        setRemovingNotificationIds((current) => new Set(current).add(notification.id));
      } else {
        setNotifications((current) =>
          current.map((item) =>
            item.id === notification.id
              ? { ...item, unread: false, readAt: new Date().toISOString() }
              : item,
          ),
        );
      }
      setSuccessMessage("Notification marked as read.");
    } catch (error: unknown) {
      setMutationError(
        getNotificationApiError(
          error,
          "The notification could not be updated. Please try again.",
        ).message,
      );
    } finally {
      setBusyNotificationId(null);
    }
  }

  async function handleMarkAllRead() {
    if (unreadCount === 0 || isMarkingAll) {
      return;
    }
    setIsMarkingAll(true);
    setMutationError(null);
    setSuccessMessage(null);
    try {
      const response = await markAllNotificationsRead();
      setUnreadCount(0);
      if (unreadOnly) {
        setRemovingNotificationIds(new Set(notifications.map((notification) => notification.id)));
      } else {
        const now = new Date().toISOString();
        setNotifications((current) =>
          current.map((item) => ({ ...item, unread: false, readAt: item.readAt ?? now })),
        );
      }
      setSuccessMessage(response.message);
    } catch (error: unknown) {
      setMutationError(
        getNotificationApiError(
          error,
          "Notifications could not be updated. Please try again.",
        ).message,
      );
    } finally {
      setIsMarkingAll(false);
    }
  }

  function finishNotificationRemoval(notificationId: number) {
    setNotifications((current) => current.filter((item) => item.id !== notificationId));
    setRemovingNotificationIds((current) => {
      const next = new Set(current);
      next.delete(notificationId);
      return next;
    });
  }

  return (
    <div aria-busy={isLoading} className="mx-auto max-w-5xl p-6 sm:p-10 lg:p-12">
      <MotionSwap stateKey={!hasLoaded ? "loading" : loadError ? "error" : "content"}>
        {!hasLoaded ? (
          <LoadingState
            message="Loading operational alerts and status updates permitted for your account."
            title="Loading notifications…"
          />
        ) : loadError ? (
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
            message={loadError}
            title="Notifications unavailable"
          />
        ) : (
          <div>
            {isLoading ? <p className="mb-4 text-sm text-muted" role="status">Updating notifications…</p> : null}
            <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-primary">
            Operational awareness
          </p>
          <h1 className="mt-3 text-3xl font-bold tracking-tight text-foreground">Notifications</h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-muted">
            These supporting alerts are generated from stored Inventory, Order, Production, and
            Delivery events. The underlying modules remain the source of truth.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <span className="rounded-full border border-border bg-surface px-4 py-2 text-sm text-foreground-muted">
            {unreadCount} unread
          </span>
          <button
            className="rounded-full border border-primary/40 px-4 py-2 text-sm font-semibold text-primary transition hover:bg-primary-hover/10 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={unreadCount === 0 || isMarkingAll}
            onClick={() => void handleMarkAllRead()}
            type="button"
          >
            {isMarkingAll ? "Updating…" : "Mark all read"}
          </button>
        </div>
      </div>

      <div className="mt-7 flex items-center gap-3 rounded-2xl border border-border bg-surface/60 p-4">
        <input
          checked={unreadOnly}
          className="h-4 w-4 accent-primary"
          id="unread-only"
          onChange={(event) => setUnreadOnly(event.target.checked)}
          type="checkbox"
        />
        <label className="text-sm text-foreground-muted" htmlFor="unread-only">
          Show unread notifications only
        </label>
      </div>

      {mutationError ? (
        <p className="mt-5 rounded-2xl border border-danger-border bg-danger-soft p-4 text-sm text-danger" role="alert">
          {mutationError}
        </p>
      ) : null}
      {successMessage ? (
        <p className="mt-5 rounded-2xl border border-success-border bg-success-soft p-4 text-sm text-success" role="status">
          {successMessage}
        </p>
      ) : null}

      <div className="mt-6" id="notification-inbox">
        <MotionSwap stateKey={notifications.length === 0 ? "empty" : "populated"}>
        {notifications.length === 0 ? (
          <EmptyState
            message={
              unreadOnly
                ? "You have no unread operational notifications."
                : "No operational notifications have been recorded for your account yet."
            }
            title={unreadOnly ? "All caught up" : "No notifications yet"}
          />
        ) : (
          <div>
            <NotificationStack
              busyNotificationId={busyNotificationId}
              notifications={notifications.slice(0, 3)}
              onMarkRead={(notification) => void handleMarkRead(notification)}
              onRemoved={finishNotificationRemoval}
              removingNotificationIds={removingNotificationIds}
              unreadCount={unreadCount}
            />
            {notifications.length > 3 ? (
              <ol aria-label="Earlier notifications" className="mt-8 grid gap-4">
                {notifications.slice(3).map((notification) => (
                  <NotificationCard
                    busyNotificationId={busyNotificationId}
                    isRemoving={removingNotificationIds.has(notification.id)}
                    key={notification.id}
                    notification={notification}
                    onMarkRead={(item) => void handleMarkRead(item)}
                    onRemoved={() => finishNotificationRemoval(notification.id)}
                  />
                ))}
              </ol>
            ) : null}
          </div>
        )}
        </MotionSwap>
      </div>
          </div>
        )}
      </MotionSwap>
    </div>
  );
}

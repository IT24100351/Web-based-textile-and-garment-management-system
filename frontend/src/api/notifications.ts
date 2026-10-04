import axios from "axios";

import { api } from "./client";

export const NOTIFICATIONS_CHANGED_EVENT = "tgms:notifications-changed";

function announceNotificationsChanged() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
  }
}

export type NotificationKind =
  | "LOW_STOCK"
  | "ORDER_STATUS"
  | "PRODUCTION_STATUS"
  | "DELIVERY_STATUS";

export type NotificationSourceModule =
  | "INVENTORY"
  | "ORDER"
  | "PRODUCTION"
  | "DELIVERY";

export interface OperationalNotification {
  id: number;
  kind: NotificationKind;
  title: string;
  message: string;
  sourceModule: NotificationSourceModule;
  sourceRecordId: number;
  unread: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationInbox {
  notifications: OperationalNotification[];
  unreadCount: number;
}

interface NotificationApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface NotificationApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
}

export async function getNotifications(
  unreadOnly: boolean,
  signal?: AbortSignal,
): Promise<NotificationInbox> {
  const response = await api.get<NotificationInbox>("/notifications", {
    params: { unreadOnly, limit: 100 },
    signal,
  });
  return response.data;
}

export async function markNotificationRead(notificationId: number) {
  const response = await api.patch<{ message: string }>(
    `/notifications/${notificationId}/read`,
  );
  announceNotificationsChanged();
  return response.data;
}

export async function markAllNotificationsRead() {
  const response = await api.patch<{ message: string }>("/notifications/read-all");
  announceNotificationsChanged();
  return response.data;
}

export function getNotificationApiError(
  error: unknown,
  fallback: string,
): NotificationApiError {
  if (!axios.isAxiosError<NotificationApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }
  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
  };
}

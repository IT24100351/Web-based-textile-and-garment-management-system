import axios from "axios";

import { api } from "./client";
import type { OrderStatus } from "./orders";

export interface DeliveryEligibleOrderItem {
  orderItemId: number;
  productId: number;
  variantId: number;
  quantity: number;
  selectedSize: string;
  selectedColor: string;
}

export interface DeliveryEligibleOrder {
  orderId: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  currentStatus: OrderStatus;
  itemCount: number;
  totalAmount: string;
  readyForDelivery: boolean;
  items: DeliveryEligibleOrderItem[];
}

interface DeliveryApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface DeliveryApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function getDeliveryEligibleOrders(
  search?: string,
  signal?: AbortSignal,
): Promise<DeliveryEligibleOrder[]> {
  const normalized = search?.trim();
  const response = await api.get<DeliveryEligibleOrder[]>("/deliveries/eligible-orders", {
    params: normalized ? { search: normalized } : undefined,
    signal,
  });
  return response.data;
}

export async function validateDeliveryOrder(
  orderId: number,
  signal?: AbortSignal,
): Promise<DeliveryEligibleOrder> {
  const response = await api.get<DeliveryEligibleOrder>(
    `/deliveries/eligible-orders/${orderId}`,
    { signal },
  );
  return response.data;
}

export function getDeliveryApiError(
  error: unknown,
  fallback: string,
): DeliveryApiError {
  if (!axios.isAxiosError<DeliveryApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

export type DeliveryStatus = "SCHEDULED" | "OUT_FOR_DELIVERY" | "DELIVERED" | "CANCELLED";

export interface CustomerDeliveryProgress {
  deliveryId: number;
  deliveryNumber: string;
  scheduledAt: string;
  status: DeliveryStatus;
  lastUpdatedAt: string;
}

export interface CustomerDeliveryTracking {
  orderId: number;
  hasDelivery: boolean;
  delivery: CustomerDeliveryProgress | null;
}

export async function getMyDeliveryTracking(
  orderId: number,
  signal?: AbortSignal,
): Promise<CustomerDeliveryTracking> {
  const response = await api.get<CustomerDeliveryTracking>(
    `/deliveries/mine/orders/${orderId}/tracking`,
    { signal },
  );
  return response.data;
}

export interface DeliveryRecord {
  id: number;
  deliveryNumber: string;
  orderId: number;
  scheduledAt: string;
  deliveryAddress: string;
  deliveryNotes: string | null;
  status: DeliveryStatus;
  allowedStatusTransitions: DeliveryStatus[];
  createdAt: string;
  updatedAt: string;
}


export interface StaffDeliveryOrderItem {
  orderItemId: number;
  productId: number;
  variantId: number;
  productName: string;
  quantity: number;
  selectedSize: string;
  selectedColor: string;
  unitPriceSnapshot: string;
  lineTotal: string;
}

export interface StaffDeliveryRecord {
  delivery: DeliveryRecord;
  orderNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  orderStatus: OrderStatus;
  orderTotal: string;
  items: StaffDeliveryOrderItem[];
}

export async function getDeliveryRecords(
  search?: string,
  status?: DeliveryStatus | "",
  signal?: AbortSignal,
): Promise<StaffDeliveryRecord[]> {
  const normalizedSearch = search?.trim();
  const response = await api.get<StaffDeliveryRecord[]>("/deliveries", {
    params: {
      ...(normalizedSearch ? { search: normalizedSearch } : {}),
      ...(status ? { status } : {}),
    },
    signal,
  });
  return response.data;
}

export async function getDeliveryRecord(
  deliveryId: number,
  signal?: AbortSignal,
): Promise<StaffDeliveryRecord> {
  const response = await api.get<StaffDeliveryRecord>(`/deliveries/${deliveryId}`, { signal });
  return response.data;
}

export interface ScheduleDeliveryInput {
  orderId: number;
  scheduledAt: string;
  deliveryAddress: string;
  deliveryNotes?: string | null;
}

export interface ScheduleDeliveryResponse {
  message: string;
  delivery: DeliveryRecord;
}

export async function scheduleDelivery(
  input: ScheduleDeliveryInput,
): Promise<ScheduleDeliveryResponse> {
  const response = await api.post<ScheduleDeliveryResponse>("/deliveries", input);
  return response.data;
}

export interface UpdateDeliveryStatusResponse {
  message: string;
  delivery: DeliveryRecord;
}

export async function updateDeliveryStatus(
  deliveryId: number,
  status: DeliveryStatus,
): Promise<UpdateDeliveryStatusResponse> {
  const response = await api.patch<UpdateDeliveryStatusResponse>(
    `/deliveries/${deliveryId}/status`,
    { status },
  );
  return response.data;
}

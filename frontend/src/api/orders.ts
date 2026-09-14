import axios from "axios";

import { api } from "./client";

export interface OrderCustomerOption {
  id: number;
  fullName: string;
  email: string;
}

export interface CreateOrderItemInput {
  productId: number;
  variantId: number;
  quantity: number;
}

export interface CreateOrderInput {
  customerId: number;
  items: CreateOrderItemInput[];
}

export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "IN_PRODUCTION"
  | "READY_FOR_DELIVERY"
  | "COMPLETED"
  | "CANCELLED";

export interface CreatedOrderItem {
  id: number;
  productId: number;
  variantId: number;
  productName: string;
  quantity: number;
  selectedSize: string;
  selectedColor: string;
  unitPriceSnapshot: string;
  lineTotal: string;
}

export interface CreateOrderResponse {
  message: string;
  id: number;
  orderNumber: string;
  customerId: number;
  customer: OrderCustomerOption;
  status: OrderStatus;
  createdAt: string;
  totalAmount: string;
  items: CreatedOrderItem[];
}

export interface OrderSummary {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
  itemCount: number;
  totalAmount: string;
}

export interface OrderDetailItem {
  id: number;
  productId: number;
  variantId: number;
  productName: string;
  quantity: number;
  selectedSize: string;
  selectedColor: string;
  unitPriceSnapshot: string;
  lineTotal: string;
}

export interface OrderStatusHistoryEntry {
  id: number;
  fromStatus: OrderStatus;
  toStatus: OrderStatus;
  changedAt: string;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
  items: OrderDetailItem[];
  totalAmount: string;
  allowedStatusTransitions: OrderStatus[];
  statusHistory: OrderStatusHistoryEntry[];
}

export interface CustomerOrderTracking {
  orderId: number;
  orderNumber: string;
  currentStatus: OrderStatus;
  placedAt: string;
  lastUpdatedAt: string;
  itemCount: number;
  totalAmount: string;
  orderHistory: OrderStatusHistoryEntry[];
}

export interface OrderStatusUpdateResponse {
  message: string;
  order: OrderDetail;
}

export type OrderPaymentStatus = "UNPAID" | "PARTIALLY_PAID" | "PAID";
export type OrderPaymentMethod = "CASH" | "BANK_TRANSFER" | "OTHER";

export interface OrderInvoice {
  id: number;
  orderId: number;
  invoiceNumber: string;
  totalAmount: string;
  issuedAt: string;
}

export interface OrderPaymentRecord {
  id: number;
  paymentStatus: OrderPaymentStatus;
  amountPaid: string;
  paymentMethod: OrderPaymentMethod | null;
  paymentReference: string | null;
  note: string | null;
  recordedAt: string;
  updatedAt: string;
}

export interface OrderBilling {
  invoiceGenerated: boolean;
  invoice: OrderInvoice | null;
  payment: OrderPaymentRecord | null;
}

export interface OrderBillingMutationResponse {
  message: string;
  billing: OrderBilling;
}

export interface OrderPaymentUpdateInput {
  paymentStatus: OrderPaymentStatus;
  amountPaid: string;
  paymentMethod?: OrderPaymentMethod | "";
  paymentReference?: string;
  note?: string;
}

export interface OrderHandoffItem {
  orderItemId: number;
  productId: number;
  variantId: number;
  quantity: number;
  selectedSize: string;
  selectedColor: string;
}

export interface OrderHandoff {
  orderId: number;
  orderNumber: string;
  customerId: number;
  currentStatus: OrderStatus;
  readyForProduction: boolean;
  readyForDelivery: boolean;
  items: OrderHandoffItem[];
}

export interface OrderListFilters {
  search?: string;
  status?: OrderStatus | "";
}

interface OrderApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface OrderApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function getOrderCustomers(
  search?: string,
  signal?: AbortSignal,
): Promise<OrderCustomerOption[]> {
  const response = await api.get<OrderCustomerOption[]>("/orders/customers", {
    params: search?.trim() ? { search: search.trim() } : undefined,
    signal,
  });
  return response.data;
}

export async function createOrder(input: CreateOrderInput): Promise<CreateOrderResponse> {
  const response = await api.post<CreateOrderResponse>("/orders", input);
  return response.data;
}

export async function createMyOrder(
  items: CreateOrderItemInput[],
): Promise<CreateOrderResponse> {
  const response = await api.post<CreateOrderResponse>("/orders/mine", { items });
  return response.data;
}

export async function getOrders(
  filters: OrderListFilters = {},
  signal?: AbortSignal,
): Promise<OrderSummary[]> {
  const params: Record<string, string> = {};
  if (filters.search?.trim()) {
    params.search = filters.search.trim();
  }
  if (filters.status) {
    params.status = filters.status;
  }
  const response = await api.get<OrderSummary[]>("/orders", {
    params: Object.keys(params).length > 0 ? params : undefined,
    signal,
  });
  return response.data;
}

export async function getOrderDetail(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderDetail> {
  const response = await api.get<OrderDetail>(`/orders/${orderId}`, { signal });
  return response.data;
}

export async function getMyOrders(signal?: AbortSignal): Promise<OrderSummary[]> {
  const response = await api.get<OrderSummary[]>("/orders/mine", { signal });
  return response.data;
}

export async function getMyOrderDetail(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderDetail> {
  const response = await api.get<OrderDetail>(`/orders/mine/${orderId}`, { signal });
  return response.data;
}

export async function getMyOrderTracking(
  orderId: number,
  signal?: AbortSignal,
): Promise<CustomerOrderTracking> {
  const response = await api.get<CustomerOrderTracking>(
    `/orders/mine/${orderId}/tracking`,
    { signal },
  );
  return response.data;
}

export async function updateOrderStatus(
  orderId: number,
  status: OrderStatus,
): Promise<OrderStatusUpdateResponse> {
  const response = await api.patch<OrderStatusUpdateResponse>(`/orders/${orderId}/status`, {
    status,
  });
  return response.data;
}

export async function getOrderBilling(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderBilling> {
  const response = await api.get<OrderBilling>(`/orders/${orderId}/billing`, { signal });
  return response.data;
}

export async function getMyOrderBilling(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderBilling> {
  const response = await api.get<OrderBilling>(`/orders/mine/${orderId}/billing`, { signal });
  return response.data;
}

export async function generateOrderInvoice(orderId: number): Promise<OrderBillingMutationResponse> {
  const response = await api.post<OrderBillingMutationResponse>(`/orders/${orderId}/invoice`);
  return response.data;
}

export async function updateOrderPayment(
  orderId: number,
  input: OrderPaymentUpdateInput,
): Promise<OrderBillingMutationResponse> {
  const response = await api.patch<OrderBillingMutationResponse>(`/orders/${orderId}/payment`, input);
  return response.data;
}

export async function getOrderHandoff(
  orderId: number,
  signal?: AbortSignal,
): Promise<OrderHandoff> {
  const response = await api.get<OrderHandoff>(`/orders/${orderId}/handoff`, { signal });
  return response.data;
}

export function getOrderApiError(error: unknown, fallback: string): OrderApiError {
  if (!axios.isAxiosError<OrderApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

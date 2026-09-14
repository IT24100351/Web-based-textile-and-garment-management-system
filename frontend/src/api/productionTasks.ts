import axios from "axios";

import { api } from "./client";
import type { OrderHandoffItem, OrderStatus } from "./orders";

export type ProductionTaskStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED";
export type ProductionQualityControlResult = "PENDING" | "PASSED" | "FAILED";
export type ProductionTaskRecordView = "ACTIVE" | "COMPLETED" | "ALL";

export type InventoryMaterialType = "FABRIC" | "RAW_MATERIAL";
export type InventoryMaterialStatus = "ACTIVE" | "INACTIVE" | "DISCONTINUED";
export type InventoryAvailabilityState = "AVAILABLE" | "INSUFFICIENT_STOCK" | "NOT_ACTIVE";
export type InventoryStockState = "LOW_STOCK" | "SUFFICIENT";

export interface ProductionMaterialOption {
  inventoryMaterialId: number;
  materialCode: string;
  materialName: string;
  materialType: InventoryMaterialType;
  unitOfMeasure: string;
  currentQuantity: string;
  status: InventoryMaterialStatus;
  stockState: InventoryStockState;
}

export interface ProductionTaskMaterialRequirement {
  inventoryMaterialId: number;
  materialCode: string;
  materialName: string;
  materialType: InventoryMaterialType;
  unitOfMeasure: string;
  requiredQuantity: string;
  currentQuantity: string;
  inventoryStatus: InventoryMaterialStatus;
  availabilityState: InventoryAvailabilityState;
  createdAt: string;
  updatedAt: string;
}

export interface ProductionEligibleOrder {
  orderId: number;
  orderNumber: string;
  customerId: number;
  currentStatus: OrderStatus;
  readyForProduction: boolean;
  items: OrderHandoffItem[];
}

export interface ProductionTask {
  id: number;
  taskNumber: string;
  orderId: number;
  status: ProductionTaskStatus;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  qualityControlResult: ProductionQualityControlResult;
  qualityCheckedByUserId: number | null;
  qualityCheckedAt: string | null;
}

export interface ProductionTaskRecordSummary {
  task: ProductionTask;
  orderNumber: string;
  customerId: number;
  orderStatus: OrderStatus;
  orderItemCount: number;
  readyForDelivery: boolean;
}

export interface ProductionTaskWorkDetails {
  workDetails: string;
  workAssignment: string;
  workNotes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductionTaskDetail {
  task: ProductionTask;
  order: ProductionEligibleOrder;
  workDetails: ProductionTaskWorkDetails | null;
  materialRequirements: ProductionTaskMaterialRequirement[];
}

export interface ProductionTaskMaterialAvailabilityReport {
  taskId: number;
  taskNumber: string;
  taskStatus: ProductionTaskStatus;
  hasMaterialRequirements: boolean;
  allMaterialsAvailable: boolean;
  canStart: boolean;
  materials: ProductionTaskMaterialRequirement[];
}

export interface ProductionTaskMaterialUsageItem {
  id: number;
  inventoryMaterialId: number;
  materialCode: string;
  materialName: string;
  unitOfMeasure: string;
  quantityUsed: string;
  remainingQuantity: string;
  recordedByUserId: number;
  recordedAt: string;
}

export interface ProductionTaskMaterialUsageReport {
  taskId: number;
  taskNumber: string;
  taskStatus: ProductionTaskStatus;
  usageRecorded: boolean;
  materials: ProductionTaskMaterialUsageItem[];
}

export interface CreateProductionTaskResponse {
  message: string;
  task: ProductionTask;
}

export interface UpdateProductionTaskDetailsResponse {
  message: string;
  task: ProductionTaskDetail;
}

export interface UpdateProductionTaskMaterialsResponse {
  message: string;
  task: ProductionTaskDetail;
}

export interface StartProductionTaskResponse {
  message: string;
  task: ProductionTaskDetail;
}

export interface UpdateProductionTaskStatusResponse {
  message: string;
  task: ProductionTaskDetail;
}

export interface RecordProductionTaskMaterialUsageResponse {
  message: string;
  usage: ProductionTaskMaterialUsageReport;
}

interface ProductionTaskApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface ProductionTaskApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}


export async function getProductionTaskRecords(
  view: ProductionTaskRecordView = "ACTIVE",
  signal?: AbortSignal,
): Promise<ProductionTaskRecordSummary[]> {
  const response = await api.get<ProductionTaskRecordSummary[]>("/production/tasks", {
    params: { view },
    signal,
  });
  return response.data;
}

export async function updateProductionQualityControl(
  taskId: number,
  result: Exclude<ProductionQualityControlResult, "PENDING">,
): Promise<UpdateProductionTaskStatusResponse> {
  const response = await api.patch<UpdateProductionTaskStatusResponse>(
    `/production/tasks/${taskId}/quality-control`,
    { result },
  );
  return response.data;
}

export async function getProductionEligibleOrders(
  signal?: AbortSignal,
): Promise<ProductionEligibleOrder[]> {
  const response = await api.get<ProductionEligibleOrder[]>("/production/tasks/eligible-orders", {
    signal,
  });
  return response.data;
}

export async function createProductionTask(orderId: number): Promise<CreateProductionTaskResponse> {
  const response = await api.post<CreateProductionTaskResponse>("/production/tasks", { orderId });
  return response.data;
}

export async function getProductionTaskMaterialOptions(
  signal?: AbortSignal,
): Promise<ProductionMaterialOption[]> {
  const response = await api.get<ProductionMaterialOption[]>("/production/tasks/material-options", {
    signal,
  });
  return response.data;
}

export async function getProductionTaskDetail(
  taskId: number,
  signal?: AbortSignal,
): Promise<ProductionTaskDetail> {
  const response = await api.get<ProductionTaskDetail>(`/production/tasks/${taskId}`, { signal });
  return response.data;
}

export async function getProductionTaskMaterialAvailability(
  taskId: number,
  signal?: AbortSignal,
): Promise<ProductionTaskMaterialAvailabilityReport> {
  const response = await api.get<ProductionTaskMaterialAvailabilityReport>(
    `/production/tasks/${taskId}/material-availability`,
    { signal },
  );
  return response.data;
}

export async function startProductionTask(
  taskId: number,
): Promise<StartProductionTaskResponse> {
  const response = await api.post<StartProductionTaskResponse>(
    `/production/tasks/${taskId}/start`,
  );
  return response.data;
}

export async function updateProductionTaskStatus(
  taskId: number,
  status: ProductionTaskStatus,
): Promise<UpdateProductionTaskStatusResponse> {
  const response = await api.patch<UpdateProductionTaskStatusResponse>(
    `/production/tasks/${taskId}/status`,
    { status },
  );
  return response.data;
}

export async function getProductionTaskMaterialUsage(
  taskId: number,
  signal?: AbortSignal,
): Promise<ProductionTaskMaterialUsageReport> {
  const response = await api.get<ProductionTaskMaterialUsageReport>(
    `/production/tasks/${taskId}/material-usage`,
    { signal },
  );
  return response.data;
}

export async function recordProductionTaskMaterialUsage(
  taskId: number,
): Promise<RecordProductionTaskMaterialUsageResponse> {
  const response = await api.post<RecordProductionTaskMaterialUsageResponse>(
    `/production/tasks/${taskId}/material-usage`,
  );
  return response.data;
}

export async function updateProductionTaskDetails(
  taskId: number,
  payload: { workDetails: string; workAssignment: string; workNotes: string | null },
): Promise<UpdateProductionTaskDetailsResponse> {
  const response = await api.put<UpdateProductionTaskDetailsResponse>(
    `/production/tasks/${taskId}/details`,
    payload,
  );
  return response.data;
}

export async function updateProductionTaskMaterials(
  taskId: number,
  materials: Array<{ inventoryMaterialId: number; requiredQuantity: string }>,
): Promise<UpdateProductionTaskMaterialsResponse> {
  const response = await api.put<UpdateProductionTaskMaterialsResponse>(
    `/production/tasks/${taskId}/materials`,
    { materials },
  );
  return response.data;
}

export function getProductionTaskApiError(
  error: unknown,
  fallback: string,
): ProductionTaskApiError {
  if (!axios.isAxiosError<ProductionTaskApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }
  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

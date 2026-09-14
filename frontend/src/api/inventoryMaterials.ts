import axios from "axios";

import { api } from "./client";

export type InventoryMaterialType = "FABRIC" | "RAW_MATERIAL";
export type InventoryMaterialStatus = "ACTIVE" | "INACTIVE" | "DISCONTINUED";
export type InventoryStockState = "LOW_STOCK" | "SUFFICIENT";

export interface InventoryMaterial {
  id: number;
  sourceMaterialSupplyId: number | null;
  materialCode: string;
  materialName: string;
  materialDescription: string | null;
  materialType: InventoryMaterialType;
  unitOfMeasure: string;
  currentQuantity: string;
  lowStockThreshold: string;
  status: InventoryMaterialStatus;
  stockState: InventoryStockState;
  createdAt: string;
  updatedAt: string;
}

export interface CreateInventoryMaterialInput {
  sourceMaterialSupplyId: string;
  materialCode: string;
  materialName: string;
  materialDescription: string;
  materialType: InventoryMaterialType;
  unitOfMeasure: string;
  currentQuantity: string;
  lowStockThreshold: string;
}

export interface CreateInventoryMaterialResponse {
  message: string;
  material: InventoryMaterial;
}

export interface UpdateInventoryMaterialInput {
  materialCode: string;
  materialName: string;
  materialDescription: string;
  materialType: InventoryMaterialType;
  unitOfMeasure: string;
  lowStockThreshold: string;
  status: Exclude<InventoryMaterialStatus, "DISCONTINUED">;
}

export interface UpdateInventoryMaterialResponse {
  message: string;
  material: InventoryMaterial;
}

export interface ArchiveInventoryMaterialResponse {
  message: string;
  material: InventoryMaterial;
}

export type InventoryAvailabilityState =
  | "AVAILABLE"
  | "INSUFFICIENT_STOCK"
  | "NOT_ACTIVE";

export interface InventoryMaterialAvailability {
  materialId: number;
  currentQuantity: string;
  requiredQuantity: string;
  status: InventoryMaterialStatus;
  available: boolean;
  availabilityState: InventoryAvailabilityState;
}

export interface ConsumeInventoryMaterialResponse {
  message: string;
  material: InventoryMaterial;
}

export interface InventoryMaterialListResponse {
  materials: InventoryMaterial[];
}

export interface LowStockInventoryMaterialListResponse {
  count: number;
  materials: InventoryMaterial[];
}

export interface InventoryMaterialListFilters {
  search?: string;
  status?: InventoryMaterialStatus;
  materialType?: InventoryMaterialType;
}

interface InventoryMaterialApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface InventoryMaterialApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function createInventoryMaterial(
  input: CreateInventoryMaterialInput,
): Promise<CreateInventoryMaterialResponse> {
  const sourceMaterialSupplyId = input.sourceMaterialSupplyId.trim();
  const response = await api.post<CreateInventoryMaterialResponse>(
    "/inventory-materials",
    {
      ...input,
      sourceMaterialSupplyId: sourceMaterialSupplyId ? Number(sourceMaterialSupplyId) : null,
    },
  );
  return response.data;
}

export async function getInventoryMaterial(
  materialId: number,
  signal?: AbortSignal,
): Promise<InventoryMaterial> {
  const response = await api.get<InventoryMaterial>(`/inventory-materials/${materialId}`, { signal });
  return response.data;
}

export async function updateInventoryMaterial(
  materialId: number,
  input: UpdateInventoryMaterialInput,
): Promise<UpdateInventoryMaterialResponse> {
  const response = await api.put<UpdateInventoryMaterialResponse>(
    `/inventory-materials/${materialId}`,
    input,
  );
  return response.data;
}

export async function archiveInventoryMaterial(
  materialId: number,
): Promise<ArchiveInventoryMaterialResponse> {
  const response = await api.delete<ArchiveInventoryMaterialResponse>(
    `/inventory-materials/${materialId}`,
  );
  return response.data;
}

export async function getInventoryMaterialAvailability(
  materialId: number,
  requiredQuantity: string,
  signal?: AbortSignal,
): Promise<InventoryMaterialAvailability> {
  const response = await api.get<InventoryMaterialAvailability>(
    `/inventory-materials/${materialId}/availability`,
    { params: { requiredQuantity: requiredQuantity.trim() }, signal },
  );
  return response.data;
}

export async function consumeInventoryMaterial(
  materialId: number,
  quantity: string,
): Promise<ConsumeInventoryMaterialResponse> {
  const response = await api.post<ConsumeInventoryMaterialResponse>(
    `/inventory-materials/${materialId}/consume`,
    { quantity: quantity.trim() },
  );
  return response.data;
}

export async function getInventoryMaterials(
  filters: InventoryMaterialListFilters = {},
  signal?: AbortSignal,
): Promise<InventoryMaterial[]> {
  const response = await api.get<InventoryMaterialListResponse>("/inventory-materials", {
    params: filters,
    signal,
  });
  return response.data.materials;
}

export async function getLowStockInventoryMaterials(
  signal?: AbortSignal,
): Promise<LowStockInventoryMaterialListResponse> {
  const response = await api.get<LowStockInventoryMaterialListResponse>(
    "/inventory-materials/low-stock",
    { signal },
  );
  return response.data;
}

export function getInventoryMaterialApiError(
  error: unknown,
  fallback: string,
): InventoryMaterialApiError {
  if (!axios.isAxiosError<InventoryMaterialApiErrorBody>(error)) {
    return { message: error instanceof Error ? error.message : fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

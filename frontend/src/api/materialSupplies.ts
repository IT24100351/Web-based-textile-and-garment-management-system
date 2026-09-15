import axios from "axios";

import { api } from "./client";

export type MaterialSupplyStatus = "ACTIVE" | "INACTIVE" | "DISCONTINUED";

export interface MaterialSupply {
  id: number;
  supplierId: number;
  materialCode: string;
  materialName: string;
  materialDescription: string | null;
  quantity: string;
  unitOfMeasure: string;
  unitPrice: string;
  deliveryLeadTimeDays: number;
  deliveryNotes: string | null;
  status: MaterialSupplyStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMaterialSupplyInput {
  materialCode: string;
  materialName: string;
  materialDescription: string;
  quantity: string;
  unitOfMeasure: string;
  unitPrice: string;
  deliveryLeadTimeDays: string;
  deliveryNotes: string;
}

export interface CreateMaterialSupplyResponse {
  message: string;
  supply: MaterialSupply;
}

export interface UpdateMaterialSupplyInput {
  quantity: string;
  unitPrice: string;
  deliveryLeadTimeDays: string;
  deliveryNotes: string;
}

export interface UpdateMaterialSupplyResponse {
  message: string;
  supply: MaterialSupply;
}

export interface ArchiveMaterialSupplyResponse {
  message: string;
  supply: MaterialSupply;
}

export interface MaterialSupplyListItem extends MaterialSupply {
  supplierBusinessName: string;
}

export interface MaterialSupplyListFilters {
  search?: string;
  status?: MaterialSupplyStatus;
}

export interface MaterialSupplyListResponse {
  supplies: MaterialSupplyListItem[];
}

interface MaterialSupplyApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface MaterialSupplyApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function createMaterialSupply(
  input: CreateMaterialSupplyInput,
): Promise<CreateMaterialSupplyResponse> {
  const response = await api.post<CreateMaterialSupplyResponse>("/material-supplies", {
    ...input,
    deliveryLeadTimeDays: Number(input.deliveryLeadTimeDays),
  });
  return response.data;
}

export async function getMaterialSupplies(
  filters: MaterialSupplyListFilters = {},
  signal?: AbortSignal,
): Promise<MaterialSupplyListItem[]> {
  const response = await api.get<MaterialSupplyListResponse>("/material-supplies", {
    params: filters,
    signal,
  });
  return response.data.supplies;
}

export async function getMaterialSupply(
  supplyId: number,
  signal?: AbortSignal,
): Promise<MaterialSupply> {
  const response = await api.get<MaterialSupply>(`/material-supplies/${supplyId}`, {
    signal,
  });
  return response.data;
}

export async function updateMaterialSupply(
  supplyId: number,
  input: UpdateMaterialSupplyInput,
): Promise<UpdateMaterialSupplyResponse> {
  const response = await api.put<UpdateMaterialSupplyResponse>(
    `/material-supplies/${supplyId}`,
    {
      ...input,
      deliveryLeadTimeDays: Number(input.deliveryLeadTimeDays),
    },
  );
  return response.data;
}

export async function archiveMaterialSupply(
  supplyId: number,
): Promise<ArchiveMaterialSupplyResponse> {
  const response = await api.delete<ArchiveMaterialSupplyResponse>(
    `/material-supplies/${supplyId}`,
  );
  return response.data;
}

export function getMaterialSupplyApiError(
  error: unknown,
  fallback: string,
): MaterialSupplyApiError {
  if (!axios.isAxiosError<MaterialSupplyApiErrorBody>(error)) {
    return { message: error instanceof Error ? error.message : fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

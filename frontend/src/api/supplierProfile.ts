import axios from "axios";

import { api } from "./client";

export interface SupplierProfile {
  id: number;
  userId: number;
  businessName: string;
  contactPhone: string;
  address: string;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierProfileInput {
  businessName: string;
  contactPhone: string;
  address: string;
}

export interface SaveSupplierProfileResponse {
  message: string;
  profile: SupplierProfile;
}

interface SupplierProfileApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface SupplierProfileApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function getSupplierProfile(signal?: AbortSignal): Promise<SupplierProfile> {
  const response = await api.get<SupplierProfile>("/supplier-profile", { signal });
  return response.data;
}

export async function saveSupplierProfile(
  input: SupplierProfileInput,
): Promise<SaveSupplierProfileResponse> {
  const response = await api.put<SaveSupplierProfileResponse>("/supplier-profile", input);
  return response.data;
}

export function getSupplierProfileApiError(
  error: unknown,
  fallback: string,
): SupplierProfileApiError {
  if (!axios.isAxiosError<SupplierProfileApiErrorBody>(error)) {
    return { message: error instanceof Error ? error.message : fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

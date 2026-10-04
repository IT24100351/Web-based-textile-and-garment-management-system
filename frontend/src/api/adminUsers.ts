import axios from "axios";

import type { UserRole } from "./auth";
import { api } from "./client";

export interface AdminUser {
  id: number;
  fullName: string;
  email: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminUserFilters {
  search?: string;
  role?: UserRole;
  active?: boolean;
}

interface AdminUserMutationResponse {
  message: string;
  user: AdminUser;
}

interface ApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export async function getAdminUsers(filters: AdminUserFilters = {}): Promise<AdminUser[]> {
  const response = await api.get<AdminUser[]>("/admin/users", { params: filters });
  return response.data;
}

export async function getAdminUser(userId: number): Promise<AdminUser> {
  const response = await api.get<AdminUser>(`/admin/users/${userId}`);
  return response.data;
}

export async function createInternalUser(input: {
  fullName: string;
  email: string;
  password: string;
  role: Exclude<UserRole, "CUSTOMER">;
}): Promise<AdminUserMutationResponse> {
  const response = await api.post<AdminUserMutationResponse>("/admin/users", input);
  return response.data;
}

export async function changeAdminUserRole(
  userId: number,
  role: UserRole,
): Promise<AdminUserMutationResponse> {
  const response = await api.patch<AdminUserMutationResponse>(
    `/admin/users/${userId}/role`,
    { role },
  );
  return response.data;
}

export async function changeAdminUserStatus(
  userId: number,
  active: boolean,
): Promise<AdminUserMutationResponse> {
  const response = await api.patch<AdminUserMutationResponse>(
    `/admin/users/${userId}/status`,
    { active },
  );
  return response.data;
}

export function getAdminUserApiError(error: unknown, fallback: string) {
  if (!axios.isAxiosError<ApiErrorBody>(error)) {
    return { code: undefined, message: fallback, fields: {} as Record<string, string> };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
  };
}

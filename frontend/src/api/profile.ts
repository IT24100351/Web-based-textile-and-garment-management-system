import axios from "axios";

import type { AuthUser } from "./auth";
import { api } from "./client";

export interface ProfileInput {
  fullName: string;
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

interface MessageResponse {
  message: string;
}

interface ProfileApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface ProfileApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
}

export async function getProfile(signal?: AbortSignal): Promise<AuthUser> {
  const response = await api.get<AuthUser>("/profile", { signal });
  return response.data;
}

export async function updateProfile(input: ProfileInput): Promise<AuthUser> {
  const response = await api.put<AuthUser>("/profile", input);
  return response.data;
}

export async function changePassword(input: ChangePasswordInput): Promise<string> {
  const response = await api.put<MessageResponse>("/profile/password", input);
  return response.data.message;
}

export function getProfileApiError(error: unknown, fallback: string): ProfileApiError {
  if (!axios.isAxiosError<ProfileApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
  };
}

import axios from "axios";

import { api } from "./client";

export type UserRole =
  | "ADMINISTRATOR"
  | "SUPPLIER"
  | "INVENTORY_MANAGER"
  | "PRODUCTION_MANAGER"
  | "SALES_OFFICER"
  | "CUSTOMER";

export interface AuthUser {
  id: number;
  fullName: string;
  email: string;
  role: UserRole;
}

interface AuthResponse {
  message: string;
  user: AuthUser;
}

interface SessionResponse {
  authenticated: boolean;
  user: AuthUser | null;
}

interface ApiErrorBody {
  error?: {
    message?: string;
  };
}

export async function registerCustomer(input: {
  fullName: string;
  email: string;
  password: string;
}): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/register", input);
  return response.data;
}

export async function loginUser(input: {
  email: string;
  password: string;
}): Promise<AuthResponse> {
  const response = await api.post<AuthResponse>("/auth/login", input);
  return response.data;
}

export async function logoutUser(): Promise<void> {
  await api.post("/auth/logout");
}

export async function requestPasswordReset(input: { email: string }): Promise<{ message: string }> {
  const response = await api.post<{ message: string }>("/auth/password-reset/request", input);
  return response.data;
}

export async function resetPassword(input: {
  token: string;
  password: string;
}): Promise<{ message: string }> {
  const response = await api.post<{ message: string }>("/auth/password-reset/confirm", input);
  return response.data;
}

export async function resendEmailVerification(input: {
  email: string;
}): Promise<{ message: string }> {
  const response = await api.post<{ message: string }>(
    "/auth/email-verification/resend",
    input,
  );
  return response.data;
}

export async function verifyCustomerEmail(input: {
  email: string;
  code: string;
}): Promise<{ message: string }> {
  const response = await api.post<{ message: string }>(
    "/auth/email-verification/confirm",
    input,
  );
  return response.data;
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  try {
    const response = await api.get<SessionResponse>("/auth/session");
    return response.data.authenticated ? response.data.user : null;
  } catch (error: unknown) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      return null;
    }
    throw error;
  }
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (!axios.isAxiosError<ApiErrorBody>(error)) {
    return fallback;
  }

  return error.response?.data.error?.message ?? fallback;
}

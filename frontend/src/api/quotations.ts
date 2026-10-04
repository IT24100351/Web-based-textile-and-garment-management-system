import axios from "axios";

import { api } from "./client";

export interface QuotationCustomerOption {
  id: number;
  fullName: string;
  email: string;
}

export interface CreateQuotationItemInput {
  productId: number;
  variantId: number;
  quantity: number;
}

export interface QuotationItem {
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

export interface CreateQuotationResponse {
  message: string;
  id: number;
  quotationNumber: string;
  customer: QuotationCustomerOption;
  issuedAt: string;
  items: QuotationItem[];
  totalAmount: string;
}

export interface QuotationSummary {
  id: number;
  quotationNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  issuedAt: string;
  itemCount: number;
  totalAmount: string;
}

export interface QuotationDetail {
  id: number;
  quotationNumber: string;
  customerId: number;
  customerName: string;
  customerEmail: string;
  issuedAt: string;
  items: QuotationItem[];
  totalAmount: string;
}

interface QuotationApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface QuotationApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function getQuotationCustomers(
  search?: string,
  signal?: AbortSignal,
): Promise<QuotationCustomerOption[]> {
  const response = await api.get<QuotationCustomerOption[]>("/quotations/customers", {
    params: search?.trim() ? { search: search.trim() } : undefined,
    signal,
  });
  return response.data;
}

export async function createQuotation(input: {
  customerId: number;
  items: CreateQuotationItemInput[];
}): Promise<CreateQuotationResponse> {
  const response = await api.post<CreateQuotationResponse>("/quotations", input);
  return response.data;
}

export async function getQuotations(signal?: AbortSignal): Promise<QuotationSummary[]> {
  const response = await api.get<QuotationSummary[]>("/quotations", { signal });
  return response.data;
}

export async function getQuotation(
  quotationId: number,
  signal?: AbortSignal,
): Promise<QuotationDetail> {
  const response = await api.get<QuotationDetail>(`/quotations/${quotationId}`, { signal });
  return response.data;
}

export function getQuotationApiError(error: unknown, fallback: string): QuotationApiError {
  if (!axios.isAxiosError<QuotationApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }
  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

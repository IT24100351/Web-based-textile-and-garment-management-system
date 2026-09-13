import axios from "axios";

import type {
  GarmentProductDetails,
  VariantStatus,
} from "../products/productTypes";
import { api } from "./client";

export type CreateProductAvailability = "AVAILABLE" | "UNAVAILABLE";

export interface ProductDetailsInput {
  name: string;
  category: string;
  description?: string;
  imageUrl?: string;
}

export interface ProductVariantInput {
  size: string;
  color: string;
  price: string;
  availability: CreateProductAvailability;
}

export interface CreateProductInput extends ProductDetailsInput {
  variants: ProductVariantInput[];
}

export interface CreateProductResponse {
  message: string;
  product: GarmentProductDetails;
}

export interface UpdateProductInput extends ProductDetailsInput, ProductVariantInput {
  variantId: number;
}

export interface UploadProductImageResponse {
  message: string;
  imageUrl: string;
  mediaType: string;
  size: number;
}

export interface UpdateProductResponse {
  message: string;
  product: GarmentProductDetails;
}

export interface DiscontinueProductResponse {
  message: string;
  product: GarmentProductDetails;
}

export interface ProductCatalogFilters {
  search?: string;
  category?: string;
  size?: string;
  color?: string;
  availability?: VariantStatus;
}

interface ProductApiErrorBody {
  error?: {
    code?: string;
    message?: string;
    fields?: Record<string, string>;
  };
}

export interface ProductApiError {
  code?: string;
  message: string;
  fields: Record<string, string>;
  status?: number;
}

export async function createProduct(
  input: CreateProductInput,
): Promise<CreateProductResponse> {
  const response = await api.post<CreateProductResponse>("/products", input);
  return response.data;
}

export async function uploadProductImage(
  imageFile: File,
): Promise<UploadProductImageResponse> {
  const data = new FormData();
  data.append("imageFile", imageFile);
  const response = await api.post<UploadProductImageResponse>("/products/images", data);
  return response.data;
}

export async function getProduct(
  productId: number,
  signal?: AbortSignal,
): Promise<GarmentProductDetails> {
  const response = await api.get<GarmentProductDetails>(`/products/${productId}`, { signal });
  return response.data;
}

export async function updateProduct(
  productId: number,
  input: UpdateProductInput,
): Promise<UpdateProductResponse> {
  const response = await api.put<UpdateProductResponse>(`/products/${productId}`, input);
  return response.data;
}

export async function discontinueProduct(
  productId: number,
): Promise<DiscontinueProductResponse> {
  const response = await api.delete<DiscontinueProductResponse>(`/products/${productId}`);
  return response.data;
}

export async function getCatalogProduct(
  productId: number,
  signal?: AbortSignal,
): Promise<GarmentProductDetails> {
  const response = await api.get<GarmentProductDetails>(
    `/products/catalog/${productId}`,
    { signal },
  );
  return response.data;
}

export async function getCatalogProducts(
  filters: ProductCatalogFilters = {},
  signal?: AbortSignal,
): Promise<GarmentProductDetails[]> {
  const response = await api.get<GarmentProductDetails[]>("/products", {
    params: filters,
    signal,
  });
  return response.data;
}

export function getProductApiError(error: unknown, fallback: string): ProductApiError {
  if (!axios.isAxiosError<ProductApiErrorBody>(error)) {
    return { message: fallback, fields: {} };
  }

  return {
    code: error.response?.data.error?.code,
    message: error.response?.data.error?.message ?? fallback,
    fields: error.response?.data.error?.fields ?? {},
    status: error.response?.status,
  };
}

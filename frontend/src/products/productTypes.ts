export type CategoryStatus = "ACTIVE" | "INACTIVE";

export type ProductStatus = "ACTIVE" | "INACTIVE" | "DISCONTINUED";

export type VariantStatus = "AVAILABLE" | "UNAVAILABLE" | "DISCONTINUED";

export interface ProductCategory {
  id: number;
  name: string;
  description: string | null;
  status: CategoryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface GarmentProduct {
  id: number;
  categoryId: number;
  name: string;
  description: string | null;
  imageUrl?: string | null;
  status: ProductStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ProductVariant {
  id: number;
  productId: number;
  size: string;
  color: string;
  /** Exact DECIMAL value serialized by the API; do not convert it to floating point. */
  price: string;
  status: VariantStatus;
  createdAt: string;
  updatedAt: string;
}

export interface GarmentProductDetails extends GarmentProduct {
  category: ProductCategory;
  variants: ProductVariant[];
}

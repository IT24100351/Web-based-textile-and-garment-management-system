import { api } from "./client";

export type SharedSearchModule =
  | "PRODUCT"
  | "SUPPLY"
  | "INVENTORY"
  | "ORDER"
  | "DELIVERY";

export interface SharedSearchResult {
  module: SharedSearchModule;
  recordId: number;
  title: string;
  subtitle: string;
  status: string;
  path: string;
}

export interface SharedSearchPage {
  search: string;
  page: number;
  size: number;
  totalResults: number;
  totalPages: number;
  results: SharedSearchResult[];
}

export async function searchCoreRecords(
  search: string,
  page = 0,
  size = 10,
): Promise<SharedSearchPage> {
  const response = await api.get<SharedSearchPage>("/search", {
    params: { search, page, size },
  });
  return response.data;
}

import axios from "axios";

import type { UserRole } from "./auth";
import { api } from "./client";

export type InternalRole = Exclude<UserRole, "CUSTOMER">;

export interface RoleAccessResponse {
  role: InternalRole;
  message: string;
}

const accessEndpoints: Record<InternalRole, string> = {
  ADMINISTRATOR: "/role-access/administrator",
  SUPPLIER: "/role-access/supplier",
  INVENTORY_MANAGER: "/role-access/inventory-manager",
  PRODUCTION_MANAGER: "/role-access/production-manager",
  SALES_OFFICER: "/role-access/sales-officer",
};

export const roleAccessPagePaths: Record<InternalRole, string> = {
  ADMINISTRATOR: "/access/administrator",
  SUPPLIER: "/access/supplier",
  INVENTORY_MANAGER: "/access/inventory-manager",
  PRODUCTION_MANAGER: "/access/production-manager",
  SALES_OFFICER: "/access/sales-officer",
};

export function isInternalRole(role: UserRole): role is InternalRole {
  return role !== "CUSTOMER";
}

export async function getRoleAccess(
  role: InternalRole,
  signal?: AbortSignal,
): Promise<RoleAccessResponse> {
  const response = await api.get<RoleAccessResponse>(accessEndpoints[role], { signal });
  return response.data;
}

export function isCanceledRequest(error: unknown): boolean {
  return axios.isCancel(error);
}

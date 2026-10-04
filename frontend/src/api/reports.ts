import { api } from "./client";
import type { UserRole } from "./auth";

export interface ReportMetric {
  key: string;
  label: string;
  value: number;
}

export interface ReportSection {
  key: "orders" | "inventory" | "production" | "delivery" | string;
  title: string;
  basis: string;
  metrics: ReportMetric[];
}

export interface DashboardReport {
  role: UserRole;
  from: string;
  to: string;
  timeZone: string;
  generatedAt: string;
  sections: ReportSection[];
  notes: string[];
}

export async function getDashboardReport(
  from: string,
  to: string,
  signal?: AbortSignal,
): Promise<DashboardReport> {
  const response = await api.get<DashboardReport>("/reports/dashboard", {
    params: { from, to },
    signal,
  });
  return response.data;
}

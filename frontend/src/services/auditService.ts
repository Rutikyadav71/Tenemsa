import axiosInstance from "./axiosInstance";
import type { AuditPage } from "../types/models";

export async function fetchAuditLog(page: number, size: number, action?: string): Promise<AuditPage> {
  const res = await axiosInstance.get<AuditPage>("/api/audit", {
    params: { page, size, ...(action ? { action } : {}) },
  });
  return res.data;
}

export async function fetchAuditActions(): Promise<string[]> {
  const res = await axiosInstance.get<string[]>("/api/audit/actions");
  return res.data;
}

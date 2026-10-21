import axiosInstance from "./axiosInstance";
import type {
  AuthResponse, CompanyInfo, CompanyRegisterInput, WorkspaceLookup,
} from "../types/models";

const LAST_WORKSPACE_KEY = "lastWorkspace";

/** Create a company + its Owner account; returns a signed-in session. */
export async function registerCompany(input: CompanyRegisterInput): Promise<AuthResponse> {
  const res = await axiosInstance.post<AuthResponse>("/api/companies/register", input);
  return res.data;
}

/** Public lookup used by the "find your workspace" screen. Returns null when not found. */
export async function lookupWorkspace(slug: string): Promise<WorkspaceLookup | null> {
  try {
    const res = await axiosInstance.get<WorkspaceLookup>("/api/companies/lookup", {
      params: { slug: slug.trim().toLowerCase() },
    });
    return res.data;
  } catch (err: any) {
    if (err.response?.status === 404) return null;
    throw err;
  }
}

export async function getMyCompany(): Promise<CompanyInfo> {
  const res = await axiosInstance.get<CompanyInfo>("/api/companies/me");
  return res.data;
}

export async function updateMyCompany(name: string): Promise<CompanyInfo> {
  const res = await axiosInstance.put<CompanyInfo>("/api/companies/me", { name });
  return res.data;
}

// Remembering the last workspace is a per-browser convenience only (never trusted by the server).
export function rememberWorkspace(slug: string): void {
  try { localStorage.setItem(LAST_WORKSPACE_KEY, slug); } catch { /* storage unavailable */ }
}
export function getRememberedWorkspace(): string {
  try { return localStorage.getItem(LAST_WORKSPACE_KEY) || ""; } catch { return ""; }
}

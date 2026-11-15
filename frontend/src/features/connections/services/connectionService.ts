import axiosInstance from "../../../shared/api/axiosInstance";

export interface AppRow {
  code: string; label: string; description: string; access: string;
  configured: boolean; enabled: boolean; connected: boolean;
  status: "ACTIVE" | "NEEDS_RECONNECT" | null; account: string | null; connectedAt: string | null;
}
export interface Connections { ready: boolean; apps: AppRow[]; companyConnections: number; maxConnections: number | null }
export interface PreviewItem { title: string; subtitle: string; url: string }

export const getConnections = async () => (await axiosInstance.get<Connections>("/api/connections")).data;
export const authorizeApp = async (code: string) => (await axiosInstance.post<{ url: string }>(`/api/connections/${code}/authorize`)).data.url;
export const disconnectApp = async (code: string) => (await axiosInstance.delete<Connections>(`/api/connections/${code}`)).data;
export const previewApp = async (code: string) => (await axiosInstance.get<PreviewItem[]>(`/api/connections/${code}/preview`)).data;
export const setAppEnabled = async (code: string, enabled: boolean) => (await axiosInstance.put<Connections>(`/api/connections/apps/${code}`, { enabled })).data;

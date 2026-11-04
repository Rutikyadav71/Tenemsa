import axiosInstance from "../../../shared/api/axiosInstance";
import { getRefreshToken } from "../../../shared/lib/authStorage";

export interface DeviceSession { id: number; device: string | null; signedInAt: string; expiresAt: string; current: boolean; }

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  // Sending this device's refresh token keeps it signed in while every other device is signed out.
  await axiosInstance.post("/api/auth/change-password", { currentPassword, newPassword, refreshToken: getRefreshToken() || undefined });
}

export async function listSessions(): Promise<DeviceSession[]> {
  const token = getRefreshToken();
  return (await axiosInstance.get<DeviceSession[]>("/api/auth/sessions", { headers: token ? { "X-Refresh-Token": token } : {} })).data;
}

export async function revokeSession(id: number): Promise<void> {
  await axiosInstance.delete(`/api/auth/sessions/${id}`);
}

/** "Chrome on Windows" from a User-Agent string. Best effort: unknown agents fall back to "Unknown device". */
export function deviceName(ua: string | null | undefined): string {
  if (!ua) return "Unknown device";
  const browser = /Edg\//.test(ua) ? "Edge" : /OPR\/|Opera/.test(ua) ? "Opera" : /Firefox\//.test(ua) ? "Firefox"
    : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : "";
  const os = /Windows/.test(ua) ? "Windows" : /Android/.test(ua) ? "Android" : /iPhone|iPad|iOS/.test(ua) ? "iOS"
    : /Mac OS X|Macintosh/.test(ua) ? "macOS" : /Linux/.test(ua) ? "Linux" : "";
  if (browser && os) return `${browser} on ${os}`;
  return browser || os || "Unknown device";
}

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8080";
const KEY = "pc-token";

export const getToken = () => { try { return sessionStorage.getItem(KEY); } catch { return null; } };
export const setToken = (t: string | null) => { try { t ? sessionStorage.setItem(KEY, t) : sessionStorage.removeItem(KEY); } catch { /* private mode */ } };

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

/** Sessions of this console are short and live only in this tab (sessionStorage), never in localStorage. */
export async function api<T = unknown>(path: string, opts: { method?: string; body?: unknown; auth?: boolean } = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const token = getToken();
  if (opts.auth !== false && token) headers.Authorization = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { method: opts.method ?? "GET", headers, body: opts.body === undefined ? undefined : JSON.stringify(opts.body) });
  } catch {
    throw new ApiError(0, "Can't reach the server. Is the backend running with PLATFORM_ENABLED=true?");
  }
  let data: unknown = null;
  const text = await res.text();
  if (text) { try { data = JSON.parse(text); } catch { data = text; } }
  if (!res.ok) {
    if (res.status === 401 && opts.auth !== false && token) {
      setToken(null);
      window.dispatchEvent(new Event("pc-signed-out"));
    }
    const msg = typeof data === "object" && data && "message" in data ? String((data as { message: unknown }).message)
      : res.status === 404 ? "Not found. Is PLATFORM_ENABLED=true on the backend?" : "Something went wrong.";
    throw new ApiError(res.status, msg);
  }
  return data as T;
}

export const errText = (e: unknown) => (e instanceof Error ? e.message : "Something went wrong.");

export const fmtDate = (s?: string | null) => (s ? new Date(s).toLocaleString("en-IN", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—");
export const fmtDay = (s?: string | null) => (s ? new Date(s).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "—");
export const fmtNum = (n?: number | null) => (n ?? 0).toLocaleString("en-IN");
export const fmtBytes = (b?: number | null) => { const n = b ?? 0; return n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(1)} KB` : n < 1073741824 ? `${(n / 1048576).toFixed(1)} MB` : `${(n / 1073741824).toFixed(2)} GB`; };
export function ago(s?: string | null) {
  if (!s) return "never";
  const m = Math.floor((Date.now() - new Date(s).getTime()) / 60000);
  if (m < 1) return "just now"; if (m < 60) return `${m} min ago`;
  const h = Math.floor(m / 60); if (h < 24) return `${h} h ago`;
  const d = Math.floor(h / 24); return d < 60 ? `${d} d ago` : fmtDay(s);
}

export interface Admin { id: number; name: string; email: string; slot: number; twoFactor: boolean; lastLoginAt: string | null; createdAt: string }
export interface CompanyRow { id: number; name: string; slug: string; status: "ACTIVE" | "FROZEN" | "DELETED"; aiEnabled: boolean; createdAt: string; members: number; lastActiveAt: string | null; ownerName: string | null; ownerEmail: string | null }
export interface Page<T> { content: T[]; page: number; totalPages: number; totalElements: number }

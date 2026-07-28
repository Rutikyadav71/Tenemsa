import type { AuthResponse, ChatRoleTag } from "../types/models";

/**
 * Single place that writes auth data to localStorage. Previously each of
 * login, admin login, and (now) OAuth callback and token refresh would each
 * hand-write the same ~10 localStorage.setItem calls — exactly the kind of
 * duplicated logic that's caused real bugs earlier in this project (see
 * ChatUserController's history). One function, one place to fix if a field
 * is ever added or renamed again.
 */
export function storeAuthResponse(resp: AuthResponse, roleTag: ChatRoleTag): void {
  localStorage.clear();
  localStorage.setItem("token", resp.token);
  localStorage.setItem("refreshToken", resp.refreshToken || "");
  localStorage.setItem("role", roleTag);
  localStorage.setItem("id", String(resp.id ?? ""));
  localStorage.setItem("empId", resp.empId != null ? String(resp.empId) : "");
  localStorage.setItem("name", resp.name || "");
  localStorage.setItem("email", resp.email || "");
  localStorage.setItem("companyId", String(resp.companyId ?? ""));
  localStorage.setItem("companyName", resp.companyName || "");
  localStorage.setItem("department", resp.department || "");
  localStorage.setItem("salary", resp.salary != null ? String(resp.salary) : "");
  localStorage.setItem("primaryOwner", String(resp.primaryOwner || false));
  localStorage.setItem("emailVerified", String(resp.emailVerified || false));
}

/** Updates just the token pair after a silent refresh — everything else (name, role, etc.)
 *  is unchanged from the original login, so there's no reason to touch it. */
export function updateTokens(token: string, refreshToken: string): void {
  localStorage.setItem("token", token);
  localStorage.setItem("refreshToken", refreshToken || "");
}

export function getRefreshToken(): string | null {
  return localStorage.getItem("refreshToken");
}

export function clearAuthStorage(): void {
  localStorage.clear();
}

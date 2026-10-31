import type { AuthResponse, ChatRoleTag } from "../types/models";
import { chatTagFor } from "./roles";

/**
 * Single place that writes auth data to localStorage. Previously each of
 * login, admin login, and (now) OAuth callback and token refresh would each
 * hand-write the same ~10 localStorage.setItem calls — exactly the kind of
 * duplicated logic that's caused real bugs earlier in this project (see
 * ChatUserController's history). One function, one place to fix if a field
 * is ever added or renamed again.
 */
export function storeAuthResponse(resp: AuthResponse, roleTagOverride?: ChatRoleTag): void {
  // The chat tag always follows the business role (same rule as the backend), whichever sign-in page was used.
  const roleTag = chatTagFor(resp.role) || roleTagOverride;
  localStorage.clear();
  localStorage.setItem("token", resp.token);
  localStorage.setItem("refreshToken", resp.refreshToken || "");
  localStorage.setItem("role", roleTag);
  // Distinct from "role" above on purpose — "role" is the two-value chat/
  // WebSocket routing tag (see ChatRoleTag), "userRole" is the actual
  // business role (OWNER/ADMIN/HR/MANAGER/EMPLOYEE/INTERN). Nothing stored
  // this before RBAC phase 3, meaning no frontend component had any way to
  // make a role-aware UI decision beyond the coarse admin/employee split —
  // e.g. which target roles to offer in the "add employee" form, which
  // depends on the CALLER's specific role, not just whether they're
  // "admin-tier" at all.
  localStorage.setItem("userRole", resp.role || "");
  localStorage.setItem("id", String(resp.id ?? ""));
  localStorage.setItem("empId", resp.empId != null ? String(resp.empId) : "");
  localStorage.setItem("name", resp.name || "");
  localStorage.setItem("permissions", JSON.stringify(resp.permissions || []));
  // The admin workspace reads these keys; set them for every role so HR works there too.
  localStorage.setItem("adminName", resp.name || "");
  localStorage.setItem("adminEmail", resp.email || "");
  localStorage.setItem("email", resp.email || "");
  localStorage.setItem("companyId", String(resp.companyId ?? ""));
  localStorage.setItem("companyName", resp.companyName || "");
  localStorage.setItem("companySlug", resp.companySlug || "");
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

import type { ChatRoleTag, Role } from "../types/models";

/** Roles that work in the admin workspace (/admin). Everyone else uses the employee workspace (/user). */
const ADMIN_WORKSPACE_ROLES: Role[] = ["OWNER", "ADMIN", "SUPER_ADMIN", "HR"];

/** Roles the chat system tags "ADMIN". Mirrors backend ChatIdentity; HR chats as a normal user. */
const CHAT_ADMIN_TAGGED: Role[] = ["OWNER", "ADMIN", "SUPER_ADMIN"];

export type Workspace = "admin" | "user";

export function workspaceFor(role: string | null | undefined): Workspace {
  return ADMIN_WORKSPACE_ROLES.includes(role as Role) ? "admin" : "user";
}

export function homeFor(role: string | null | undefined): string {
  return `/${workspaceFor(role)}/dashboard`;
}

export function chatTagFor(role: string | null | undefined): ChatRoleTag {
  return CHAT_ADMIN_TAGGED.includes(role as Role) ? "ADMIN" : "USER";
}

export function currentPermissions(): string[] {
  try {
    return JSON.parse(localStorage.getItem("permissions") || "[]");
  } catch {
    return [];
  }
}

/** True if the signed-in user holds at least one of the given permissions. This only shapes the UI;
 *  the server checks every request again. */
export function can(...permissions: string[]): boolean {
  const mine = currentPermissions();
  return permissions.length === 0 || permissions.some(p => mine.includes(p));
}

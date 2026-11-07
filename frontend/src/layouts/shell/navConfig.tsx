import type { IconType } from "react-icons";
import {
  LuBell, LuBookOpen, LuBot, LuBuilding2, LuCalendarCheck, LuCalendarDays, LuChartColumn, LuClipboardCheck, LuFileText,
  LuFolderKanban, LuHistory, LuLayoutDashboard, LuMail, LuMessageSquare, LuPenLine, LuShieldCheck, LuSparkles,
  LuMegaphone, LuUserPlus, LuUsers, LuUsersRound, LuVideo, LuGauge, LuLibrary,
} from "react-icons/lu";
import { can } from "../../shared/lib/roles";
import type { Workspace } from "../../shared/lib/roles";

/**
 * The one definition of the application's navigation, shared by every role.
 * An item is shown only if the signed-in user holds one of its `perms` (or one of its `roles`);
 * the same rule guards the route itself, and the server still checks every request.
 * `path` is relative to the workspace (/admin or /user); `workspaces` lists where the page exists.
 */
export interface NavItem {
  id: string;
  label: string;
  path: string;
  icon: IconType;
  perms?: string[];
  roles?: string[];
  workspaces?: Workspace[];
  /** Not shown in menus; only used for route guarding and breadcrumbs. */
  hidden?: boolean;
  /** Label used in the command palette in place of `label`. */
  keywords?: string;
}
export interface NavGroup { id: string; label: string; icon: IconType; items: NavItem[] }

const ADMIN_TIER = ["OWNER", "ADMIN", "SUPER_ADMIN"];

export const NAV_GROUPS: NavGroup[] = [
  { id: "home", label: "Home", icon: LuLayoutDashboard, items: [
    { id: "dashboard", label: "Dashboard", path: "dashboard", icon: LuLayoutDashboard, keywords: "home overview" },
  ] },
  { id: "people", label: "People", icon: LuUsers, items: [
    { id: "employees", label: "Employees", path: "employees", icon: LuUsers, perms: ["user:view:any", "user:view:team"], workspaces: ["admin"], keywords: "directory staff" },
    { id: "add-employee", label: "Add employee", path: "add", icon: LuUserPlus, perms: ["user:create"], workspaces: ["admin"], keywords: "new hire onboard" },
    { id: "teams-admin", label: "Teams", path: "teams", icon: LuUsersRound, perms: ["team:manage:any"], workspaces: ["admin"] },
    { id: "teams-user", label: "My teams", path: "teams", icon: LuUsersRound, workspaces: ["user"] },
    { id: "roles", label: "Roles & admins", path: "manage-admins", icon: LuShieldCheck, roles: ADMIN_TIER, workspaces: ["admin"], keywords: "permissions access" },
    { id: "invitations", label: "Invitations", path: "invitations", icon: LuMail, perms: ["user:create"], workspaces: ["admin"], keywords: "invite" },
    { id: "update-employee", label: "Edit employee", path: "update", icon: LuUsers, perms: ["user:update"], workspaces: ["admin"], hidden: true },
  ] },
  { id: "work", label: "Work", icon: LuFolderKanban, items: [
    { id: "projects", label: "Projects", path: "projects", icon: LuFolderKanban, perms: ["project:view"], keywords: "tasks kanban board sprint backlog" },
    { id: "meetings", label: "Meetings", path: "meetings", icon: LuVideo, perms: ["ai:use"], keywords: "summary notes" },
  ] },
  { id: "time", label: "Time & attendance", icon: LuCalendarDays, items: [
    { id: "attendance", label: "Attendance", path: "attendance", icon: LuCalendarCheck, perms: ["attendance:manage:any", "attendance:view:team", "attendance:mark:self"] },
    { id: "apply-leave", label: "Apply for leave", path: "apply-leave", icon: LuCalendarDays, perms: ["leave:apply:self"], keywords: "vacation time off" },
    { id: "leaves", label: "Leave requests", path: "leaves", icon: LuClipboardCheck, perms: ["leave:view:any"], workspaces: ["admin"], keywords: "approve" },
    { id: "team-leaves", label: "Team leave", path: "team-leaves", icon: LuClipboardCheck, perms: ["leave:approve:team"], keywords: "approve" },
    { id: "my-leaves", label: "My leave history", path: "leaves/employee", icon: LuHistory, workspaces: ["user"], hidden: true },
  ] },
  { id: "engage", label: "Engage", icon: LuMessageSquare, items: [
    { id: "chat", label: "Chat", path: "chat", icon: LuMessageSquare, perms: ["chat:use"], keywords: "messages" },
    { id: "announcements", label: "Announcements", path: "announcements", icon: LuMegaphone, perms: ["announcement:send"], workspaces: ["admin"], keywords: "send email everyone broadcast" },
    { id: "notifications", label: "Notifications", path: "notifications", icon: LuBell },
  ] },
  { id: "ai", label: "Tenemsa AI", icon: LuSparkles, items: [
    { id: "assistant", label: "AI Assistant", path: "assistant", icon: LuBot, perms: ["ai:assistant:self"], keywords: "hr assistant ask" },
    { id: "ask-docs", label: "Ask documents", path: "ask-docs", icon: LuBookOpen, perms: ["kb:ask"], keywords: "knowledge search question" },
    { id: "writer", label: "AI Writer", path: "writer", icon: LuPenLine, perms: ["ai:use"], keywords: "draft message" },
    { id: "knowledge", label: "Knowledge base", path: "knowledge", icon: LuLibrary, perms: ["kb:manage"], keywords: "index documents" },
    { id: "ai-usage", label: "AI usage", path: "ai-usage", icon: LuGauge, perms: ["ai:usage:view"], workspaces: ["admin"], keywords: "cost quota" },
  ] },
  { id: "resources", label: "Resources", icon: LuFileText, items: [
    { id: "documents", label: "Documents", path: "documents", icon: LuFileText, perms: ["document:view"], keywords: "files policies" },
  ] },
  { id: "analytics", label: "Analytics", icon: LuChartColumn, items: [
    { id: "insights", label: "Insights", path: "insights", icon: LuChartColumn, keywords: "reports charts" },
    { id: "audit", label: "Audit log", path: "audit", icon: LuHistory, perms: ["audit:view"], workspaces: ["admin"], keywords: "activity history" },
  ] },
  { id: "admin", label: "Administration", icon: LuBuilding2, items: [
    { id: "company", label: "Company settings", path: "company", icon: LuBuilding2, perms: ["company:manage"], workspaces: ["admin"], keywords: "workspace ai switch" },
  ] },
];

export const PROFILE_ITEM: NavItem = { id: "profile", label: "My profile", path: "profile", icon: LuUsers };

export function isAllowed(item: NavItem, workspace: Workspace): boolean {
  if (item.workspaces && !item.workspaces.includes(workspace)) return false;
  if (item.perms && !can(...item.perms)) return false;
  if (item.roles && !item.roles.includes(localStorage.getItem("userRole") || "")) return false;
  return true;
}

export function visibleGroups(workspace: Workspace): NavGroup[] {
  return NAV_GROUPS
    .map(g => ({ ...g, items: g.items.filter(i => !i.hidden && isAllowed(i, workspace)) }))
    .filter(g => g.items.length > 0);
}

export const toPath = (workspace: Workspace, item: NavItem) => `/${workspace}/${item.path}`;

/** The nav entry that owns this URL (so /admin/projects/7/backlog belongs to Projects), or null for pages such as the profile. */
export function findItem(workspace: Workspace, pathname: string): { group: NavGroup; item: NavItem } | null {
  let best: { group: NavGroup; item: NavItem } | null = null;
  for (const group of NAV_GROUPS) {
    for (const item of group.items) {
      if (item.workspaces && !item.workspaces.includes(workspace)) continue;
      const base = toPath(workspace, item);
      if (pathname === base || pathname.startsWith(base + "/")) {
        if (!best || base.length > toPath(workspace, best.item).length) best = { group, item };
      }
    }
  }
  return best;
}

/** Everything in the signed-in user's menus, for the command palette. */
export function paletteItems(workspace: Workspace) {
  return visibleGroups(workspace).flatMap(g => g.items.map(item => ({ group: g.label, item, to: toPath(workspace, item) })));
}

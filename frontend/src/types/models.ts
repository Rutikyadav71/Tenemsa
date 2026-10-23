// Central domain types, mirroring the Phase 1 backend (User, Company, Role).
// Kept here instead of duplicated per-component so a future backend field
// rename only needs one edit here, not a grep across 30 files.

export type Role = "SUPER_ADMIN" | "OWNER" | "ADMIN" | "HR" | "MANAGER" | "EMPLOYEE" | "INTERN";

// The two-tier chat/WebSocket routing tag — deliberately NOT the same type
// as Role. See ChatUserController on the backend: this is a fixed
// "ADMIN"/"USER" routing convention independent of the six-tier business
// role hierarchy, and conflating the two broke WebSocket message delivery
// during Phase 1 frontend sync. Keeping them as distinct types here means
// TypeScript itself will flag it if anything ever tries to pass a Role
// where a ChatRoleTag is expected.
export type ChatRoleTag = "ADMIN" | "USER";

export interface User {
  id: number;
  empId: number | null;
  companyId: number;
  name: string;
  email: string;
  role: Role;
  department: string | null;
  salary: number | null;
  profileImageUrl: string | null;
  frozen: boolean;
  primaryOwner: boolean;
  createdAt: string;
  updatedAt: string;
}

// Shape of the employee create/update form payload — a subset of User's
// writable fields, no id/company/audit fields (server-assigned).
export interface EmployeeFormInput {
  empId: number | string;
  name: string;
  department: string;
  email: string;
  password: string;
  salary: number | string;
  role: Role;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page, 0-indexed
  size: number;
  first: boolean;
  last: boolean;
}

export interface AuthResponse {
  token: string;
  // Phase 2: access tokens are short-lived (15 min) — this is used to
  // silently renew them via /api/auth/refresh instead of forcing a
  // re-login every 15 minutes. See axiosInstance.ts.
  refreshToken: string;
  role: Role;
  name: string;
  email: string;
  id: number;
  empId: number | null;
  companyId: number;
  companyName: string | null;
  companySlug?: string | null;
  department: string | null;
  salary: number | null;
  primaryOwner: boolean | null;
  emailVerified: boolean | null;
}

export type LeaveStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface Leave {
  id: number;
  empId: number;
  employeeName?: string;
  // NOTE: the backend Leave entity has no leaveType column at all, despite
  // ml-service's /predict-leave requiring "leave_type" as an input feature.
  // The prediction endpoint is very likely non-functional as a result — see
  // docs/database.md. Kept optional here rather than removed, since the ML
  // response shape may reference it.
  leaveType?: string;
  startDate: string;
  endDate: string;
  reason: string;
  status: LeaveStatus;
  appliedDate?: string;
}

export type AttendanceStatus = "PRESENT" | "LATE" | "ABSENT" | "HALF_DAY" | "ON_LEAVE";

export interface Attendance {
  id: number;
  empId: number;
  date: string;
  checkIn: string | null;
  checkOut: string | null;
  status: AttendanceStatus;
  workHours: number | null;
  notes: string | null;
  markedBy: string | null;
}

export type TeamStatus = "ACTIVE" | "COMPLETED" | "ON_HOLD" | "CANCELLED";

export interface Team {
  id: number;
  name: string;
  description: string | null;
  projectName: string | null;
  deadline: string | null;
  status: TeamStatus;
  memberIds: number[];
  teamLeadId: number | null;
  createdDate: string | null;
}

export interface Notification {
  id: number;
  message: string;
  createdAt?: string;
  read?: boolean;
}

export interface ChatMessage {
  id?: number;
  senderId: number;
  senderRole: ChatRoleTag;
  receiverId: number;
  receiverRole: ChatRoleTag;
  message: string;
  timestamp?: string;
  status?: "SENT" | "DELIVERED" | "READ" | "DELETED";
  fileUrl?: string;
  reaction?: string | null;
  starred?: boolean;
}

// Row shape returned by GET /api/chat-users — role here is always the
// ChatRoleTag alias, not the six-tier Role (see backend ChatUserController).
export interface ChatUser {
  id: number;
  name: string;
  email: string;
  department: string | null;
  profileImageUrl: string | null;
  role: ChatRoleTag;
}

// ── Phase 4b: workspaces / company registration ───────────────────────────
export interface CompanyInfo {
  id: number;
  name: string;
  slug: string;
  createdAt: string;
  memberCount: number;
}

/** Public, minimal view returned by the "find your workspace" lookup. */
export interface WorkspaceLookup {
  name: string;
  slug: string;
}

export interface CompanyRegisterInput {
  companyName: string;
  ownerName: string;
  email: string;
  password: string;
}

// ── Phase 4c: audit trail and invitations ─────────────────────────────────
export interface AuditLogEntry {
  id: number;
  actorId: number | null;
  actorName: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  details: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AuditPage {
  content: AuditLogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InvitationItem {
  id: number;
  email: string;
  name: string;
  role: Role;
  department: string | null;
  status: "PENDING" | "ACCEPTED" | "REVOKED";
  expiresAt: string;
  createdAt: string;
  /** Only present on the response to "send": false when the mail server could not deliver the link. */
  emailSent?: boolean | null;
}

export interface InvitationPreview {
  companyName: string;
  companySlug: string;
  email: string;
  name: string;
  role: Role;
}

// ---- Phase 5a: projects and issues ------------------------------------------

export type ProjectStatus = "ACTIVE" | "ARCHIVED";
export type IssueType = "TASK" | "BUG" | "STORY";
export type IssueStatus = "TODO" | "IN_PROGRESS" | "IN_REVIEW" | "DONE";
export type IssuePriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export interface Project {
  id: number;
  key: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  leadEmpId: number | null;
  leadName: string | null;
  totalIssues: number;
  doneIssues: number;
  createdAt: string;
}

export interface Issue {
  id: number;
  key: string;
  projectId: number;
  projectKey: string;
  issueNumber: number;
  title: string;
  description: string | null;
  type: IssueType;
  status: IssueStatus;
  priority: IssuePriority;
  assigneeEmpId: number | null;
  assigneeName: string | null;
  reporterEmpId: number | null;
  reporterName: string | null;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface IssueComment {
  id: number;
  authorEmpId: number | null;
  authorName: string;
  body: string;
  createdAt: string;
}

export interface Person {
  empId: number;
  name: string;
  role: Role;
}

export interface IssueInput {
  title: string;
  description?: string;
  type: IssueType;
  priority: IssuePriority;
  assigneeEmpId?: number | null;
  dueDate?: string | null;
}


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
  permissions?: string[];
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
  /** LEAVE_REQUESTED, LEAVE_DECIDED, LEAVE_DELETED, or empty for older company-wide ones. */
  type?: string | null;
  leaveId?: number | null;
}

// ---- Team leave approval (Phase 8) ----
export interface TeamLeave {
  id: number;
  empId: number;
  employeeName: string;
  department: string | null;
  startDate: string;
  endDate: string;
  days: number;
  reason: string | null;
  status: LeaveStatus;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  /** Other people in the same group who are off on overlapping days. */
  clashesWith: string[];
  canDecide: boolean;
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
  aiEnabled: boolean;
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
  sprintId: number | null;
  sprintName: string | null;
  storyPoints: number | null;
  labels: string[];
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
  storyPoints?: number | null;
  labels?: string[];
}

export type SprintStatus = "PLANNED" | "ACTIVE" | "COMPLETED";

export interface Sprint {
  id: number;
  projectId: number;
  name: string;
  goal: string | null;
  status: SprintStatus;
  startDate: string | null;
  endDate: string | null;
  startedAt: string | null;
  completedAt: string | null;
  totalIssues: number;
  doneIssues: number;
  totalPoints: number;
  donePoints: number;
  completedCount: number | null;
  carriedOverCount: number | null;
}

export interface SprintInput {
  name: string;
  goal?: string;
  startDate?: string | null;
  endDate?: string | null;
}

// ---- Phase 6a: AI ----------------------------------------------------------

export interface AiStatus {
  enabled: boolean;
  provider: string;
  model: string;
  requestsLeftThisHour: number;
  companyRequestsLeftToday: number;
  /** false when an owner/admin switched AI off for the company. */
  companyEnabled: boolean;
}

export interface AiUsage {
  days: number;
  totalRequests: number;
  successful: number;
  failed: number;
  rateLimited: number;
  inputTokens: number;
  outputTokens: number;
  averageLatencyMs: number;
  byFeature: { feature: string; requests: number; inputTokens: number; outputTokens: number }[];
  byDay: { date: string; requests: number; failed: number }[];
  /** null when no price list is configured on the server. */
  estimatedCostUsd: number | null;
}

export type MeetingStatus = "DRAFT" | "APPROVED";

export interface MeetingListItem {
  id: number;
  title: string;
  meetingDate: string | null;
  status: MeetingStatus;
  actionItemCount: number;
  convertedCount: number;
  createdByName: string | null;
  createdAt: string;
}

export interface MeetingActionItem {
  id: number | null;            // null = a new item that is not saved yet
  title: string;
  description: string | null;
  ownerName: string | null;
  ownerEmpId: number | null;
  dueDate: string | null;
  priority: IssuePriority;
  issueId: number | null;
  issueKey: string | null;
}

export interface MeetingDetail {
  id: number;
  title: string;
  meetingDate: string | null;
  sourceRef: string | null;
  sourceText: string | null;
  summary: string;
  decisions: string[];
  openQuestions: string[];
  actionItems: MeetingActionItem[];
  status: MeetingStatus;
  projectId: number | null;
  model: string | null;
  promptVersion: string | null;
  createdByEmpId: number | null;
  createdByName: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MeetingCreateInput {
  title: string;
  meetingDate?: string | null;
  sourceRef?: string;
  notes: string;
}

export interface MeetingUpdateInput {
  title: string;
  meetingDate: string | null;
  summary: string;
  decisions: string[];
  openQuestions: string[];
  actionItems: {
    id: number | null; title: string; description?: string; ownerName?: string;
    ownerEmpId: number | null; dueDate: string | null; priority: IssuePriority;
  }[];
}

// ---- Phase 6c AI helpers ----------------------------------------------------

export interface SuggestedTask {
  title: string;
  description: string | null;
  type: IssueType;
  priority: IssuePriority;
  storyPoints: number | null;
  labels: string[];
  possibleDuplicate: boolean;
}
export interface TaskSuggestions { projectId: number; tasks: SuggestedTask[]; model: string; promptVersion: string; }
export interface TaskDraft {
  title: string; description?: string; type: IssueType; priority: IssuePriority; storyPoints: number | null; labels: string[];
}

export type CommunicationKind = "ANNOUNCEMENT" | "REMINDER" | "FEEDBACK" | "POLICY_UPDATE" | "WELCOME" | "THANK_YOU" | "OTHER";
export type CommunicationTone = "FORMAL" | "FRIENDLY" | "CONCISE" | "ENCOURAGING";
export interface CommunicationInput { kind: CommunicationKind; audience: string; tone: CommunicationTone; points: string; }
export interface CommunicationDraft { subject: string; body: string; model: string; promptVersion: string; }

export type ReportType = "STATUS_UPDATE" | "SPRINT_RETRO";
export interface ProjectReport {
  type: ReportType; projectKey: string; headline: string; summary: string;
  highlights: string[]; risks: string[]; nextSteps: string[]; basedOn: string[];
  model: string; promptVersion: string; generatedAt: string;
}

export interface HrAnswer { answer: string; factsUsed: string[]; model: string; promptVersion: string; }

// ---- Knowledge base (Phase 7) ----
export type KbAudience = "ALL_STAFF" | "MANAGEMENT";
export type KbStatus = "PROCESSING" | "READY" | "FAILED";

export interface KbDocument {
  id: number;
  title: string;
  fileName: string | null;
  audience: KbAudience;
  status: KbStatus;
  errorMessage: string | null;
  charCount: number;
  chunkCount: number;
  embeddingModel: string | null;
  /** Indexed with an older embedding model: re-index to make it searchable again. */
  stale: boolean;
  uploadedByName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface KbSource { n: number; documentId: number; title: string; snippet: string; score: number; }

export interface KbAnswer {
  found: boolean;
  answer: string;
  sources: KbSource[];
  model: string | null;
  promptVersion: string | null;
}

export interface KbServiceStatus {
  available: boolean;
  companyEnabled: boolean;
  vectorStore: string;
  embeddingModel: string;
  readyDocuments: number;
}

// ---- Documents (Phase 8b) ----
export type DocCategory = "POLICY" | "HANDBOOK" | "CONTRACT" | "FORM" | "PERSONAL" | "OTHER";
export type DocVisibility = "ALL_STAFF" | "MANAGEMENT" | "PRIVATE";

export interface StoredDocument {
  id: number;
  title: string;
  category: DocCategory;
  visibility: DocVisibility;
  ownerEmpId: number | null;
  ownerName: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedByName: string | null;
  indexedForAi: boolean;
  /** Why it is not searchable in the knowledge base, when that was asked for. */
  indexNote: string | null;
  canDelete: boolean;
  createdAt: string;
}

// ---- Analytics (Phase 8c) ----
export interface Count { label: string; value: number; }

export interface AnalyticsOverview {
  scope: "COMPANY" | "TEAM";
  headcount: number;
  byDepartment: Count[];
  byRole: Count[];
  leave: { pending: number; offToday: string[]; daysByMonth: Count[]; statusCounts: Count[] };
  attendance: {
    days: number; present: number; late: number; halfDay: number; absent: number; onLeave: number;
    presentRate: number | null; averageWorkHours: number | null;
  };
  projects: { issuesByStatus: Count[]; overdueIssues: number } | null;
}

export interface MySummary {
  leaveDaysApprovedThisYear: number;
  pendingLeaveRequests: number;
  presentRateLast30Days: number | null;
  openIssues: number;
  overdueIssues: number;
  unreadNotifications: number;
}

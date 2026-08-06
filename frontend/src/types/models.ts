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

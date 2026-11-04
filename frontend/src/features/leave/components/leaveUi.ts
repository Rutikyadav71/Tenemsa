import type { LeaveStatus } from "../../../shared/types/models";
import type { Tone } from "../../../shared/ui";

export const LEAVE_TONE: Record<LeaveStatus, Tone> = { PENDING: "warning", APPROVED: "success", REJECTED: "danger" };
export const LEAVE_LABEL: Record<LeaveStatus, string> = { PENDING: "Pending", APPROVED: "Approved", REJECTED: "Rejected" };
const day = (s: string) => new Date(`${s.slice(0, 10)}T00:00:00`);
export const leaveDays = (a: string, b: string) => Math.round((day(b).getTime() - day(a).getTime()) / 86_400_000) + 1;
export const prettyDay = (s: string) => day(s).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

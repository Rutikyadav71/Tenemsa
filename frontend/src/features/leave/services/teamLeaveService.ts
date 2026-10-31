import axiosInstance from "../../../shared/api/axiosInstance";
import type { TeamLeave } from "../../../shared/types/models";

export type TeamLeaveFilter = "PENDING" | "APPROVED" | "REJECTED" | "ALL";

export async function listTeamLeaves(status: TeamLeaveFilter): Promise<TeamLeave[]> {
  return (await axiosInstance.get<TeamLeave[]>("/api/leaves/team", { params: { status } })).data;
}

export async function decideLeave(id: number, decision: "approve" | "reject", note: string): Promise<void> {
  await axiosInstance.put(`/api/leaves/${id}/${decision}`, { note: note.trim() || null });
}

export function leaveErrorMessage(err: any, fallback: string): string {
  const data = err?.response?.data;
  const text = (data && typeof data === "object" ? data.message : data) || "";
  return typeof text === "string" && text ? text : fallback;
}

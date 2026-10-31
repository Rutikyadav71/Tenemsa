import axiosInstance from "../../../shared/api/axiosInstance";
import type {
  MeetingCreateInput, MeetingDetail, MeetingListItem, MeetingUpdateInput,
} from "../../../shared/types/models";

export async function listMeetings(): Promise<MeetingListItem[]> {
  return (await axiosInstance.get<MeetingListItem[]>("/api/meetings")).data;
}

export async function getMeeting(id: number): Promise<MeetingDetail> {
  return (await axiosInstance.get<MeetingDetail>(`/api/meetings/${id}`)).data;
}

/** Sends the notes to the AI model; can take several seconds, so the timeout is longer than usual. */
export async function generateMeeting(input: MeetingCreateInput): Promise<MeetingDetail> {
  return (await axiosInstance.post<MeetingDetail>("/api/meetings", input, { timeout: 90000 })).data;
}

export async function updateMeeting(id: number, input: MeetingUpdateInput): Promise<MeetingDetail> {
  return (await axiosInstance.put<MeetingDetail>(`/api/meetings/${id}`, input)).data;
}

export async function approveMeeting(id: number): Promise<MeetingDetail> {
  return (await axiosInstance.post<MeetingDetail>(`/api/meetings/${id}/approve`)).data;
}

export async function createIssuesFromMeeting(
  id: number, projectId: number, actionItemIds: number[],
): Promise<{ created: { actionItemId: number; issueId: number; issueKey: string }[] }> {
  return (await axiosInstance.post(`/api/meetings/${id}/create-issues`, { projectId, actionItemIds })).data;
}

export async function deleteMeeting(id: number): Promise<void> {
  await axiosInstance.delete(`/api/meetings/${id}`);
}

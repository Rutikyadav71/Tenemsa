import axiosInstance from "../../../shared/api/axiosInstance";

export type AudienceType = "ALL" | "ROLES" | "DEPARTMENTS" | "PEOPLE";

export interface AnnouncementInput {
  subject: string; body: string; audience: AudienceType;
  roles?: string[]; departments?: string[]; empIds?: number[]; sendEmail: boolean;
}
export interface Preview { count: number; sample: string[]; tooMany: boolean }
export interface AnnouncementRow {
  id: number; subject: string; body: string; audience: string; recipients: number; sentBy: string;
  sendEmail: boolean; emailsSent: number; emailsFailed: number; status: "SENDING" | "DONE"; createdAt: string;
}
export interface Paged<T> { content: T[]; page: number; totalPages: number; totalElements: number }
export interface PlatformNotice { id: number; title: string; message: string; severity: "INFO" | "WARNING" | "CRITICAL"; createdAt: string }

export const previewAudience = async (i: AnnouncementInput): Promise<Preview> => (await axiosInstance.post<Preview>("/api/announcements/preview", i)).data;
export const sendAnnouncement = async (i: AnnouncementInput) => (await axiosInstance.post<{ id: number; recipients: number; emailing: boolean }>("/api/announcements", i)).data;
export const getAnnouncements = async (page = 0): Promise<Paged<AnnouncementRow>> => (await axiosInstance.get<Paged<AnnouncementRow>>("/api/announcements", { params: { page, size: 8 } })).data;
export const getPlatformNotices = async (): Promise<PlatformNotice[]> => (await axiosInstance.get<PlatformNotice[]>("/api/announcements/platform")).data;

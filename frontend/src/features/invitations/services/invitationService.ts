import axiosInstance from "../../../shared/api/axiosInstance";
import type { AuthResponse, InvitationItem, InvitationPreview, Role } from "../../../shared/types/models";

export async function sendInvitation(input: { email: string; name: string; role: Role; department?: string }): Promise<InvitationItem> {
  const res = await axiosInstance.post<InvitationItem>("/api/invitations", input);
  return res.data;
}

export async function listInvitations(): Promise<InvitationItem[]> {
  const res = await axiosInstance.get<InvitationItem[]>("/api/invitations");
  return res.data;
}

export async function revokeInvitation(id: number): Promise<void> {
  await axiosInstance.delete(`/api/invitations/${id}`);
}

/** Public: used by the accept-invite page. Returns null when the link is invalid or expired. */
export async function previewInvitation(token: string): Promise<InvitationPreview | null> {
  try {
    const res = await axiosInstance.get<InvitationPreview>("/api/invitations/preview", { params: { token } });
    return res.data;
  } catch (err: any) {
    if (err.response?.status === 404) return null;
    throw err;
  }
}

export async function acceptInvitation(token: string, name: string, password: string): Promise<AuthResponse> {
  const res = await axiosInstance.post<AuthResponse>("/api/invitations/accept", { token, name, password });
  return res.data;
}

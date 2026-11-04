import axiosInstance from "../../../shared/api/axiosInstance";
import type { AiChatDetail, AiChatKind, AiChatSummary } from "../../../shared/types/models";

export async function listChats(kind: AiChatKind): Promise<AiChatSummary[]> {
  return (await axiosInstance.get<AiChatSummary[]>("/api/ai/conversations", { params: { kind } })).data;
}

export async function getChat(id: number): Promise<AiChatDetail> {
  return (await axiosInstance.get<AiChatDetail>(`/api/ai/conversations/${id}`)).data;
}

export async function renameChat(id: number, title: string): Promise<AiChatSummary> {
  return (await axiosInstance.patch<AiChatSummary>(`/api/ai/conversations/${id}`, { title })).data;
}

export async function deleteChat(id: number): Promise<void> {
  await axiosInstance.delete(`/api/ai/conversations/${id}`);
}

export async function deleteAllChats(kind: AiChatKind): Promise<void> {
  await axiosInstance.delete("/api/ai/conversations", { params: { kind } });
}

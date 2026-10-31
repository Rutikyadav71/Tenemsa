import axiosInstance from "../../../shared/api/axiosInstance";
import type { KbAnswer, KbAudience, KbDocument, KbServiceStatus } from "../../../shared/types/models";

const SLOW = { timeout: 90000 };

export async function kbStatus(): Promise<KbServiceStatus> {
  return (await axiosInstance.get<KbServiceStatus>("/api/kb/status")).data;
}

export async function listDocuments(): Promise<KbDocument[]> {
  return (await axiosInstance.get<KbDocument[]>("/api/kb/documents")).data;
}

export async function uploadDocument(file: File, title: string, audience: KbAudience): Promise<KbDocument> {
  const form = new FormData();
  form.append("file", file);
  if (title.trim()) form.append("title", title.trim());
  form.append("audience", audience);
  return (await axiosInstance.post<KbDocument>("/api/kb/documents", form, SLOW)).data;
}

export async function addTextDocument(title: string, audience: KbAudience, text: string): Promise<KbDocument> {
  return (await axiosInstance.post<KbDocument>("/api/kb/documents/text", { title: title.trim(), audience, text }, SLOW)).data;
}

export async function reindexDocument(id: number): Promise<KbDocument> {
  return (await axiosInstance.post<KbDocument>(`/api/kb/documents/${id}/reindex`, null, SLOW)).data;
}

export async function deleteDocument(id: number): Promise<void> {
  await axiosInstance.delete(`/api/kb/documents/${id}`);
}

export async function askKnowledge(question: string): Promise<KbAnswer> {
  return (await axiosInstance.post<KbAnswer>("/api/kb/ask", { question }, SLOW)).data;
}

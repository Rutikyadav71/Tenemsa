import axiosInstance from "../../../shared/api/axiosInstance";
import type { DocCategory, DocVisibility, StoredDocument } from "../../../shared/types/models";

export async function listDocuments(category?: DocCategory): Promise<StoredDocument[]> {
  return (await axiosInstance.get<StoredDocument[]>("/api/documents", { params: category ? { category } : {} })).data;
}

export interface UploadInput {
  file: File;
  title: string;
  category: DocCategory;
  visibility: DocVisibility;
  ownerEmpId?: number;
  indexForAi: boolean;
}

export async function uploadDocument(input: UploadInput): Promise<StoredDocument> {
  const form = new FormData();
  form.append("file", input.file);
  if (input.title.trim()) form.append("title", input.title.trim());
  form.append("category", input.category);
  form.append("visibility", input.visibility);
  if (input.visibility === "PRIVATE" && input.ownerEmpId) form.append("ownerEmpId", String(input.ownerEmpId));
  form.append("indexForAi", String(input.indexForAi));
  return (await axiosInstance.post<StoredDocument>("/api/documents", form, { timeout: 90000 })).data;
}

export async function deleteDocument(id: number): Promise<void> {
  await axiosInstance.delete(`/api/documents/${id}`);
}

/** Downloads through the authenticated client (the file needs the sign-in token), then saves it from memory. */
export async function downloadDocument(doc: StoredDocument): Promise<void> {
  const res = await axiosInstance.get<Blob>(`/api/documents/${doc.id}/download`, { responseType: "blob" });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = doc.fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

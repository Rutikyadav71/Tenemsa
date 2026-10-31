import SockJS from "sockjs-client";
import { Client, type IMessage } from "@stomp/stompjs";
import { API_BASE_URL } from "../../../shared/api/apiConfig";
import axiosInstance from "../../../shared/api/axiosInstance";
import type { ChatMessage, ChatRoleTag } from "../../../shared/types/models";

let stompClient: Client | null = null;
let isConnected = false;
let pendingMessages: ChatMessage[] = [];

export function connect(
  userId: number | string,
  role: ChatRoleTag,
  onMessage: (msg: ChatMessage) => void
): void {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    isConnected = false;
  }

  stompClient = new Client({
    webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
    reconnectDelay: 3000,

    // The server rejects any STOMP CONNECT without a valid access token (HTTP
    // security never sees frames inside an open WebSocket). Re-read the token
    // on every (re)connect attempt so a refreshed token is picked up.
    beforeConnect: () => {
      stompClient!.connectHeaders = {
        Authorization: `Bearer ${localStorage.getItem("token") ?? ""}`,
      };
    },

    onConnect: () => {
      isConnected = true;
      // Subscribe to this user's personal queue.
      // Key pattern: c{companyId}_{ROLE}_{userId}  e.g. "c1_ADMIN_1" or "c1_USER_101".
      // companyId is part of the key because "USER_101" exists in every
      // company — without it two tenants' employee 101 would share one queue.
      const companyId = localStorage.getItem("companyId") ?? "";
      const userQueue = `/user/c${companyId}_${role}_${userId}/queue/messages`;
      stompClient!.subscribe(userQueue, (frame: IMessage) => {
        try {
          const body: ChatMessage = JSON.parse(frame.body);
          onMessage(body);
        } catch (e) { console.error("WS parse error", e); }
      });

      // Flush any queued messages
      const queued = [...pendingMessages];
      pendingMessages = [];
      queued.forEach(m => stompClient!.publish({
        destination: "/app/chat.send",
        body: JSON.stringify(m),
      }));
    },

    onWebSocketClose: () => { isConnected = false; },
    onStompError: () => { isConnected = false; },
  });

  stompClient.activate();
}

/**
 * msg shape expected by ChatMessage entity:
 *   senderId, senderRole, receiverId, receiverRole, message
 * Note: field is "message" (not "content")!
 */
export function sendMessage(msg: ChatMessage): void {
  if (stompClient && isConnected) {
    stompClient.publish({
      destination: "/app/chat.send",
      body: JSON.stringify(msg),
    });
  } else {
    pendingMessages.push(msg);
  }
}

export function disconnect(): void {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    isConnected = false;
    pendingMessages = [];
  }
}

/* ── Attachments (Phase 8d) ─────────────────────────────────────────── */

export interface UploadedAttachment {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  image: boolean;
}

export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

/** A readable message from any failed request: never an object (that is what showed "[object Object]"). */
export function errorText(e: unknown, fallback = "Something went wrong. Please try again."): string {
  const err = e as { response?: { data?: unknown; status?: number }; message?: string };
  const data = err?.response?.data;
  if (typeof data === "string" && data.trim()) return data;
  if (data && typeof data === "object") {
    const m = (data as { message?: unknown }).message;
    if (typeof m === "string" && m.trim()) return m;
  }
  if (err?.response?.status === 413) return "That file is larger than 10 MB.";
  if (err?.response?.status === 403) return "You don't have permission to do that.";
  if (err && !err.response && err.message === "Network Error") return "Can't reach the server. Check your connection.";
  return fallback;
}

export async function uploadAttachment(file: File): Promise<UploadedAttachment> {
  const fd = new FormData();
  fd.append("file", file);
  const res = await axiosInstance.post<UploadedAttachment>("/api/chat/attachments", fd);
  return res.data;
}

/** The authenticated file as a Blob (an <img> or <a> cannot send the login token itself). */
export async function fetchAttachmentBlob(id: number): Promise<Blob> {
  const res = await axiosInstance.get<Blob>(`/api/chat/attachments/${id}`, { params: { download: true }, responseType: "blob" });
  return res.data;
}

/** Saves the exact stored file under the name it was sent with. */
export async function saveAttachment(id: number, fileName: string): Promise<void> {
  const blob = await fetchAttachmentBlob(id);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

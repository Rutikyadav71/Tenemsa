import SockJS from "sockjs-client";
import { Client, type IMessage } from "@stomp/stompjs";
import { API_BASE_URL } from "./apiConfig";
import type { ChatMessage, ChatRoleTag } from "../types/models";

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

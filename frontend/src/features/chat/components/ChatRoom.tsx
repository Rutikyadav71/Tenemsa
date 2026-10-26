import React, { useCallback, useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { sendMessage } from "../services/chatService";
import type { ChatMessage, ChatRoleTag, ChatUser } from "../../../shared/types/models";
import "../chat.css";

/* ── Helpers ─────────────────────────────────────────────────────────── */
const AV = ["#6366f1", "#2dd4bf", "#22c55e", "#f59e0b", "#ef4444", "#a78bfa", "#38bdf8", "#fb923c"];
const getColor = (n?: string | null) => AV[(n?.charCodeAt(0) || 0) % AV.length];
const getInitials = (n?: string | null) => n?.split(" ").map(x => x[0]).join("").toUpperCase().slice(0, 2) || "?";
const fmtSz = (b?: number) => { const n = Number(b) || 0; return n < 1024 ? n + "B" : n < 1048576 ? (n / 1024).toFixed(1) + "KB" : (n / 1048576).toFixed(1) + "MB"; };
const EMOJIS = ["😀", "😂", "😊", "😍", "🤔", "😎", "😭", "😡", "👍", "👎", "🙏", "🔥", "💯", "❤️", "✨", "🎉", "🎯", "⚡", "💡", "🚀", "😅", "🥳", "🤝", "👏", "💪", "😴", "😬", "🫡", "🙌", "🤑"];
const REACTIONS = ["👍", "❤️", "😂", "😮", "😢", "🙏"];

/* Icons */
const I = {
  send: <svg width="17" height="17" fill="none" viewBox="0 0 24 24"><line x1="22" y1="2" x2="11" y2="13" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" /><polygon points="22 2 15 22 11 13 2 9 22 2" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>,
  emoji: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" /><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>,
  imgIc: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8" /><circle cx="8.5" cy="8.5" r="1.5" stroke="currentColor" strokeWidth="1.6" /><polyline points="21 15 16 10 5 21" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>,
  fileIc: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><polyline points="14 2 14 8 20 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>,
  reply: <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><polyline points="9 17 4 12 9 7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /><path d="M20 18v-2a4 4 0 0 0-4-4H4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>,
  copy: <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2" stroke="currentColor" strokeWidth="1.8" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>,
  trash: <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M19 6l-1 14H6L5 6M10 11v6M14 11v6M9 6V4h6v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>,
  download: <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>,
  star: <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>,
  starF: <svg width="14" height="14" fill="#f59e0b" viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" stroke="#f59e0b" strokeWidth="1.8" /></svg>,
  info: <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8" /><path d="M12 8h.01M12 12v4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>,
  close: <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>,
  pdf: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" /><path d="M14 2v6h6M9 15h6M9 11h6M9 19h6" stroke="currentColor" strokeWidth="1.8" /></svg>,
  word: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" /><path d="M14 2v6h6M9 15h6M9 11h6" stroke="currentColor" strokeWidth="1.8" /></svg>,
  xls: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" /><path d="M14 2v6h6M8 12h8v8H8z" stroke="currentColor" strokeWidth="1.8" /></svg>,
  ppt: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" /><path d="M14 2v6h6" stroke="currentColor" strokeWidth="1.8" /><circle cx="12" cy="14" r="3" stroke="currentColor" strokeWidth="1.8" /></svg>,
  vid: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="14" rx="2" stroke="currentColor" strokeWidth="1.8" /><path d="M2 12h20M7 3v14M17 3v14M22 7l-5 5 5 5V7z" stroke="currentColor" strokeWidth="1.8" /></svg>,
  aud: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13" stroke="currentColor" strokeWidth="1.8" /><circle cx="6" cy="18" r="3" stroke="currentColor" strokeWidth="1.8" /><circle cx="18" cy="16" r="3" stroke="currentColor" strokeWidth="1.8" /></svg>,
  zip: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" /><path d="M14 2v6h6M10 12h4v4h-4zM12 8v4" stroke="currentColor" strokeWidth="1.8" /></svg>,
};

/* File type → color + label */
function getFileType(mime = "", name = "") {
  const ext = (name.split(".").pop() || "").toLowerCase();
  if (mime.includes("pdf") || ext === "pdf") return { color: "#dc2626", lbl: "PDF", icon: I.pdf };
  if (mime.includes("word") || ["doc", "docx"].includes(ext)) return { color: "#2563eb", lbl: "DOC", icon: I.word };
  if (mime.includes("sheet") || ["xls", "xlsx"].includes(ext)) return { color: "#16a34a", lbl: "XLS", icon: I.xls };
  if (mime.includes("presentation") || ["ppt", "pptx"].includes(ext)) return { color: "#ea580c", lbl: "PPT", icon: I.ppt };
  if (mime.startsWith("video/") || ["mp4", "mov", "avi"].includes(ext)) return { color: "#7c3aed", lbl: "VID", icon: I.vid };
  if (mime.startsWith("audio/") || ["mp3", "wav"].includes(ext)) return { color: "#0891b2", lbl: "AUD", icon: I.aud };
  if (["zip", "rar", "7z", "gz"].includes(ext)) return { color: "#854d0e", lbl: "ZIP", icon: I.zip };
  return { color: "#64748b", lbl: "FILE", icon: I.fileIc };
}

/* Parse file message — handles both old [FILE:name:url] and new [FILE:name:mime:size:url] */
interface ParsedFile { name: string; mime: string; size: number; url: string; }

function parseFile(text?: string): ParsedFile | null {
  if (!text?.startsWith("[FILE:")) return null;
  const inner = text.slice(6, text.length - 1);    // strip [FILE: and ]
  const httpsIdx = inner.indexOf("https://");
  if (httpsIdx < 0) return null;
  const url = inner.slice(httpsIdx);
  const meta = inner.slice(0, httpsIdx).replace(/:$/, ""); // trim trailing colon
  const parts = meta.split(":");
  const name = parts[0] || "file";
  const mime = parts.length >= 3 ? parts[1] : "application/octet-stream";
  const size = parts.length >= 3 ? Number(parts[2]) : 0;
  return { name, mime, size, url };
}

/* Download a file properly */
/* Download a file properly with blob approach to preserve filename/extension */
async function downloadFile(url: string, filename: string) {
  try {
    const res = await fetch(url);
    if (!res.ok) throw new Error("Fetch failed");
    const blob = await res.blob();
    const bUrl = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = bUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(bUrl);
  } catch {
    // Fallback if CORS or fetch fails
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.target = "_blank";
    a.rel = "noopener noreferrer";
    document.body.appendChild(a);
    a.click();
    setTimeout(() => document.body.removeChild(a), 100);
  }
}

/* File card component — WhatsApp styled */
function FileCard({ info, isMine }: { info: ParsedFile; isMine: boolean }) {
  const ft = getFileType(info.mime, info.name);
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12, minWidth: 210, maxWidth: 300,
      padding: "2px 0"
    }}>
      <div style={{
        width: 48, height: 48, borderRadius: 12, flexShrink: 0, display: "flex",
        alignItems: "center", justifyContent: "center",
        background: `${ft.color}1a`, border: `1px solid ${ft.color}33`,
        color: ft.color, boxShadow: `0 4px 12px ${ft.color}11`
      }}>
        {ft.icon}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 13.5, fontWeight: 600, color: isMine ? "white" : "var(--text-1)",
          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap"
        }}>
          {info.name}
        </div>
        <div style={{
          fontSize: 11, opacity: 0.7, marginTop: 2,
          color: isMine ? "rgba(255,255,255,0.8)" : "var(--text-3)",
          display: "flex", alignItems: "center", gap: 5
        }}>
          <span>{ft.lbl}</span>
          <span style={{ opacity: 0.5 }}>•</span>
          <span>{info.size ? fmtSz(info.size) : "File"}</span>
        </div>
      </div>
      <button onClick={() => downloadFile(info.url, info.name)}
        style={{
          width: 32, height: 32, borderRadius: "50%", border: "none", cursor: "pointer",
          display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0,
          background: isMine ? "rgba(255,255,255,0.15)" : "var(--bg-hover)",
          color: isMine ? "white" : "var(--text-2)", transition: "all .2s"
        }}
        onMouseEnter={e => { e.currentTarget.style.transform = "scale(1.1)"; e.currentTarget.style.background = isMine ? "rgba(255,255,255,0.25)" : "var(--border-bright)"; }}
        onMouseLeave={e => { e.currentTarget.style.transform = "scale(1)"; e.currentTarget.style.background = isMine ? "rgba(255,255,255,0.15)" : "var(--bg-hover)"; }}
        title="Download">
        {I.download}
      </button>
    </div>
  );
}

/* Message content — renders text / image / file correctly */
function MsgContent({ msg, isMine, msgRefs }: { msg: ChatMessage; isMine: boolean; msgRefs: React.MutableRefObject<Map<number, HTMLElement>> }) {
  const raw = msg.message || "";
  if (!raw || raw === "[deleted]") return <span style={{ fontStyle: "italic", opacity: 0.4, fontSize: 12 }}>Message deleted</span>;

  // Strip reply prefix to get the actual text
  let text = raw, replyFrom = "", replyPreview = "", replyToId: number | null = null;
  const rm = raw.match(/^\[REPLY:(.+?):(.+?):(\d+)?\](.+)/s);
  const rm2 = rm ? rm : raw.match(/^\[REPLY:(.+?):(.+?)\](.+)/s);
  if (rm) { replyFrom = rm[1]; replyPreview = rm[2]; replyToId = rm[3] ? Number(rm[3]) : null; text = rm[4]; }
  else if (rm2) { replyFrom = rm2[1]; replyPreview = rm2[2]; text = rm2[3]; }

  const isImg = text.match(/^https?:\/\/.+\.(jpg|jpeg|png|gif|webp)(\?.*)?$/i) ||
    (text.includes("res.cloudinary.com") && text.includes("/image/"));
  const fInfo = !isImg ? parseFile(text) : null;

  return (
    <>
      {replyFrom && (
        <div style={{
          background: "rgba(0,0,0,0.22)", borderLeft: `3px solid ${isMine ? "rgba(255,255,255,0.5)" : "var(--accent)"}`,
          borderRadius: 6, padding: "5px 8px", marginBottom: 7, cursor: "pointer"
        }}
          onClick={() => {
            if (replyToId && msgRefs?.current) {
              const el = msgRefs.current.get(replyToId);
              el?.scrollIntoView({ behavior: "smooth", block: "center" });
              // flash highlight
              el?.classList.add("msg-highlight");
              setTimeout(() => el?.classList.remove("msg-highlight"), 2000);
            }
          }}>
          <div style={{
            fontSize: 10, fontWeight: 700, marginBottom: 2,
            color: isMine ? "rgba(255,255,255,0.85)" : "var(--accent-light)"
          }}>{replyFrom}</div>
          <div style={{ fontSize: 11.5, opacity: 0.7, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: 230 }}>
            {replyPreview}
          </div>
        </div>
      )}
      {isImg ? (
        <img src={text} alt="image"
          style={{ maxWidth: "100%", maxHeight: 260, borderRadius: 10, display: "block", cursor: "pointer" }}
          onClick={() => window.open(text, "_blank")} />
      ) : fInfo ? (
        <FileCard info={fInfo} isMine={isMine} />
      ) : (
        <span style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{text}</span>
      )}
    </>
  );
}

/* Avatar */
function UAvatar({ user, size = 32, onClick }: { user?: ChatUser | null; size?: number; onClick?: () => void }) {
  const c = getColor(user?.name || "");
  return (
    <div style={{
      width: size, height: size, borderRadius: "50%", flexShrink: 0, overflow: "hidden",
      background: `${c}22`, color: c, display: "flex", alignItems: "center", justifyContent: "center",
      fontSize: size * 0.34, fontWeight: 700, fontFamily: "'Outfit',sans-serif",
      cursor: onClick ? "pointer" : "default"
    }}
      onClick={onClick}>
      {user?.profileImageUrl
        ? <img src={user.profileImageUrl} alt={user.name}
          style={{ width: "100%", height: "100%", objectFit: "cover", display: "block", borderRadius: "50%" }} />
        : getInitials(user?.name)
      }
    </div>
  );
}

/* Profile popup */
function ProfilePopup({ user, onClose }: { user?: ChatUser | null; onClose: () => void }) {
  const c = getColor(user?.name || "");
  return (
    <div className="ppbg" onClick={onClose}>
      <div className="pp" onClick={e => e.stopPropagation()}>
        <div className="pp-banner" style={{ background: `linear-gradient(135deg,${c}44,${c}18)` }} />
        <div className="pp-body">
          <div className="pp-av" style={{ background: user?.profileImageUrl ? "transparent" : `${c}22`, color: c }}>
            {user?.profileImageUrl
              ? <img src={user.profileImageUrl} alt={user.name}
                style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "50%" }} />
              : getInitials(user?.name)}
          </div>
          <div className="pp-name">{user?.name}</div>
          <div style={{ fontSize: 12, color: user?.role === "ADMIN" ? "var(--accent-light)" : "var(--text-3)", marginTop: 3 }}>
            {user?.role === "ADMIN" ? "Administrator" : `${user?.department || "Employee"} · #${user?.id}`}
          </div>
          <div style={{ marginTop: 16 }}>
            {([
              { l: "Role", v: user?.role === "ADMIN" ? "Admin" : "Employee" },
              user?.role !== "ADMIN" && { l: "Department", v: user?.department || "—" },
              user?.role !== "ADMIN" && { l: "Employee ID", v: `#${user?.id}` },
              { l: "Email", v: user?.email || "—" },
            ] as (false | { l: string; v: string })[]).filter((r): r is { l: string; v: string } => r !== false).map(r => (
              <div className="pp-row" key={r.l}>
                <span className="pp-lbl">{r.l}</span>
                <span className="pp-val">{r.v}</span>
              </div>
            ))}
          </div>
          <button onClick={onClose}
            style={{
              width: "100%", marginTop: 14, padding: "9px", background: "var(--bg-hover)",
              border: "1px solid var(--border-bright)", borderRadius: 8,
              color: "var(--text-2)", cursor: "pointer", fontSize: 13, fontWeight: 500
            }}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

/* Message Info popup — real data */
interface MsgInfoPopupProps {
  msg: ChatMessage; selectedUser?: ChatUser | null; currentUserId: number;
  currentUserRole: ChatRoleTag | null; onClose: () => void;
}

function MsgInfoPopup({ msg, selectedUser, currentUserId, currentUserRole, onClose }: MsgInfoPopupProps) {
  const isMine = Number(msg.senderId) === Number(currentUserId) && msg.senderRole === currentUserRole;
  const ts = msg.timestamp ? new Date(msg.timestamp) : null;
  const fmtTs = (t: Date | null) => t ? t.toLocaleString("en-IN", { weekday: "short", month: "short", day: "numeric", year: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—";
  const rawText = msg.message || "";
  let preview = rawText;
  if (rawText === "[deleted]") preview = "[deleted]";
  else if (rawText.startsWith("[FILE:")) { const f = parseFile(rawText); preview = f ? `📎 ${f.name}` : "[File]"; }
  else if (rawText.match(/^\[REPLY:/)) preview = rawText.replace(/^\[REPLY:.+?\]\n*/s, "").slice(0, 60);

  return (
    <div className="ppbg" onClick={onClose}>
      <div style={{
        background: "var(--bg-elevated)", border: "1px solid var(--border-bright)",
        borderRadius: 18, padding: 24, width: 310, maxWidth: "95vw", boxShadow: "0 20px 60px rgba(0,0,0,0.6)",
        animation: "popQ .18s ease"
      }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <div style={{
            fontSize: 16, fontWeight: 700, color: "var(--text-1)", fontFamily: "'Outfit',sans-serif",
            display: "flex", alignItems: "center", gap: 8
          }}>
            {I.info} Message Info
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", display: "flex" }}>{I.close}</button>
        </div>
        {[
          { l: "From", v: isMine ? "You (me)" : selectedUser?.name },
          { l: "To", v: isMine ? selectedUser?.name : "You (me)" },
          { l: "Sent at", v: fmtTs(ts) },
          { l: "Status", v: msg.status === "READ" ? "✓✓ Read" : msg.status === "DELETED" ? "🗑 Deleted" : msg.status === "SENT" ? "✓ Sent" : "✓✓ Delivered" },
          { l: "Reaction", v: msg.reaction || "None" },
          { l: "Starred", v: msg.starred ? "⭐ Yes" : "No" },
          { l: "Content", v: preview },
        ].map(r => (
          <div key={r.l} style={{
            display: "flex", justifyContent: "space-between", alignItems: "flex-start",
            padding: "9px 0", borderBottom: "1px solid var(--border)", fontSize: 13
          }}>
            <span style={{ color: "var(--text-3)", flexShrink: 0, marginRight: 8 }}>{r.l}</span>
            <span style={{
              color: "var(--text-1)", fontWeight: 500, textAlign: "right",
              maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis"
            }}>{r.v}</span>
          </div>
        ))}
        <button onClick={onClose}
          style={{
            width: "100%", marginTop: 14, padding: "9px", background: "var(--bg-hover)",
            border: "1px solid var(--border-bright)", borderRadius: 8,
            color: "var(--text-2)", cursor: "pointer", fontSize: 13, fontWeight: 500
          }}>
          Close
        </button>
      </div>
    </div>
  );
}

/* Starred messages side panel — fetches from backend */
interface StarredPanelProps {
  selectedUser?: ChatUser | null; currentUserId: number; currentUserRole: ChatRoleTag | null;
  onClose: () => void; onScrollTo: (id: number) => void;
}

function StarredPanel({ selectedUser, currentUserId, currentUserRole, onClose, onScrollTo }: StarredPanelProps) {
  const [starred, setStarred] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    if (!selectedUser) return;
    axiosInstance.get<ChatMessage[]>("/api/chat/starred", {
      params: { id1: currentUserId, role1: currentUserRole, id2: Number(selectedUser.id), role2: selectedUser.role }
    }).then(r => { setStarred(r.data || []); }).catch(() => { }).finally(() => setLoading(false));
  }, [selectedUser]);

  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 5000, background: "rgba(0,0,0,0.55)", backdropFilter: "blur(4px)" }} onClick={onClose}>
      <div style={{
        position: "absolute", right: 0, top: 0, bottom: 0, width: 340, maxWidth: "100vw",
        background: "var(--bg-elevated)", borderLeft: "1px solid var(--border)",
        display: "flex", flexDirection: "column", animation: "slideInR .22s ease"
      }}
        onClick={e => e.stopPropagation()}>
        <div style={{ padding: "14px 16px", borderBottom: "1px solid var(--border)", display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: 17 }}>{I.starF}</span>
          <span style={{ flex: 1, fontFamily: "'Outfit',sans-serif", fontSize: 15, fontWeight: 700, color: "var(--text-1)" }}>Starred Messages</span>
          <button onClick={onClose} style={{ background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", display: "flex" }}>{I.close}</button>
        </div>
        <div style={{ flex: 1, overflowY: "auto", padding: 12 }}>
          {loading ? <div style={{ textAlign: "center", padding: "30px 0", color: "var(--text-3)" }}>Loading...</div>
            : starred.length === 0 ? <div style={{ textAlign: "center", color: "var(--text-3)", fontSize: 13, padding: "40px 0" }}>No starred messages</div>
              : starred.map(m => {
                const isMine = Number(m.senderId) === Number(currentUserId) && m.senderRole === currentUserRole;
                const fi = parseFile(m.message);
                const preview = m.message === "[deleted]" ? "[deleted]" : fi ? `📎 ${fi.name}` : m.message?.replace(/^\[REPLY:.+?\]/s, "").slice(0, 80) || "";
                const ts = m.timestamp ? new Date(m.timestamp).toLocaleString("en-IN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "";
                return (
                  <div key={m.id} onClick={() => { onScrollTo(m.id!); onClose(); }}
                    style={{
                      padding: "10px 12px", background: "var(--bg-surface)", borderRadius: 10, marginBottom: 8,
                      border: "1px solid var(--border)", cursor: "pointer", transition: "background .15s"
                    }}
                    onMouseEnter={e => e.currentTarget.style.background = "var(--bg-elevated)"}
                    onMouseLeave={e => e.currentTarget.style.background = "var(--bg-surface)"}>
                    <div style={{ fontSize: 11, color: isMine ? "var(--accent-light)" : "var(--text-3)", marginBottom: 4, fontWeight: 600, display: "flex", justifyContent: "space-between" }}>
                      <span>{isMine ? "You" : selectedUser?.name}</span>
                      <span style={{ color: "var(--text-3)", fontWeight: 400 }}>{ts}</span>
                    </div>
                    <div style={{ fontSize: 13, color: "var(--text-1)" }}>{preview}</div>
                  </div>
                );
              })
          }
        </div>
      </div>
    </div>
  );
}

/* Context menu — all actions call backend */
interface ContextMenuProps {
  msg: ChatMessage; isMine: boolean; pos: { x: number; y: number }; onClose: () => void;
  onReply: () => void; onCopy: () => void; onDelete: () => void; onDownload: () => void;
  onReact: (emoji: string) => void; onStar: () => void; onInfo: () => void;
}

function ContextMenu({ msg, isMine, pos, onClose, onReply, onCopy, onDelete, onDownload, onReact, onStar, onInfo }: ContextMenuProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [style, setStyle] = useState({ left: pos.x, top: pos.y, opacity: 0 });
  const text = msg.message || "";
  const fInfo = parseFile(text);
  const isImg = !fInfo && (text.includes("res.cloudinary.com") || text.match(/^https?:\/\/.+\.(jpg|jpeg|png|gif|webp)/i));
  const deleted = msg.status === "DELETED" || text === "[deleted]";

  useEffect(() => {
    const h = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) onClose(); };
    setTimeout(() => document.addEventListener("mousedown", h), 0);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  useEffect(() => {
    if (!ref.current) return;
    const { width: mw, height: mh } = ref.current.getBoundingClientRect();
    const vw = window.innerWidth, vh = window.innerHeight;
    let x = pos.x, y = pos.y;
    if (x + mw > vw - 8) x = vw - mw - 8;
    if (y + mh > vh - 8) y = vh - mh - 8;
    if (x < 8) x = 8; if (y < 8) y = 8;
    setStyle({ left: x, top: y, opacity: 1 });
  }, [pos]);

  return (
    <>
      <div className="ctx-bd" onClick={onClose} />
      <div ref={ref} className="ctx-menu" style={{ position: "fixed", ...style }}>
        {/* Emoji reactions */}
        {!deleted && (
          <div className="ctx-reacts">
            {REACTIONS.map(r => (
              <button key={r} className={`ctx-r${msg.reaction === r ? " active" : ""}`}
                onClick={() => { onReact(r); onClose(); }} title={`React with ${r}`}>{r}</button>
            ))}
          </div>
        )}

        {!deleted && <div className="ctx-i" onClick={() => { onReply(); onClose(); }}>{I.reply} Reply</div>}
        {!deleted && !fInfo && !isImg && <div className="ctx-i" onClick={() => { onCopy(); onClose(); }}>{I.copy} Copy text</div>}
        {(fInfo || isImg) && !deleted && <div className="ctx-i" onClick={() => { onDownload(); onClose(); }}>{I.download} Download</div>}
        {!deleted && (
          <div className="ctx-i" onClick={() => { onStar(); onClose(); }}>
            {msg.starred ? I.starF : I.star} {msg.starred ? "Unstar message" : "Star message"}
          </div>
        )}
        {<div className="ctx-i" onClick={() => { onInfo(); onClose(); }}>{I.info} Info</div>}
        {isMine && !deleted && <><div className="ctx-sep" /><div className="ctx-i danger" onClick={() => { onDelete(); onClose(); }}>{I.trash} Delete</div></>}
      </div>
    </>
  );
}

/* ── Main ChatRoom ───────────────────────────────────────────────────── */
interface ChatRoomProps {
  currentUserId: number;
  currentUserRole: ChatRoleTag | null;
  selectedUser: ChatUser | null;
  incomingMessage: ChatMessage | null;
  onMessageSent?: () => void;
}

export default function ChatRoom({ currentUserId, currentUserRole, selectedUser, incomingMessage, onMessageSent }: ChatRoomProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [showEmoji, setShowEmoji] = useState(false);
  const [sending, setSending] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null);
  const [ctx, setCtx] = useState<{ msg: ChatMessage; pos: { x: number; y: number } } | null>(null);
  const [infoMsg, setInfoMsg] = useState<ChatMessage | null>(null);
  const [showProfile, setShowProfile] = useState(false);
  const [showStarred, setShowStarred] = useState(false);

  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const emojiRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const msgRefs = useRef(new Map<number, HTMLElement>()); // id → DOM element

  const fetchHistory = useCallback(async () => {
    if (!selectedUser) return;
    try {
      const res = await axiosInstance.get<ChatMessage[]>("/api/chat/history", {
        params: { id1: currentUserId, role1: currentUserRole, id2: Number(selectedUser.id), role2: selectedUser.role }
      });
      setMessages(res.data || []);
      await axiosInstance.put("/api/chat/read", null, {
        params: { senderId: Number(selectedUser.id), senderRole: selectedUser.role, receiverId: currentUserId, receiverRole: currentUserRole }
      }).catch(() => { });
    } catch (e) { console.error(e); }
  }, [selectedUser, currentUserId, currentUserRole]);

  useEffect(() => { if (!selectedUser) return; setMessages([]); setReplyTo(null); fetchHistory(); }, [selectedUser]);
  useEffect(() => { if (incomingMessage) fetchHistory(); }, [incomingMessage]);
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages]);

  useEffect(() => {
    const h = (e: MouseEvent) => { if (emojiRef.current && !emojiRef.current.contains(e.target as Node)) setShowEmoji(false); };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  /* Send text message */
  const doSend = async (text: string) => {
    if (!text?.trim() || !selectedUser || sending) return;
    setSending(true);
    try {
      let t = text;
      if (replyTo) {
        const rt = replyTo.message?.startsWith("[FILE:") ? "[File]" : (replyTo.message || "").slice(0, 60);
        const rName = Number(replyTo.senderId) === Number(currentUserId) ? "You" : selectedUser.name;
        // Include original message ID for scroll-to on click
        t = `[REPLY:${rName}:${rt}:${replyTo.id || ""}]${text}`;
        setReplyTo(null);
      }
      sendMessage({ senderId: currentUserId, senderRole: currentUserRole!, receiverId: Number(selectedUser.id), receiverRole: selectedUser.role, message: t });
      setInput(""); setShowEmoji(false);
      setTimeout(() => { fetchHistory(); onMessageSent?.(); }, 400);
    } finally { setSending(false); inputRef.current?.focus(); }
  };

  /* Upload file via plain axios (no interceptor Content-Type conflict) */
  const handleUpload = async (file: File) => {
    if (!file || !selectedUser) return;
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append("file", file);
      fd.append("sender", `${currentUserRole}_${currentUserId}`);
      const res = await axiosInstance.post<{url:string; isImage:boolean; fileName:string; fileSize:number; mimeType:string}>("/api/upload/chat", fd);
      const { url, isImage, fileName, fileSize, mimeType } = res.data;
      const msgText = isImage ? url : `[FILE:${fileName}:${mimeType || "application/octet-stream"}:${fileSize || 0}:${url}]`;
      await doSend(msgText);
    } catch (e: any) { alert(e.response?.data || "Upload failed. Check Cloudinary config."); }
    finally { setUploading(false); }
  };

  /* Delete — calls backend soft-delete */
  const handleDelete = async (id?: number) => {
    try {
      await axiosInstance.delete(`/api/chat/message/${id}`);
      setMessages(p => p.map(m => m.id === id ? { ...m, message: "[deleted]", status: "DELETED" as const } : m));
    } catch (e) { console.error(e); }
  };

  /* Reaction — calls backend, updates state from response */
  const handleReact = async (msgId: number | undefined, emoji: string) => {
    try {
      const res = await axiosInstance.put(`/api/chat/message/${msgId}/react`, { reaction: emoji });
      setMessages(p => p.map(m => m.id === msgId ? { ...m, reaction: res.data.reaction || null } : m));
    } catch (e) { console.error(e); }
  };

  /* Star — calls backend */
  const handleStar = async (msgId?: number) => {
    try {
      const res = await axiosInstance.put(`/api/chat/message/${msgId}/star`);
      setMessages(p => p.map(m => m.id === msgId ? { ...m, starred: res.data.starred } : m));
    } catch (e) { console.error(e); }
  };

  const handleCopy = (text?: string) => {
    const t = (text || "").replace(/^\[REPLY:.+?\]/s, "");
    if (t && !t.startsWith("[")) navigator.clipboard?.writeText(t).catch(() => { });
  };

  const handleDownload = (msg: ChatMessage) => {
    const text = msg.message || "";
    const fi = parseFile(text);
    if (fi) downloadFile(fi.url, fi.name);
    else if (text.startsWith("http")) window.open(text, "_blank");
  };

  const openCtx = (e: ReactMouseEvent, msg: ChatMessage) => {
    e.preventDefault(); e.stopPropagation();
    setCtx({ msg, pos: { x: e.clientX, y: e.clientY } });
  };

  const scrollToMsg = (id: number) => {
    const el = msgRefs.current.get(Number(id));
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "center" });
      el.classList.add("msg-highlight");
      setTimeout(() => el.classList.remove("msg-highlight"), 2000);
    }
  };

  const groupByDate = (msgs: ChatMessage[]) => {
    const g: Record<string, ChatMessage[]> = {};
    msgs.forEach(m => {
      const d = m.timestamp ? new Date(m.timestamp).toLocaleDateString("en-IN", { weekday: "short", month: "short", day: "numeric" }) : "Today";
      if (!g[d]) g[d] = [];
      g[d].push(m);
    });
    return g;
  };

  if (!selectedUser) return (
    <div className="chat-empty">
      <svg width="60" height="60" fill="none" viewBox="0 0 24 24" style={{ opacity: 0.1 }}>
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <div style={{ fontSize: 15, fontWeight: 500, color: "var(--text-2)" }}>Select a conversation</div>
      <div style={{ fontSize: 12, color: "var(--text-3)" }}>Choose someone to start chatting</div>
    </div>
  );

  const grouped = groupByDate(messages);
  const isAdminU = selectedUser.role === "ADMIN";

  return (
    <>
      {/* Header */}
      <div className="chat-room-header" onClick={() => setShowProfile(true)}>
        <UAvatar user={selectedUser} size={40} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-1)" }}>{selectedUser.name}</div>
          <div style={{ fontSize: 11, color: isAdminU ? "var(--accent-light)" : "var(--text-3)" }}>
            {isAdminU ? "Administrator" : `${selectedUser.department || ""}${selectedUser.department ? " · " : ""}Employee #${selectedUser.id}`}
          </div>
        </div>
        <div style={{ display: "flex", gap: 6, flexShrink: 0 }} onClick={e => e.stopPropagation()}>
          <button onClick={() => setShowStarred(true)} title="Starred messages"
            style={{
              background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", padding: 6, display: "flex",
              borderRadius: 6, transition: "all .15s"
            }}
            onMouseEnter={e => { e.currentTarget.style.color = "#f59e0b"; e.currentTarget.style.background = "var(--bg-elevated)"; }}
            onMouseLeave={e => { e.currentTarget.style.color = "var(--text-3)"; e.currentTarget.style.background = "none"; }}>
            {I.star}
          </button>
        </div>
        <div style={{ fontSize: 10, color: "var(--text-3)" }}>tap for info</div>
      </div>

      {/* Messages */}
      <div className="chat-messages">
        {messages.length === 0 && (
          <div style={{ textAlign: "center", color: "var(--text-3)", fontSize: 13, padding: "40px 0" }}>Start the conversation!</div>
        )}
        {Object.entries(grouped).map(([date, msgs]) => (
          <React.Fragment key={date}>
            <div className="chat-day-div"><span>{date}</span></div>
            {msgs.map((m, idx) => {
              const isMine = Number(m.senderId) === Number(currentUserId) && m.senderRole === currentUserRole;
              const deleted = m.status === "DELETED" || m.message === "[deleted]";
              const showAv = !isMine && (idx === 0 || msgs[idx - 1]?.senderId !== m.senderId || msgs[idx - 1]?.senderRole !== m.senderRole);
              const showTm = idx === msgs.length - 1 || msgs[idx + 1]?.senderId !== m.senderId || msgs[idx + 1]?.senderRole !== m.senderRole;
              const text = m.message || "";
              const isImg = !deleted && (text.includes("res.cloudinary.com") || text.match(/^https?:\/\/.+\.(jpg|jpeg|png|gif|webp)(\?.*)?$/i));
              const isFile = !deleted && text.startsWith("[FILE:");

              return (
                <div key={m.id || idx}
                  id={`msg-${m.id}`}
                  ref={el => { if (m.id && el) msgRefs.current.set(Number(m.id), el); }}
                  className={`msg-row ${isMine ? "mine" : "them"}`}
                  style={{ marginBottom: showTm ? 7 : 1 }}>

                  {/* Other user avatar */}
                  {!isMine && (showAv
                    ? <UAvatar user={selectedUser} size={28} onClick={() => setShowProfile(true)} />
                    : <div style={{ width: 28, flexShrink: 0 }} />
                  )}

                  {/* ⋮ button — flex sibling, not absolute */}
                  <button className="msg-opts-btn"
                    style={{ order: isMine ? 0 : 2, alignSelf: "flex-end", marginBottom: showTm ? 22 : 6 }}
                    onClick={e => openCtx(e, m)} title="Options">
                    ⋮
                  </button>

                  {/* Bubble */}
                  <div className="msg-wrap" style={{ order: 1 }}>
                    <div
                      className={`msg-bubble ${isMine ? "mine" : "them"}${deleted ? " msg-deleted" : ""}${isImg ? " img-bubble" : ""}${isFile ? " file-bubble" : ""}${m.starred ? " starred" : ""}`}
                      onContextMenu={e => openCtx(e, m)}>
                      {m.starred && !deleted && (
                        <span style={{ position: "absolute", top: -8, right: 8, fontSize: 11 }}>⭐</span>
                      )}
                      <MsgContent msg={m} isMine={isMine} msgRefs={msgRefs} />
                      {showTm && (
                        <div className="msg-meta">
                          <span className="msg-time">
                            {m.timestamp ? new Date(m.timestamp).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" }) : ""}
                          </span>
                          {isMine && !deleted && (
                            <span className="msg-ticks"
                              style={{ color: m.status === "READ" ? "#53bdeb" : "rgba(255,255,255,0.42)" }}>
                              {m.status === "READ" ? "✓✓" : "✓"}
                            </span>
                          )}
                        </div>
                      )}
                    </div>
                    {/* Reaction badge — shows who reacted */}
                    {m.reaction && (
                      <div className="msg-react"
                        onClick={() => handleReact(m.id, m.reaction!)}
                        title="Click to remove reaction">
                        <span>{m.reaction}</span>
                        <span className="cnt">1</span>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </React.Fragment>
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Upload bar */}
      {uploading && (
        <div className="upload-bar">
          <div style={{ width: 13, height: 13, border: "2px solid rgba(99,102,241,0.3)", borderTopColor: "var(--accent)", borderRadius: "50%", animation: "spin .7s linear infinite" }} />
          Uploading...
        </div>
      )}

      {/* Reply preview bar */}
      {replyTo && (
        <div className="reply-bar">
          <div className="reply-bar-line" />
          <div style={{ flex: 1 }}>
            <div className="reply-bar-name">
              {Number(replyTo.senderId) === Number(currentUserId) ? "You" : selectedUser.name}
            </div>
            <div className="reply-bar-text">
              {replyTo.message?.startsWith("[FILE:") ? "📎 File attachment" : (replyTo.message || "").replace(/^\[REPLY:.+?\]/s, "").slice(0, 80)}
            </div>
          </div>
          <button onClick={() => setReplyTo(null)}
            style={{ background: "none", border: "none", color: "var(--text-3)", cursor: "pointer", padding: 4, display: "flex" }}>
            {I.close}
          </button>
        </div>
      )}

      {/* Input area */}
      <div className="chat-input-area">
        <input ref={imgRef} type="file" accept="image/*" style={{ display: "none" }} onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f); e.target.value = ""; }} />
        <input ref={fileRef} type="file" style={{ display: "none" }} onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f); e.target.value = ""; }} />

        {showEmoji && (
          <div ref={emojiRef} className="emoji-wrap">
            <div className="emoji-grid">
              {EMOJIS.map(em => <button key={em} className="emoji-btn" onClick={() => setInput(v => v + em)}>{em}</button>)}
            </div>
          </div>
        )}

        <button className="cia-btn" style={{ color: showEmoji ? "var(--accent-light)" : undefined }}
          onClick={() => setShowEmoji(v => !v)} title="Emoji">{I.emoji}</button>
        <button className="cia-btn" onClick={() => imgRef.current?.click()} title="Send image" disabled={uploading}>{I.imgIc}</button>
        <button className="cia-btn" onClick={() => fileRef.current?.click()} title="Send file" disabled={uploading}>{I.fileIc}</button>

        <textarea ref={inputRef} className="chat-input" rows={1}
          value={input} onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); doSend(input); } }}
          placeholder={`Message ${selectedUser.name}...`} disabled={uploading} />

        <button className="chat-send-btn" onClick={() => doSend(input)}
          disabled={!input.trim() || sending || uploading}>{I.send}</button>
      </div>

      {/* Context menu */}
      {ctx && (
        <ContextMenu
          msg={ctx.msg}
          isMine={Number(ctx.msg.senderId) === Number(currentUserId) && ctx.msg.senderRole === currentUserRole}
          pos={ctx.pos}
          onClose={() => setCtx(null)}
          onReply={() => setReplyTo(ctx.msg)}
          onCopy={() => handleCopy(ctx.msg.message)}
          onDelete={() => handleDelete(ctx.msg.id)}
          onDownload={() => handleDownload(ctx.msg)}
          onReact={emoji => handleReact(ctx.msg.id, emoji)}
          onStar={() => handleStar(ctx.msg.id)}
          onInfo={() => setInfoMsg(ctx.msg)}
        />
      )}

      {/* Info popup */}
      {infoMsg && (
        <MsgInfoPopup msg={infoMsg} selectedUser={selectedUser}
          currentUserId={currentUserId} currentUserRole={currentUserRole}
          onClose={() => setInfoMsg(null)} />
      )}

      {/* Profile popup */}
      {showProfile && <ProfilePopup user={selectedUser} onClose={() => setShowProfile(false)} />}

      {/* Starred messages panel */}
      {showStarred && (
        <StarredPanel
          selectedUser={selectedUser}
          currentUserId={currentUserId}
          currentUserRole={currentUserRole}
          onClose={() => setShowStarred(false)}
          onScrollTo={scrollToMsg} />
      )}

      <style>{`
        @keyframes spin{to{transform:rotate(360deg)}}
        @keyframes slideInR{from{transform:translateX(100%)}to{transform:translateX(0)}}
        @keyframes popQ{from{opacity:0;transform:scale(.93)}to{opacity:1;transform:scale(1)}}
        .msg-highlight{animation:msgFlash 2s ease;}
        @keyframes msgFlash{0%,100%{background:transparent}20%,80%{background:rgba(99,102,241,0.25)}}
      `}</style>
    </>
  );
}

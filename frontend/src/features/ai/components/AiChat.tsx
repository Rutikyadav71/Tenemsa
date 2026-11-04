import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from "react";
import { LuCheck, LuCopy, LuHistory, LuMessageSquarePlus, LuPencil, LuRefreshCw, LuSend, LuSparkles, LuTrash2 } from "react-icons/lu";
import { Alert, Button, Field, Input, Modal, Skeleton, useConfirm, useToast } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";
import type { AiChatKind, AiChatSummary } from "../../../shared/types/models";
import { deleteAllChats, deleteChat, getChat, listChats, renameChat } from "../services/chatHistoryService";
import "./aiChat.css";

export interface ChatAnswer { conversationId: number | null; answer: string; details?: unknown }

interface Msg { key: string; role: "USER" | "ASSISTANT"; content: string; details?: unknown; state?: "pending" | "error"; retry?: string }

interface Props {
  kind: AiChatKind;
  title: string;
  subtitle: string;
  placeholder: string;
  welcomeTitle: string;
  welcomeText: string;
  suggestions: string[];
  ask: (question: string, conversationId: number | null) => Promise<ChatAnswer>;
  /** When set, the composer is disabled and this explains why. */
  blocked?: { tone: "error" | "info" | "warning"; text: string } | null;
  note?: ReactNode;
  renderDetails?: (details: unknown) => ReactNode;
  onAnswered?: () => void;
}

const MAX = 500;
let seq = 0;
const uid = () => `m${++seq}`;

function groupLabel(iso: string): string {
  const d = new Date(iso); const now = new Date();
  const day = (x: Date) => Math.floor(new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime() / 86400000);
  const diff = day(now) - day(d);
  return diff <= 0 ? "Today" : diff === 1 ? "Yesterday" : diff < 7 ? "Previous 7 days" : "Older";
}

/**
 * One chat screen for every conversational Tenemsa AI feature: a saved-chats list, a message thread that can be
 * continued later (the server gives the AI the earlier turns), suggested questions, copy, retry and clear errors.
 */
export default function AiChat({ kind, title, subtitle, placeholder, welcomeTitle, welcomeText, suggestions, ask, blocked, note, renderDetails, onAnswered }: Props) {
  const toast = useToast();
  const confirmAction = useConfirm();
  const [chats, setChats] = useState<AiChatSummary[] | null>(null);
  const [chatsFailed, setChatsFailed] = useState(false);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [msgs, setMsgs] = useState<Msg[]>([]);
  const [opening, setOpening] = useState(false);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [drawer, setDrawer] = useState(false);
  const [renaming, setRenaming] = useState<AiChatSummary | null>(null);
  const [newName, setNewName] = useState("");
  const [copied, setCopied] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const boxRef = useRef<HTMLTextAreaElement>(null);
  const reqRef = useRef(0);

  const refresh = useCallback(async () => {
    try { setChats(await listChats(kind)); setChatsFailed(false); } catch { setChatsFailed(true); setChats(c => c ?? []); }
  }, [kind]);
  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" }); }, [msgs]);

  const grow = (el: HTMLTextAreaElement | null) => { if (!el) return; el.style.height = "auto"; el.style.height = Math.min(el.scrollHeight, 140) + "px"; };
  useEffect(() => grow(boxRef.current), [text]);

  const newChat = () => { reqRef.current++; setActiveId(null); setMsgs([]); setText(""); setBusy(false); setDrawer(false); boxRef.current?.focus(); };

  const open = async (id: number) => {
    setDrawer(false);
    if (id === activeId && msgs.length) return;
    const mine = ++reqRef.current;
    setOpening(true);
    try {
      const chat = await getChat(id);
      if (mine !== reqRef.current) return;
      setActiveId(chat.id);
      setMsgs(chat.messages.map(m => ({ key: `s${m.id}`, role: m.role, content: m.content, details: m.details })));
      setBusy(false);
    } catch (e) {
      toast.error(errorMessage(e, "We couldn't open that chat."));
      void refresh();
    } finally { if (mine === reqRef.current) setOpening(false); }
  };

  const send = async (raw: string, retryKey?: string) => {
    const q = raw.trim();
    if (q.length < 3 || busy || blocked) return;
    const mine = ++reqRef.current;
    setBusy(true);
    const pendingKey = uid();
    setMsgs(m => {
      const base = retryKey ? m.filter(x => x.key !== retryKey) : [...m, { key: uid(), role: "USER" as const, content: q }];
      return [...base, { key: pendingKey, role: "ASSISTANT" as const, content: "", state: "pending" as const }];
    });
    if (!retryKey) setText("");
    try {
      const res = await ask(q, activeId);
      if (mine !== reqRef.current) return;
      if (res.conversationId) setActiveId(res.conversationId);
      setMsgs(m => m.map(x => x.key === pendingKey ? { key: pendingKey, role: "ASSISTANT", content: res.answer, details: res.details } : x));
      onAnswered?.();
      void refresh();
    } catch (e) {
      if (mine !== reqRef.current) return;
      setMsgs(m => m.map(x => x.key === pendingKey ? { key: pendingKey, role: "ASSISTANT", content: errorMessage(e, "We couldn't get an answer. Please try again."), state: "error", retry: q } : x));
    } finally { if (mine === reqRef.current) { setBusy(false); boxRef.current?.focus(); } }
  };

  const onSubmit = (e: FormEvent) => { e.preventDefault(); void send(text); };
  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); void send(text); }
  };

  const copy = async (m: Msg) => {
    try { await navigator.clipboard.writeText(m.content); setCopied(m.key); setTimeout(() => setCopied(c => (c === m.key ? null : c)), 1600); }
    catch { toast.error("Copying isn't available in this browser."); }
  };

  const remove = async (c: AiChatSummary) => {
    if (!(await confirmAction({ title: `Delete "${c.title}"?`, message: "This chat is removed for good.", confirmLabel: "Delete chat", tone: "danger" }))) return;
    try { await deleteChat(c.id); if (c.id === activeId) newChat(); toast.success("Chat deleted."); void refresh(); }
    catch (e) { toast.error(errorMessage(e, "We couldn't delete that chat.")); }
  };

  const clearAll = async () => {
    if (!(await confirmAction({ title: "Delete all chats?", message: "Every saved chat in this list is removed for good.", confirmLabel: "Delete all", tone: "danger" }))) return;
    try { await deleteAllChats(kind); newChat(); toast.success("All chats deleted."); void refresh(); }
    catch (e) { toast.error(errorMessage(e, "We couldn't delete the chats.")); }
  };

  const saveName = async (e: FormEvent) => {
    e.preventDefault();
    if (!renaming || !newName.trim()) return;
    try { await renameChat(renaming.id, newName.trim()); setRenaming(null); void refresh(); }
    catch (err) { toast.error(errorMessage(err, "We couldn't rename that chat.")); }
  };

  const grouped = useMemo(() => {
    const out: { label: string; items: AiChatSummary[] }[] = [];
    for (const c of chats ?? []) {
      const label = groupLabel(c.updatedAt);
      const g = out.find(x => x.label === label);
      if (g) g.items.push(c); else out.push({ label, items: [c] });
    }
    return out;
  }, [chats]);

  const history = (
    <nav className="ai-hist" aria-label="Saved chats">
      <Button variant="primary" icon={<LuMessageSquarePlus size={16} />} onClick={newChat} style={{ width: "100%" }}>New chat</Button>
      <div className="ai-hist-scroll">
        {chats === null ? <div style={{ display: "grid", gap: 10, padding: 4 }}>{[0, 1, 2].map(i => <Skeleton key={i} height={34} radius={8} />)}</div>
          : chatsFailed ? <div className="ai-hist-note">Your chats couldn't be loaded. <button className="ai-link" onClick={() => void refresh()}>Try again</button></div>
          : chats.length === 0 ? <div className="ai-hist-note">Your conversations will be saved here, so you can come back and continue them.</div>
          : grouped.map(g => (
            <div key={g.label}>
              <div className="ai-hist-group">{g.label}</div>
              <ul>
                {g.items.map(c => (
                  <li key={c.id} className={c.id === activeId ? "is-active" : ""}>
                    <button className="ai-hist-item" onClick={() => void open(c.id)} aria-current={c.id === activeId ? "true" : undefined} title={c.title}>{c.title}</button>
                    <span className="ai-hist-tools">
                      <button aria-label={`Rename ${c.title}`} onClick={() => { setRenaming(c); setNewName(c.title); }}><LuPencil size={14} /></button>
                      <button aria-label={`Delete ${c.title}`} onClick={() => void remove(c)}><LuTrash2 size={14} /></button>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
      </div>
      {chats && chats.length > 1 && <button className="ai-link ai-clear" onClick={() => void clearAll()}>Delete all chats</button>}
    </nav>
  );

  const empty = msgs.length === 0 && !opening;

  return (
    <div className="ai-page">
      <div className="ai-top">
        <div><h1 className="ai-title">{title}</h1><p className="ai-sub">{subtitle}</p></div>
        <Button className="ai-hist-btn" icon={<LuHistory size={16} />} onClick={() => setDrawer(true)}>History</Button>
      </div>

      <div className="ai-shell">
        <aside className="ai-side">{history}</aside>

        <section className="ai-main" aria-label={title}>
          {blocked && <div style={{ padding: "12px 16px 0" }}><Alert tone={blocked.tone}>{blocked.text}</Alert></div>}

          <div className="ai-thread" role="log" aria-live="polite" aria-busy={busy}>
            {opening && <div style={{ display: "grid", gap: 12, padding: 8 }}><Skeleton height={44} radius={12} width="55%" /><Skeleton height={70} radius={12} width="80%" /></div>}
            {empty && (
              <div className="ai-welcome">
                <span className="ai-welcome-icon" aria-hidden="true"><LuSparkles size={26} /></span>
                <h2>{welcomeTitle}</h2>
                <p>{welcomeText}</p>
                <div className="ai-chips">
                  {suggestions.map(s => <button key={s} className="ai-chip" disabled={!!blocked || busy} onClick={() => void send(s)}>{s}</button>)}
                </div>
              </div>
            )}
            {msgs.map(m => m.role === "USER" ? (
              <div key={m.key} className="ai-row ai-row-user"><div className="ai-bubble ai-user">{m.content}</div></div>
            ) : (
              <div key={m.key} className="ai-row">
                <span className="ai-avatar" aria-hidden="true"><LuSparkles size={15} /></span>
                <div className={`ai-bubble ai-bot${m.state === "error" ? " is-error" : ""}`}>
                  {m.state === "pending" ? <span className="ai-typing" role="status" aria-label="Tenemsa AI is thinking"><i /><i /><i /></span>
                    : <>
                      <div className="ai-text">{m.content}</div>
                      {m.state !== "error" && renderDetails?.(m.details)}
                      <div className="ai-actions">
                        {m.state === "error"
                          ? <Button size="sm" icon={<LuRefreshCw size={14} />} onClick={() => void send(m.retry ?? "", m.key)} disabled={busy}>Try again</Button>
                          : <button className="ai-act" onClick={() => void copy(m)} aria-label="Copy answer">{copied === m.key ? <><LuCheck size={14} /> Copied</> : <><LuCopy size={14} /> Copy</>}</button>}
                      </div>
                    </>}
                </div>
              </div>
            ))}
            <div ref={endRef} />
          </div>

          <form className="ai-composer" onSubmit={onSubmit}>
            <textarea ref={boxRef} rows={1} maxLength={MAX} value={text} onChange={e => setText(e.target.value)} onKeyDown={onKey}
              placeholder={placeholder} aria-label="Your message" disabled={!!blocked} />
            <span className={`ai-count${text.length > MAX - 60 ? " is-near" : ""}`}>{text.length > MAX - 120 ? `${text.length}/${MAX}` : ""}</span>
            <Button type="submit" variant="primary" aria-label="Send" icon={<LuSend size={16} />} disabled={busy || !!blocked || text.trim().length < 3} />
          </form>
          <div className="ai-note">{note ?? "Tenemsa AI can make mistakes. Check important details."}</div>
        </section>
      </div>

      <Modal open={drawer} drawer title="Chat history" onClose={() => setDrawer(false)}>{history}</Modal>

      <Modal open={!!renaming} title="Rename chat" onClose={() => setRenaming(null)} size="sm"
        footer={<><Button onClick={() => setRenaming(null)}>Cancel</Button><Button variant="primary" type="submit" form="chat-rename" disabled={!newName.trim()}>Save</Button></>}>
        <form id="chat-rename" onSubmit={saveName}>
          <Field label="Name"><Input data-autofocus maxLength={80} value={newName} onChange={e => setNewName(e.target.value)} /></Field>
        </form>
      </Modal>
    </div>
  );
}

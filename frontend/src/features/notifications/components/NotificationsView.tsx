import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { LuBellOff, LuCheckCheck } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import type { Notification } from "../../../shared/types/models";
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, SkeletonList, useToast } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";
import EmailToggle from "./EmailToggle";

/** Where a notification leads, depending on its kind and what this person is allowed to open. */
function destination(n: Notification, base: string, admin: boolean, empId: string | null): string | null {
  if (n.type === "LEAVE_REQUESTED") return can("leave:view:any") && admin ? `${base}/leaves` : can("leave:approve:team") ? `${base}/team-leaves` : `${base}/dashboard`;
  if (n.type === "LEAVE_DECIDED") return admin ? `${base}/apply-leave` : n.leaveId && empId ? `${base}/leaves/employee/${empId}?leaveId=${n.leaveId}` : `${base}/apply-leave`;
  if (n.type === "DOCUMENT_SHARED") return `${base}/documents`;
  if (n.leaveId && empId && !admin) return `${base}/leaves/employee/${empId}?leaveId=${n.leaveId}`;
  return null;
}

const when = (iso?: string) => iso ? new Date(iso).toLocaleString(undefined, { weekday: "short", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }) : "";

export default function NotificationsView({ workspace }: { workspace: "admin" | "user" }) {
  const navigate = useNavigate();
  const toast = useToast();
  const base = `/${workspace}`;
  const empId = localStorage.getItem("empId");
  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    try { setItems((await axiosInstance.get<Notification[]>("/api/notifications")).data || []); }
    catch { setFailed(true); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const open = async (n: Notification) => {
    if (!n.read) {
      setItems(prev => prev.map(x => x.id === n.id ? { ...x, read: true } : x));
      axiosInstance.put(`/api/notifications/${n.id}/read`).catch(() => setItems(prev => prev.map(x => x.id === n.id ? { ...x, read: false } : x)));
    }
    const to = destination(n, base, workspace === "admin", empId);
    if (to) navigate(to);
  };

  const markAll = async () => {
    try { await axiosInstance.put("/api/notifications/mark-all-read"); setItems(prev => prev.map(x => ({ ...x, read: true }))); }
    catch (e) { toast.fail(e, "We couldn't mark them as read."); }
  };

  const unread = items.filter(n => !n.read).length;

  return (
    <div style={{ maxWidth: 820 }}>
      <PageHeader title="Notifications" subtitle={loading ? undefined : unread ? `${unread} unread` : "You're all caught up"}
        actions={unread > 0 ? <Button icon={<LuCheckCheck size={16} />} onClick={() => void markAll()}>Mark all as read</Button> : undefined} />
      <div style={{ marginBottom: 14 }}><EmailToggle /></div>
      <Card pad={false}>
        {loading ? <div style={{ padding: 20 }}><SkeletonList rows={4} /></div>
          : failed ? <ErrorState message="Your notifications couldn't be loaded." onRetry={() => { setLoading(true); void load(); }} />
          : items.length === 0 ? <EmptyState icon={<LuBellOff size={24} />} title="No notifications yet" text="Leave requests, decisions and shared documents will show up here." />
          : <ul className="tn-list">{items.map(n => (
              <li key={n.id}>
                <button type="button" onClick={() => void open(n)} className="nt-row" aria-label={`${n.read ? "" : "Unread: "}${n.message}`}>
                  <span className="nt-dot" data-unread={!n.read} aria-hidden="true" />
                  <span style={{ flex: 1, minWidth: 0, textAlign: "left" }}>
                    <span className="nt-msg" data-unread={!n.read}>{n.message}</span>
                    {n.createdAt && <span className="nt-time">{when(n.createdAt)}</span>}
                  </span>
                  {!n.read && <Badge tone="accent">New</Badge>}
                </button>
              </li>))}
          </ul>}
      </Card>
      <style>{`
        .nt-row{all:unset;box-sizing:border-box;display:flex;gap:12px;align-items:flex-start;width:100%;padding:14px 20px;cursor:pointer;}
        .nt-row:hover{background:var(--hairline);} .nt-row:focus-visible{outline:2px solid var(--accent);outline-offset:-2px;}
        .nt-dot{width:8px;height:8px;border-radius:50%;margin-top:6px;flex:none;background:var(--border-bright);} .nt-dot[data-unread="true"]{background:var(--accent);}
        .nt-msg{display:block;font-size:13.5px;color:var(--text-2);overflow-wrap:anywhere;} .nt-msg[data-unread="true"]{color:var(--text-1);font-weight:500;}
        .nt-time{display:block;font-size:12px;color:var(--text-3);margin-top:3px;}
      `}</style>
    </div>
  );
}

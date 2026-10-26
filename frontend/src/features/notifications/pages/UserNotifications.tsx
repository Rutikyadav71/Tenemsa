import { useEffect, useState } from "react";
import axiosInstance from "../services/axiosInstance";
import { useNavigate } from "react-router-dom";
import type { Notification } from "../types/models";

const BellIcon = () => <svg width="28" height="28" fill="none" viewBox="0 0 24 24"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>;

export default function UserNotifications() {
  const empId    = localStorage.getItem("empId");
  const navigate = useNavigate();

  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading,       setLoading]       = useState(true);

  useEffect(() => {
    if (empId) fetchNotifications();
    else setLoading(false);
  }, [empId]);

  const fetchNotifications = async () => {
    try {
      const res  = await axiosInstance.get<Notification[]>("/api/notifications");
      const mine = res.data.filter(n =>
        n.message.toLowerCase().includes("leave") &&
        n.message.includes(`Emp ID: ${empId}`)
      );
      setNotifications(mine);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  };

  const handleClick = async (n: Notification) => {
    try {
      await axiosInstance.put("/api/notifications/mark-all-read");
      setNotifications(prev => prev.map(x => ({ ...x, read: true })));
      const match = n.message.match(/Leave ID:\s*(\d+)/);
      navigate(match ? `/user/leaves/employee/${empId}?leaveId=${match[1]}` : `/user/leaves/employee/${empId}`);
    } catch (e) { console.error(e); }
  };

  const unread = notifications.filter(n => !n.read).length;

  const RowSkeleton = () => (
    <div style={{ padding: "14px 20px", borderBottom: "1px solid var(--border)", display: "flex", gap: 12 }}>
      <div className="skeleton" style={{ width: 8, height: 8, borderRadius: "50%", marginTop: 5, flexShrink: 0 }} />
      <div style={{ flex: 1 }}>
        <div className="skeleton" style={{ width: "70%", height: 13, borderRadius: 4, marginBottom: 6 }} />
        <div className="skeleton" style={{ width: "40%", height: 11, borderRadius: 4 }} />
      </div>
    </div>
  );

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Notifications</div>
          <div className="page-sub">{unread > 0 ? `${unread} unread updates` : "All caught up"}</div>
        </div>
        {unread > 0 && (
          <button
            className="btn-ems btn-secondary-ems"
            style={{ fontSize: 12 }}
            onClick={async () => {
              await axiosInstance.put("/api/notifications/mark-all-read").catch(() => {});
              fetchNotifications();
            }}
          >
            Mark all read
          </button>
        )}
      </div>

      <div className="ems-card" style={{ padding: 0 }}>
        {loading ? (
          Array(5).fill(0).map((_, i) => <RowSkeleton key={i} />)
        ) : notifications.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 0", color: "var(--text-3)" }}>
            <div style={{ display: "flex", justifyContent: "center", marginBottom: 12, opacity: 0.3 }}><BellIcon /></div>
            <div style={{ fontSize: 14 }}>No leave notifications yet</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>You'll be notified when your leave status changes</div>
          </div>
        ) : (
          notifications.map((n, i) => (
            <div
              key={n.id || i}
              onClick={() => handleClick(n)}
              style={{
                padding: "14px 20px",
                borderBottom: i < notifications.length - 1 ? "1px solid var(--border)" : "none",
                cursor: "pointer",
                display: "flex", alignItems: "flex-start", gap: 12,
                background: !n.read ? "rgba(99,102,241,0.03)" : "transparent",
                transition: "background 0.2s",
              }}
              onMouseEnter={e => e.currentTarget.style.background = "var(--bg-elevated)"}
              onMouseLeave={e => e.currentTarget.style.background = !n.read ? "rgba(99,102,241,0.03)" : "transparent"}
            >
              <div style={{
                width: 8, height: 8, borderRadius: "50%", marginTop: 5, flexShrink: 0,
                background: !n.read ? "var(--accent)" : "var(--text-3)"
              }} />
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13.5, color: n.read ? "var(--text-2)" : "var(--text-1)", fontWeight: n.read ? 400 : 500 }}>
                  {n.message}
                </div>
                {n.createdAt && (
                  <div style={{ fontSize: 11, color: "var(--text-3)", marginTop: 3 }}>
                    {new Date(n.createdAt).toLocaleString("en-IN", { weekday: "short", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                  </div>
                )}
              </div>
              {!n.read && <span className="badge badge-blue" style={{ flexShrink: 0 }}>New</span>}
            </div>
          ))
        )}
      </div>
    </div>
  );
}

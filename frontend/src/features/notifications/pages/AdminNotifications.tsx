import { useEffect, useState } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import type { Notification } from "../../../shared/types/models";

const BellIcon = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;

const RowSkeleton = () => (
  <div style={{padding:"14px 20px",borderBottom:"1px solid var(--border)"}}>
    <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}}>
      <div style={{flex:1}}><div className="skeleton" style={{width:"70%",height:13,borderRadius:4,marginBottom:6}}/><div className="skeleton" style={{width:"40%",height:11,borderRadius:4}}/></div>
      <div className="skeleton" style={{width:50,height:18,borderRadius:99}}/>
    </div>
  </div>
);

export default function AdminNotifications() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { fetchNotifications(); }, []);

  const fetchNotifications = async () => {
    try {
      const res = await axiosInstance.get<Notification[]>("/api/notifications");
      setNotifications(res.data);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  const markAllRead = async () => {
    await axiosInstance.put("/api/notifications/mark-all-read").catch(()=>{});
    fetchNotifications();
  };

  const unread = notifications.filter(n => !n.read).length;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Notifications</div>
          <div className="page-sub">{unread > 0 ? `${unread} unread` : "All caught up"}</div>
        </div>
        {unread > 0 && (
          <button className="btn-ems btn-secondary-ems" onClick={markAllRead} style={{fontSize:12}}>
            Mark all read
          </button>
        )}
      </div>

      <div className="ems-card" style={{padding:0}}>
        {loading ? Array(6).fill(0).map((_,i) => <RowSkeleton key={i}/>) :
          notifications.length === 0 ? (
            <div style={{textAlign:"center",padding:"60px 0",color:"var(--text-3)"}}>
              <div style={{display:"flex",justifyContent:"center",marginBottom:12,opacity:0.3}}><BellIcon/></div>
              <div style={{fontSize:14}}>No notifications yet</div>
            </div>
          ) :
          notifications.map((n, i) => (
            <div key={n.id || i} style={{
              padding:"14px 20px",
              borderBottom: i < notifications.length - 1 ? "1px solid var(--border)" : "none",
              background: !n.read ? "rgba(99,102,241,0.03)" : "transparent",
              display:"flex", alignItems:"flex-start", gap:12,
              transition:"background 0.2s"
            }}>
              <div style={{width:8,height:8,borderRadius:"50%",background:n.read ? "var(--text-3)" : "var(--accent)",marginTop:5,flexShrink:0}}/>
              <div style={{flex:1}}>
                <div style={{fontSize:13.5,color: n.read ? "var(--text-2)" : "var(--text-1)",fontWeight: n.read ? 400 : 500}}>{n.message}</div>
                {n.createdAt && <div style={{fontSize:11,color:"var(--text-3)",marginTop:3}}>{new Date(n.createdAt).toLocaleString("en-IN",{weekday:"short",month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"})}</div>}
              </div>
              {!n.read && <span className="badge badge-blue" style={{flexShrink:0}}>New</span>}
            </div>
          ))
        }
      </div>
    </div>
  );
}

import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import axiosInstance from "../services/axiosInstance";
import type { Notification } from "../types/models";

const pageTitles: Record<string, string> = {
  "/admin/dashboard":"Dashboard","/admin/employees":"Employees","/admin/add":"Add Employee",
  "/admin/leaves":"Leave Management","/admin/apply-leave":"Apply Leave","/admin/attendance":"Attendance",
  "/admin/teams":"Teams","/admin/chat":"Chat Room","/admin/notifications":"Notifications",
  "/admin/profile":"Profile","/admin/manage-admins":"Admin Control",
};

const MenuIcon     = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M3 12h18M3 6h18M3 18h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const CollapseIcon = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M11 19l-7-7 7-7M18 19l-7-7 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const ExpandIcon   = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M13 5l7 7-7 7M6 5l7 7-7 7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const BellIcon     = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const ChevronIcon  = () => <svg width="12" height="12" fill="none" viewBox="0 0 24 24"><path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const LogoutIcon   = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;

function SignOutModal({ onConfirm, onCancel }: { onConfirm: () => void; onCancel: () => void }) {
  return (
    <div className="ems-modal-backdrop" onClick={onCancel}>
      <div className="ems-modal" style={{ width:340 }} onClick={e=>e.stopPropagation()}>
        <div style={{ textAlign:"center", padding:"8px 0 16px" }}>
          <div style={{ width:48,height:48,borderRadius:"50%",background:"var(--red-glow)",
            display:"flex",alignItems:"center",justifyContent:"center",margin:"0 auto 14px" }}>
            <LogoutIcon/>
          </div>
          <div style={{ fontSize:17,fontWeight:700,color:"var(--text-1)",marginBottom:6 }}>Sign out?</div>
          <div style={{ fontSize:13,color:"var(--text-3)",lineHeight:1.6 }}>You will be returned to the login page.</div>
        </div>
        <div style={{ display:"flex",gap:10 }}>
          <button className="btn-ems btn-secondary-ems" style={{ flex:1,justifyContent:"center" }} onClick={onCancel}>Cancel</button>
          <button className="btn-ems btn-danger-ems" style={{ flex:1,justifyContent:"center" }} onClick={onConfirm}>
            <LogoutIcon/> Sign Out
          </button>
        </div>
      </div>
    </div>
  );
}

interface AdminNavbarProps {
  toggleSidebar?: () => void;
  toggleCollapsed?: () => void;
  sidebarCollapsed?: boolean;
}

export default function AdminNavbar({ toggleSidebar, toggleCollapsed, sidebarCollapsed }: AdminNavbarProps) {
  const navigate  = useNavigate();
  const location  = useLocation();
  const name      = localStorage.getItem("adminName") || "Admin";
  const isMain    = localStorage.getItem("primaryOwner") === "true";
  const initials  = name.split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2);
  const imgUrl    = localStorage.getItem("adminProfileImageUrl") || null;
  const title     = pageTitles[location.pathname] || "Dashboard";
  const isMobile  = () => window.innerWidth <= 768;

  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount,   setUnreadCount]   = useState(0);
  const [showNotifs,    setShowNotifs]    = useState(false);
  const [showUser,      setShowUser]      = useState(false);
  const [showSignout,   setShowSignout]   = useState(false);
  const notifRef = useRef<HTMLDivElement>(null);
  const userRef  = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchNotifications();
    const iv = setInterval(fetchNotifications, 30000);
    return () => clearInterval(iv);
  }, []);

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) setShowNotifs(false);
      if (userRef.current  && !userRef.current.contains(e.target as Node))  setShowUser(false);
    };
    document.addEventListener("mousedown", h);
    return () => document.removeEventListener("mousedown", h);
  }, []);

  const fetchNotifications = async () => {
    try {
      const res = await axiosInstance.get<Notification[]>("/api/notifications");
      setNotifications(res.data || []);
      setUnreadCount((res.data || []).filter(n => !n.read).length);
    } catch {}
  };

  const toggleBell = async () => {
    setShowNotifs(v => !v); setShowUser(false);
    if (unreadCount > 0) {
      await axiosInstance.put("/api/notifications/mark-all-read").catch(() => {});
      fetchNotifications();
    }
  };

  // On mobile use toggleSidebar (overlay); on desktop use toggleCollapsed (shrink)
  const handleSidebarBtn = () => {
    if (isMobile()) toggleSidebar?.();
    else toggleCollapsed?.();
  };

  return (
    <>
      <header className="ems-navbar">
        {/* Mobile: hamburger | Desktop: collapse arrows */}
        <button className="navbar-hamburger" onClick={handleSidebarBtn}
          title="Toggle sidebar">
          {isMobile() ? <MenuIcon/> : (sidebarCollapsed ? <ExpandIcon/> : <CollapseIcon/>)}
        </button>

        <span className="navbar-title">{title}</span>

        <div className="navbar-actions">
          {/* Bell */}
          <div style={{ position:"relative" }} ref={notifRef}>
            <button className="navbar-btn" onClick={toggleBell}>
              <BellIcon/>
              {unreadCount>0 && <span className="navbar-badge">{unreadCount>9?"9+":unreadCount}</span>}
            </button>
            {showNotifs && (
              <div className="navbar-dropdown">
                <div className="dropdown-header">
                  <span>Notifications</span>
                  <Link to="/admin/notifications" style={{ fontSize:11,color:"var(--accent-light)" }}
                    onClick={()=>setShowNotifs(false)}>View all</Link>
                </div>
                {notifications.length===0
                  ? <div style={{ padding:"20px 16px",textAlign:"center",fontSize:13,color:"var(--text-3)" }}>No notifications</div>
                  : notifications.slice(0,6).map((n,i)=>(
                    <div key={i} className="notif-item">
                      <div className={`notif-msg${!n.read?" unread":""}`}>{n.message}</div>
                      {n.createdAt && <div className="notif-time">{new Date(n.createdAt).toLocaleString("en-IN",{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"})}</div>}
                    </div>
                  ))
                }
              </div>
            )}
          </div>

          {/* User menu */}
          <div style={{ position:"relative" }} ref={userRef}>
            <button className="navbar-user-btn" onClick={()=>{ setShowUser(v=>!v); setShowNotifs(false); }}>
              {imgUrl
                ? <img src={imgUrl} alt={name} style={{ width:28,height:28,borderRadius:"50%",objectFit:"cover" }}/>
                : <div className="sidebar-avatar" style={{ width:28,height:28,fontSize:11 }}>{initials}</div>
              }
              <span className="navbar-user-name">{name}</span>
              {isMain && <span className="badge badge-blue" style={{ fontSize:9,padding:"1px 6px" }}>Main</span>}
              <ChevronIcon/>
            </button>
            {showUser && (
              <div className="navbar-dropdown" style={{ width:210 }}>
                <Link to="/admin/profile" className="notif-item"
                  style={{ display:"block",color:"var(--text-2)",fontSize:13 }}
                  onClick={()=>setShowUser(false)}>Profile Settings</Link>
                <Link to="/admin/manage-admins" className="notif-item"
                  style={{ display:"block",color:"var(--text-2)",fontSize:13 }}
                  onClick={()=>setShowUser(false)}>Admin Control</Link>
                <div className="divider" style={{ margin:"4px 16px" }}/>
                <div className="notif-item"
                  style={{ cursor:"pointer",color:"var(--red)",fontSize:13,display:"flex",alignItems:"center",gap:8 }}
                  onClick={()=>{ setShowUser(false); setShowSignout(true); }}>
                  <LogoutIcon/> Sign Out
                </div>
              </div>
            )}
          </div>
        </div>
      </header>

      {showSignout && (
        <SignOutModal
          onConfirm={()=>{ localStorage.clear(); navigate("/"); }}
          onCancel={()=>setShowSignout(false)}
        />
      )}
    </>
  );
}

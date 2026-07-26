import React, { useState } from "react";
import { Link, useLocation } from "react-router-dom";

const ic = {
  dashboard:  <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/><rect x="14" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/><rect x="3" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/><rect x="14" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/></svg>,
  leave:      <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M14 2v6h6M12 18v-6M9 15h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  attendance: <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><polyline points="9 12 11 14 15 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  teams:      <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  chat:       <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  notif:      <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  profile:    <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/></svg>,
  logout:     <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
};

const navItems = [
  { to:"/user/dashboard",     label:"Dashboard",    icon:ic.dashboard },
  { to:"/user/apply-leave",   label:"Apply Leave",  icon:ic.leave },
  { to:"/user/attendance",    label:"Attendance",   icon:ic.attendance },
  { to:"/user/teams",         label:"My Teams",     icon:ic.teams },
  { to:"/user/chat",          label:"Chat Room",    icon:ic.chat },
  { to:"/user/notifications", label:"Notifications",icon:ic.notif },
];

const AV_COLORS = ["#6366f1","#2dd4bf","#22c55e","#f59e0b","#ef4444","#a78bfa"];

export default function UserSidebar({ isOpen, collapsed, toggleSidebar }) {
  const location   = useLocation();
  const name       = localStorage.getItem("name") || "Employee";
  const dept       = localStorage.getItem("department") || "Team";
  const profileImg = localStorage.getItem("profileImageUrl") || null;
  const initials   = name.split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2);
  const color      = AV_COLORS[(name.charCodeAt(0)||0) % AV_COLORS.length];
  const [showSignout, setShowSignout] = useState(false);

  const NavItem = ({ item }) => {
    const active = location.pathname === item.to;
    return (
      <Link to={item.to}
        className={`sidebar-item${active?" active":""}`}
        title={collapsed ? item.label : undefined}
        style={{ justifyContent:collapsed?"center":undefined, padding:collapsed?"10px":undefined }}
        onClick={() => window.innerWidth<768 && toggleSidebar?.()}>
        {item.icon}
        {!collapsed && <span>{item.label}</span>}
      </Link>
    );
  };

  return (
    <>
      <div className={`ems-sidebar${collapsed?" collapsed":""}`}>
        <div className="sidebar-logo" style={{ justifyContent:collapsed?"center":undefined }}>
          <div className="sidebar-logo-icon" style={{ flexShrink:0 }}>
            <svg width="18" height="18" fill="none" viewBox="0 0 24 24">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
                stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          {!collapsed && <div><div className="sidebar-logo-text">EMS</div><div className="sidebar-logo-sub">Employee Portal</div></div>}
        </div>

        {!collapsed && <div className="sidebar-section-label">Main Menu</div>}
        <nav className="sidebar-nav">
          {navItems.map(item => <NavItem key={item.to} item={item}/>)}
        </nav>

        {!collapsed && <div className="sidebar-section-label">Account</div>}
        <div className="sidebar-footer">
          <NavItem item={{ to:"/user/profile", label:"Profile", icon:ic.profile }}/>
          <div className="sidebar-user"
            style={{ justifyContent:collapsed?"center":undefined, padding:collapsed?"8px 10px":undefined, cursor:"default" }}>
            {profileImg
              ? <img src={profileImg} alt={name} title={collapsed?name:undefined}
                  style={{ width:30,height:30,borderRadius:"50%",objectFit:"cover",
                    flexShrink:0,border:"1px solid var(--border-bright)" }}/>
              : <div className="sidebar-avatar" title={collapsed?name:undefined}
                  style={{ background:`${color}22`,color,flexShrink:0 }}>{initials}</div>
            }
            {!collapsed && (
              <>
                <div className="sidebar-user-info">
                  <div className="sidebar-user-name">{name}</div>
                  <div className="sidebar-user-role">{dept}</div>
                </div>
                <button onClick={()=>setShowSignout(true)} title="Sign out"
                  style={{ background:"none",border:"none",color:"var(--text-3)",cursor:"pointer",
                    padding:4,borderRadius:4,display:"flex",alignItems:"center",flexShrink:0,transition:"color .15s" }}
                  onMouseEnter={e=>e.currentTarget.style.color="var(--red)"}
                  onMouseLeave={e=>e.currentTarget.style.color="var(--text-3)"}>
                  {ic.logout}
                </button>
              </>
            )}
          </div>
          {collapsed && (
            <button onClick={()=>setShowSignout(true)} title="Sign out"
              className="sidebar-item"
              style={{ justifyContent:"center",padding:"10px",border:"none",cursor:"pointer",
                width:"100%",marginTop:4,color:"var(--text-3)",background:"transparent" }}
              onMouseEnter={e=>{e.currentTarget.style.color="var(--red)";e.currentTarget.style.background="var(--red-glow)";}}
              onMouseLeave={e=>{e.currentTarget.style.color="var(--text-3)";e.currentTarget.style.background="transparent";}}>
              {ic.logout}
            </button>
          )}
        </div>
      </div>

      {showSignout && (
        <div className="ems-modal-backdrop" onClick={()=>setShowSignout(false)}>
          <div className="ems-modal" style={{ width:340 }} onClick={e=>e.stopPropagation()}>
            <div style={{ textAlign:"center",padding:"8px 0 16px" }}>
              <div style={{ width:48,height:48,borderRadius:"50%",background:"var(--red-glow)",
                display:"flex",alignItems:"center",justifyContent:"center",margin:"0 auto 14px" }}>
                {ic.logout}
              </div>
              <div style={{ fontSize:17,fontWeight:700,color:"var(--text-1)",marginBottom:6 }}>Sign out?</div>
              <div style={{ fontSize:13,color:"var(--text-3)",lineHeight:1.6 }}>You will be returned to the login page.</div>
            </div>
            <div style={{ display:"flex",gap:10 }}>
              <button className="btn-ems btn-secondary-ems" style={{ flex:1,justifyContent:"center" }} onClick={()=>setShowSignout(false)}>Cancel</button>
              <button className="btn-ems btn-danger-ems" style={{ flex:1,justifyContent:"center" }}
                onClick={()=>{ localStorage.clear(); window.location.href="/"; }}>
                {ic.logout} Sign Out
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

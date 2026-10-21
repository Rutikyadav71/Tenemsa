import { useState, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";

const ic = {
  dashboard:  <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><rect x="3" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/><rect x="14" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/><rect x="3" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/><rect x="14" y="14" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1.8"/></svg>,
  employees:  <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  add:        <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8"/><path d="M12 8v8M8 12h8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  leaves:     <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  applyLeave: <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M14 2v6h6M12 18v-6M9 15h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  attendance: <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><polyline points="9 12 11 14 15 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  teams:      <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  chat:       <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  notif:      <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  admins:     <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  invite:     <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM22 6l-10 7L2 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  audit:      <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h8M8 9h2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  company:    <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 9h1M9 13h1M14 9h1M14 13h1M10 21v-4h4v4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  profile:    <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/></svg>,
  logout:     <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>,
};

const navItems = [
  { to:"/admin/dashboard",     label:"Dashboard",      icon:ic.dashboard },
  { to:"/admin/employees",     label:"Employees",      icon:ic.employees },
  { to:"/admin/add",           label:"Add Employee",   icon:ic.add },
  { to:"/admin/leaves",        label:"Leave Requests", icon:ic.leaves },
  { to:"/admin/apply-leave",   label:"Apply Leave",    icon:ic.applyLeave },
  { to:"/admin/attendance",    label:"Attendance",     icon:ic.attendance },
  { to:"/admin/teams",         label:"Teams",          icon:ic.teams },
  { to:"/admin/chat",          label:"Chat Room",      icon:ic.chat },
  { to:"/admin/notifications", label:"Notifications",  icon:ic.notif },
  { to:"/admin/manage-admins", label:"Admin Control",  icon:ic.admins },
  { to:"/admin/invitations",   label:"Invitations",    icon:ic.invite },
  { to:"/admin/audit",         label:"Audit Log",      icon:ic.audit },
  { to:"/admin/company",       label:"Company",        icon:ic.company },
];

interface AdminSidebarProps {
  isOpen?: boolean;
  collapsed?: boolean;
  toggleSidebar?: () => void;
}

interface NavItemData { to: string; label: string; icon: ReactNode; }

export default function AdminSidebar({ collapsed, toggleSidebar }: AdminSidebarProps) {
  const location = useLocation();
  const name     = localStorage.getItem("adminName") || "Admin";
  const isMain   = localStorage.getItem("primaryOwner") === "true";
  const imgUrl   = localStorage.getItem("adminProfileImageUrl") || null;
  const initials = name.split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2);
  const [showSignout, setShowSignout] = useState(false);

  const NavItem = ({ item }: { item: NavItemData }) => {
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
        {/* Logo */}
        <div className="sidebar-logo" style={{ justifyContent:collapsed?"center":undefined }}>
          <div className="sidebar-logo-icon" style={{ flexShrink:0 }}>
            <svg width="18" height="18" fill="none" viewBox="0 0 24 24">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
                stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          {!collapsed && <div><div className="sidebar-logo-text">Tenemsa</div><div className="sidebar-logo-sub">{localStorage.getItem("companyName") || "Admin Panel"}</div></div>}
        </div>

        {!collapsed && <div className="sidebar-section-label">Main Menu</div>}
        <nav className="sidebar-nav">
          {navItems.map(item => <NavItem key={item.to} item={item}/>)}
        </nav>

        {!collapsed && <div className="sidebar-section-label">Account</div>}
        <div className="sidebar-footer">
          <NavItem item={{ to:"/admin/profile", label:"Profile", icon:ic.profile }}/>
          <div className="sidebar-user"
            style={{ justifyContent:collapsed?"center":undefined, padding:collapsed?"8px 10px":undefined, cursor:"default" }}>
            {imgUrl
              ? <img src={imgUrl} alt={name} title={collapsed?name:undefined}
                  style={{ width:30,height:30,borderRadius:"50%",objectFit:"cover",flexShrink:0,border:"1px solid var(--border-bright)" }}/>
              : <div className="sidebar-avatar" style={{ flexShrink:0 }} title={collapsed?name:undefined}>{initials}</div>
            }
            {!collapsed && (
              <>
                <div className="sidebar-user-info">
                  <div className="sidebar-user-name">{name}</div>
                  <div className="sidebar-user-role" style={{ color:isMain?"var(--accent-light)":"var(--text-3)" }}>
                    {isMain?"Main Admin":"Administrator"}
                  </div>
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

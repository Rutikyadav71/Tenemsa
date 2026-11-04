import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { LuBell, LuLogOut, LuMenu, LuMonitor, LuMoon, LuPanelLeftClose, LuPanelLeftOpen, LuSearch, LuSettings, LuSun, LuUser } from "react-icons/lu";
import axiosInstance from "../../shared/api/axiosInstance";
import { performLogout } from "../../shared/lib/logout";
import { useTheme, type ThemeMode } from "../../shared/theme/ThemeProvider";
import { Button, useConfirm } from "../../shared/ui";
import type { Notification } from "../../shared/types/models";
import type { Workspace } from "../../shared/lib/roles";
import Breadcrumbs from "./Breadcrumbs";

const ROLE_LABEL: Record<string, string> = {
  OWNER: "Owner", ADMIN: "Admin", SUPER_ADMIN: "Super admin", HR: "HR", MANAGER: "Manager", EMPLOYEE: "Employee", INTERN: "Intern",
};

const initialsOf = (name: string) => name.split(" ").filter(Boolean).map(n => n[0]).join("").toUpperCase().slice(0, 2) || "?";

const THEMES: { id: ThemeMode; label: string; icon: typeof LuSun }[] = [
  { id: "light", label: "Light", icon: LuSun }, { id: "dark", label: "Dark", icon: LuMoon }, { id: "system", label: "System", icon: LuMonitor },
];

interface TopbarProps {
  workspace: Workspace;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  onOpenMenu: () => void;
  onOpenSearch: () => void;
}

export default function Topbar({ workspace, collapsed, onToggleCollapsed, onOpenMenu, onOpenSearch }: TopbarProps) {
  const navigate = useNavigate();
  const confirmAction = useConfirm();
  const { mode, setMode } = useTheme();
  const name = localStorage.getItem("name") || localStorage.getItem("adminName") || "You";
  const role = localStorage.getItem("userRole") || "";
  const email = localStorage.getItem("email") || "";
  const photo = localStorage.getItem("profileImageUrl") || localStorage.getItem("adminProfileImageUrl") || "";

  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [bell, setBell] = useState(false);
  const [menu, setMenu] = useState(false);
  const bellRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const unread = notifications.filter(n => !n.read).length;

  const load = useCallback(async () => {
    try { setNotifications((await axiosInstance.get<Notification[]>("/api/notifications")).data || []); }
    catch { /* the bell simply shows nothing until the next refresh */ }
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(() => { if (!document.hidden) load(); }, 30000);
    return () => clearInterval(t);
  }, [load]);

  useEffect(() => {
    const down = (e: MouseEvent) => {
      if (bellRef.current && !bellRef.current.contains(e.target as Node)) setBell(false);
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenu(false);
    };
    const esc = (e: KeyboardEvent) => { if (e.key === "Escape") { setBell(false); setMenu(false); } };
    document.addEventListener("mousedown", down);
    document.addEventListener("keydown", esc);
    return () => { document.removeEventListener("mousedown", down); document.removeEventListener("keydown", esc); };
  }, []);

  const openBell = async () => {
    setBell(v => !v); setMenu(false);
    if (unread > 0) { await axiosInstance.put("/api/notifications/mark-all-read").catch(() => undefined); load(); }
  };

  const signOut = async () => {
    setMenu(false);
    if (!(await confirmAction({ title: "Sign out of Tenemsa?", message: "You will be returned to the sign-in page.", confirmLabel: "Sign out", tone: "primary" }))) return;
    performLogout().finally(() => navigate("/"));
  };

  const avatar = photo
    ? <img src={photo} alt="" className="sh-avatar-img" />
    : <span className="sh-avatar">{initialsOf(name)}</span>;

  return (
    <header className="sh-topbar">
      <Button variant="ghost" className="sh-only-mobile" aria-label="Open menu" onClick={onOpenMenu} icon={<LuMenu size={20} />} />
      <Button variant="ghost" className="sh-only-desktop" aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"} onClick={onToggleCollapsed}
        icon={collapsed ? <LuPanelLeftOpen size={19} /> : <LuPanelLeftClose size={19} />} />
      <div className="sh-crumbs-wrap"><Breadcrumbs workspace={workspace} /></div>

      <button className="sh-search" onClick={onOpenSearch} aria-label="Search (Ctrl K)">
        <LuSearch size={16} aria-hidden="true" />
        <span>Search pages, projects, people…</span>
        <kbd>Ctrl K</kbd>
      </button>
      <Button variant="ghost" className="sh-search-icon" aria-label="Search" onClick={onOpenSearch} icon={<LuSearch size={19} />} />

      <div className="sh-pop-anchor" ref={bellRef}>
        <Button variant="ghost" aria-label={`Notifications${unread ? `, ${unread} unread` : ""}`} aria-expanded={bell} onClick={openBell}
          icon={<span className="sh-bell"><LuBell size={19} />{unread > 0 && <i>{unread > 9 ? "9+" : unread}</i>}</span>} />
        {bell && (
          <div className="sh-pop sh-pop-notifs" role="dialog" aria-label="Notifications">
            <div className="sh-pop-head"><strong>Notifications</strong><Link to={`/${workspace}/notifications`} onClick={() => setBell(false)}>View all</Link></div>
            {notifications.length === 0
              ? <div className="sh-pop-empty">You're all caught up.</div>
              : notifications.slice(0, 6).map(n => (
                <div key={n.id} className={`sh-notif${n.read ? "" : " unread"}`}>
                  <div>{n.message}</div>
                  {n.createdAt && <time>{new Date(n.createdAt).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}</time>}
                </div>
              ))}
          </div>
        )}
      </div>

      <div className="sh-pop-anchor" ref={menuRef}>
        <button className="sh-user" onClick={() => { setMenu(v => !v); setBell(false); }} aria-expanded={menu} aria-haspopup="menu" aria-label="Account menu">
          {avatar}
          <span className="sh-user-text"><b>{name}</b><small>{ROLE_LABEL[role] ?? role}</small></span>
        </button>
        {menu && (
          <div className="sh-pop sh-pop-menu" role="menu">
            <div className="sh-pop-who">{avatar}<div><b>{name}</b><small>{email}</small></div></div>
            <Link role="menuitem" to={`/${workspace}/profile`} onClick={() => setMenu(false)} className="sh-menu-item"><LuUser size={16} /> My profile</Link>
            <Link role="menuitem" to={`/${workspace}/settings`} onClick={() => setMenu(false)} className="sh-menu-item"><LuSettings size={16} /> Settings</Link>
            <div className="sh-pop-label" id="theme-label">Appearance</div>
            <div className="sh-theme" role="radiogroup" aria-labelledby="theme-label">
              {THEMES.map(t => { const Icon = t.icon; return (
                <button key={t.id} role="radio" aria-checked={mode === t.id} className={mode === t.id ? "on" : ""} onClick={() => setMode(t.id)}>
                  <Icon size={15} aria-hidden="true" />{t.label}
                </button>
              ); })}
            </div>
            <button role="menuitem" className="sh-menu-item danger" onClick={signOut}><LuLogOut size={16} /> Sign out</button>
          </div>
        )}
      </div>
    </header>
  );
}

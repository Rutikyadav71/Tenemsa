import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Button } from "./ui";
import type { Admin } from "../api/client";
import { setToken } from "../api/client";

const NAV = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/companies", label: "Companies" },
  { to: "/announcements", label: "Announcements" },
  { to: "/audit", label: "Audit log" },
  { to: "/admins", label: "Administrators" },
  { to: "/system", label: "System" },
];

export default function Layout({ admin }: { admin: Admin }) {
  const nav = useNavigate();
  const out = () => { setToken(null); nav("/login"); };
  const toggle = () => {
    const cur = document.documentElement.getAttribute("data-theme");
    const next = cur === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    try { localStorage.setItem("tenemsa-theme", next); } catch { /* ignore */ }
  };
  return (
    <div className="pc-app">
      <a className="pc-skip" href="#main">Skip to content</a>
      <aside className="pc-side">
        <div className="pc-brand"><img src="/brand/tenemsa-logo.png" alt="Tenemsa" height={30} /><span className="pc-tag">Platform</span></div>
        <nav aria-label="Console">
          {NAV.map(n => <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) => `pc-link${isActive ? " active" : ""}`}>{n.label}</NavLink>)}
        </nav>
        <div className="pc-side-foot">
          <div className="pc-me"><b>{admin.name}</b><span>{admin.email}</span><span>Administrator {admin.slot}</span></div>
          <div className="pc-side-btns"><Button size="sm" onClick={toggle}>Theme</Button><Button size="sm" onClick={out}>Sign out</Button></div>
        </div>
      </aside>
      <main id="main" className="pc-main" tabIndex={-1}><Outlet /></main>
    </div>
  );
}

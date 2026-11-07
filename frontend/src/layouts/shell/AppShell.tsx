import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Outlet, useLocation } from "react-router-dom";
import PlatformNotices from "../../features/announcements/components/PlatformNotices";
import type { Workspace } from "../../shared/lib/roles";
import Sidebar from "./Sidebar";
import Topbar from "./Topbar";
import BottomNav from "./BottomNav";
import CommandPalette from "./CommandPalette";
import { AccessDenied } from "./GuardPages";
import ErrorBoundary from "./ErrorBoundary";
import { findItem, isAllowed } from "./navConfig";
import "./shell.css";

const COLLAPSE_KEY = "tenemsa-sidebar-collapsed";

/**
 * The one layout for every role. What a person sees (menus, search results, and whether a URL opens
 * at all) comes from their permissions; the backend still checks every request.
 * Pages render through <Outlet/> so each workspace keeps its own route table in App.tsx.
 */
export default function AppShell({ workspace }: { workspace: Workspace }) {
  const { pathname } = useLocation();
  const [collapsed, setCollapsed] = useState(() => { try { return localStorage.getItem(COLLAPSE_KEY) === "1"; } catch { return false; } });
  const [drawer, setDrawer] = useState(false);
  const [palette, setPalette] = useState(false);

  const toggleCollapsed = useCallback(() => setCollapsed(c => {
    try { localStorage.setItem(COLLAPSE_KEY, c ? "0" : "1"); } catch { /* not remembered */ }
    return !c;
  }), []);

  useEffect(() => { setDrawer(false); }, [pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); setPalette(p => !p); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const home = `/${workspace}/dashboard`;
  const match = findItem(workspace, pathname);
  const denied = match ? !isAllowed(match.item, workspace) : false;

  let content: ReactNode = <Outlet />;
  if (denied) content = <AccessDenied home={home} />;

  return (
    <div className={`sh-app${collapsed ? " sh-collapsed" : ""}`}>
      <a className="sh-skip" href="#main">Skip to content</a>
      <aside className="sh-aside"><Sidebar workspace={workspace} collapsed={collapsed} /></aside>

      {drawer && (
        <div className="sh-drawer-wrap" onMouseDown={e => { if (e.target === e.currentTarget) setDrawer(false); }}>
          <aside className="sh-drawer"><Sidebar workspace={workspace} collapsed={false} onNavigate={() => setDrawer(false)} /></aside>
        </div>
      )}

      <div className="sh-body">
        <Topbar workspace={workspace} collapsed={collapsed} onToggleCollapsed={toggleCollapsed} onOpenMenu={() => setDrawer(true)} onOpenSearch={() => setPalette(true)} />
        <main id="main" className="sh-main" tabIndex={-1}><PlatformNotices /><ErrorBoundary resetKey={pathname}>{content}</ErrorBoundary></main>
      </div>

      <BottomNav workspace={workspace} onMore={() => setDrawer(true)} />
      <CommandPalette workspace={workspace} open={palette} onClose={() => setPalette(false)} />
    </div>
  );
}


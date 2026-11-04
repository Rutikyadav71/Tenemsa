import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { LuChevronDown } from "react-icons/lu";
import type { Workspace } from "../../shared/lib/roles";
import { Logo } from "../../shared/ui";
import { findItem, toPath, visibleGroups } from "./navConfig";

const OPEN_KEY = "tenemsa-nav-open";

function readOpen(): Record<string, boolean> {
  try { return JSON.parse(localStorage.getItem(OPEN_KEY) || "{}"); } catch { return {}; }
}

interface SidebarProps {
  workspace: Workspace;
  collapsed: boolean;
  /** Called after a link is followed, so the mobile drawer can close. */
  onNavigate?: () => void;
}

/** Grouped navigation. Groups the user has no access to never appear; the group holding the current page opens itself. */
export default function Sidebar({ workspace, collapsed, onNavigate }: SidebarProps) {
  const { pathname } = useLocation();
  const groups = visibleGroups(workspace);
  const active = findItem(workspace, pathname);
  const [open, setOpen] = useState<Record<string, boolean>>(readOpen);

  useEffect(() => {
    if (active && open[active.group.id] === false) setOpen(o => ({ ...o, [active.group.id]: true }));
    // Only react to navigation, not to the user closing a group on purpose.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  const toggle = (id: string) => setOpen(o => {
    const next = { ...o, [id]: o[id] === false };
    try { localStorage.setItem(OPEN_KEY, JSON.stringify(next)); } catch { /* not remembered in private mode */ }
    return next;
  });

  const company = localStorage.getItem("companyName") || "";

  return (
    <nav className={`sh-sidebar${collapsed ? " collapsed" : ""}`} aria-label="Main navigation">
      <div className="sh-brand">
        {collapsed ? <Logo icon height={30} plate={false} /> : <Logo height={32} />}
      </div>
      {!collapsed && company && <div className="sh-workspace" title={company}><span className="sh-workspace-dot" />{company}</div>}

      <div className="sh-scroll">
        {groups.map(g => {
          const single = g.items.length === 1;
          const isOpen = collapsed || single || open[g.id] !== false;
          return (
            <div className="sh-group" key={g.id}>
              {!collapsed && !single && (
                <button className="sh-group-head" onClick={() => toggle(g.id)} aria-expanded={isOpen}>
                  <span>{g.label}</span>
                  <LuChevronDown size={14} style={{ transform: isOpen ? "none" : "rotate(-90deg)", transition: "transform .15s" }} />
                </button>
              )}
              {collapsed && <div className="sh-group-sep" aria-hidden="true" />}
              {isOpen && g.items.map(item => {
                const Icon = item.icon;
                const to = toPath(workspace, item);
                const current = active?.item.id === item.id;
                return (
                  <NavLink key={item.id} to={to} end={false} onClick={onNavigate}
                    className={`sh-item${current ? " active" : ""}`} aria-current={current ? "page" : undefined}
                    title={collapsed ? item.label : undefined}>
                    <Icon size={18} aria-hidden="true" />
                    {!collapsed && <span>{item.label}</span>}
                    {collapsed && <span className="sr-only">{item.label}</span>}
                  </NavLink>
                );
              })}
            </div>
          );
        })}
      </div>
    </nav>
  );
}

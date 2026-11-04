import { NavLink } from "react-router-dom";
import { LuEllipsis } from "react-icons/lu";
import type { Workspace } from "../../shared/lib/roles";
import { toPath, visibleGroups, type NavItem } from "./navConfig";

/** Phone navigation: the four things people open most, plus "More" for the full menu. */
const PRIORITY = ["dashboard", "attendance", "projects", "chat"];

export default function BottomNav({ workspace, onMore }: { workspace: Workspace; onMore: () => void }) {
  const all = visibleGroups(workspace).flatMap(g => g.items);
  const picks = PRIORITY.map(id => all.find(i => i.id === id)).filter((i): i is NavItem => !!i).slice(0, 4);
  return (
    <nav className="sh-bottom" aria-label="Quick navigation">
      {picks.map(item => { const Icon = item.icon; return (
        <NavLink key={item.id} to={toPath(workspace, item)} className={({ isActive }) => (isActive ? "active" : "")}>
          <Icon size={21} aria-hidden="true" /><span>{item.label.replace("Apply for ", "")}</span>
        </NavLink>
      ); })}
      <button onClick={onMore} aria-label="More"><LuEllipsis size={21} aria-hidden="true" /><span>More</span></button>
    </nav>
  );
}

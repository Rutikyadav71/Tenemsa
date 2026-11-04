import { Link, useLocation } from "react-router-dom";
import { LuChevronRight } from "react-icons/lu";
import type { Workspace } from "../../shared/lib/roles";
import { findItem, toPath } from "./navConfig";

const EXTRA: Record<string, string> = { profile: "My profile", settings: "Settings", backlog: "Backlog" };

/** Home › Group › Page (› detail), built from the same navigation definition as the sidebar. */
export default function Breadcrumbs({ workspace }: { workspace: Workspace }) {
  const { pathname } = useLocation();
  const match = findItem(workspace, pathname);
  const crumbs: { label: string; to?: string }[] = [{ label: "Home", to: `/${workspace}/dashboard` }];

  if (match) {
    const base = toPath(workspace, match.item);
    if (match.group.id !== "home") crumbs.push({ label: match.group.label });
    const rest = pathname.slice(base.length).split("/").filter(Boolean);
    crumbs.push({ label: match.item.label, to: rest.length ? base : undefined });
    rest.forEach((seg, i) => {
      const label = EXTRA[seg] ?? (/^\d+$/.test(seg) ? "Details" : seg.replace(/-/g, " "));
      crumbs.push({ label, to: i < rest.length - 1 ? `${base}/${rest.slice(0, i + 1).join("/")}` : undefined });
    });
  } else {
    const seg = pathname.split("/").filter(Boolean)[1];
    if (seg) crumbs.push({ label: EXTRA[seg] ?? seg.replace(/-/g, " ") });
  }
  if (crumbs.length === 2 && match?.group.id === "home") crumbs.pop();

  return (
    <nav className="sh-crumbs" aria-label="Breadcrumb">
      <ol>
        {crumbs.map((c, i) => (
          <li key={i} aria-current={i === crumbs.length - 1 ? "page" : undefined}>
            {c.to && i < crumbs.length - 1 ? <Link to={c.to}>{c.label}</Link> : <span>{c.label}</span>}
            {i < crumbs.length - 1 && <LuChevronRight size={13} aria-hidden="true" />}
          </li>
        ))}
      </ol>
    </nav>
  );
}

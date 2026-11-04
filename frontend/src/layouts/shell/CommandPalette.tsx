import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { LuCornerDownLeft, LuFolderKanban, LuSearch, LuUser } from "react-icons/lu";
import type { IconType } from "react-icons";
import { can, type Workspace } from "../../shared/lib/roles";
import { listProjects } from "../../features/projects/services/projectService";
import { searchEmployees } from "../../features/employees/services/EmployeeService";
import type { Project, User } from "../../shared/types/models";
import { paletteItems } from "./navConfig";

interface Entry { key: string; label: string; hint: string; to: string; icon: IconType }

/** Ctrl/Cmd+K: jump to any page the user may open, or find a project or person. Only what the user is allowed to see is offered. */
export default function CommandPalette({ workspace, open, onClose }: { workspace: Workspace; open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [cursor, setCursor] = useState(0);
  const [projects, setProjects] = useState<Project[]>([]);
  const [people, setPeople] = useState<User[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const q = query.trim().toLowerCase();

  useEffect(() => {
    if (!open) return;
    setQuery(""); setCursor(0); setPeople([]);
    setTimeout(() => inputRef.current?.focus(), 0);
    if (can("project:view")) listProjects().then(setProjects).catch(() => setProjects([]));
  }, [open]);

  useEffect(() => {
    if (!open || q.length < 2 || !can("user:view:any")) { setPeople([]); return; }
    const t = setTimeout(() => { searchEmployees(q).then(r => setPeople(r.data.slice(0, 5))).catch(() => setPeople([])); }, 250);
    return () => clearTimeout(t);
  }, [q, open]);

  const entries = useMemo<Entry[]>(() => {
    const pages = paletteItems(workspace)
      .filter(p => !q || `${p.item.label} ${p.group} ${p.item.keywords ?? ""}`.toLowerCase().includes(q))
      .map(p => ({ key: `p-${p.item.id}`, label: p.item.label, hint: p.group, to: p.to, icon: p.item.icon }));
    const proj = q ? projects.filter(p => `${p.key} ${p.name}`.toLowerCase().includes(q)).slice(0, 5)
      .map(p => ({ key: `pr-${p.id}`, label: p.name, hint: `Project ${p.key}`, to: `/${workspace}/projects/${p.id}`, icon: LuFolderKanban })) : [];
    const ppl = people.map(u => ({ key: `u-${u.empId}`, label: u.name, hint: u.department || u.role || "Employee",
      to: can("user:update") ? `/${workspace}/update/${u.empId}` : `/${workspace}/employees`, icon: LuUser }));
    return [...pages.slice(0, 8), ...proj, ...ppl];
  }, [workspace, q, projects, people]);

  useEffect(() => setCursor(0), [q]);

  if (!open) return null;
  const go = (e: Entry) => { onClose(); navigate(e.to); };
  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") { e.preventDefault(); setCursor(c => Math.min(c + 1, entries.length - 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setCursor(c => Math.max(c - 1, 0)); }
    else if (e.key === "Enter" && entries[cursor]) { e.preventDefault(); go(entries[cursor]); }
    else if (e.key === "Escape") { e.preventDefault(); onClose(); }
  };

  return createPortal(
    <div className="tn-overlay sh-palette-wrap" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="sh-palette" role="dialog" aria-modal="true" aria-label="Search and quick navigation" onKeyDown={onKey}>
        <div className="sh-palette-input">
          <LuSearch size={18} aria-hidden="true" />
          <input ref={inputRef} value={query} onChange={e => setQuery(e.target.value)} placeholder="Search pages, projects and people…"
            role="combobox" aria-expanded="true" aria-controls="sh-palette-list" aria-activedescendant={entries[cursor] ? `sh-opt-${entries[cursor].key}` : undefined} />
          <kbd>Esc</kbd>
        </div>
        <ul id="sh-palette-list" role="listbox" className="sh-palette-list">
          {entries.length === 0 && <li className="sh-palette-empty">Nothing matches “{query}”.</li>}
          {entries.map((e, i) => { const Icon = e.icon; return (
            <li key={e.key} id={`sh-opt-${e.key}`} role="option" aria-selected={i === cursor} className={i === cursor ? "active" : ""}
              onMouseEnter={() => setCursor(i)} onClick={() => go(e)}>
              <Icon size={17} aria-hidden="true" />
              <span className="sh-palette-label">{e.label}</span>
              <span className="sh-palette-hint">{e.hint}</span>
              {i === cursor && <LuCornerDownLeft size={14} aria-hidden="true" />}
            </li>
          ); })}
        </ul>
      </div>
    </div>, document.body);
}

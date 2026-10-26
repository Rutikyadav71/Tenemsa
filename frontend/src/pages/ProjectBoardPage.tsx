import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  addComment, createIssue, deleteIssue, deleteProject, getProject, listComments, listIssues,
  listPeople, listSprints, MANAGER_ROLES, moveIssue, updateIssue, updateProject,
} from "../services/projectService";
import type {
  Issue, IssueComment, IssueInput, IssuePriority, IssueStatus, IssueType, Person, Project, Sprint,
} from "../types/models";

const COLUMNS: { status: IssueStatus; label: string }[] = [
  { status: "TODO", label: "To do" },
  { status: "IN_PROGRESS", label: "In progress" },
  { status: "IN_REVIEW", label: "In review" },
  { status: "DONE", label: "Done" },
];
const PRIORITY_COLOR: Record<IssuePriority, string> = { LOW: "#64748b", MEDIUM: "#3b82f6", HIGH: "#f59e0b", URGENT: "#ef4444" };
const TYPES: IssueType[] = ["TASK", "BUG", "STORY"];
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const emptyInput = (): IssueInput => ({ title: "", description: "", type: "TASK", priority: "MEDIUM", assigneeEmpId: null, dueDate: null, storyPoints: null, labels: [] });
const msg = (err: any, fallback: string) => err?.response?.data?.message || fallback;

export default function ProjectBoardPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";
  const role = localStorage.getItem("userRole") || "";
  const myEmpId = Number(localStorage.getItem("empId") || 0);
  const canManage = MANAGER_ROLES.includes(role);

  const [project, setProject] = useState<Project | null>(null);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [people, setPeople] = useState<Person[]>([]);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [onlyMine, setOnlyMine] = useState(false);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [sprintFilter, setSprintFilter] = useState<string>("active"); // active | all | backlog | <sprintId>
  const [dragId, setDragId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [open, setOpen] = useState<Issue | null>(null);

  const archived = project?.status === "ARCHIVED";

  const reload = async () => {
    try {
      const [p, i, s] = await Promise.all([getProject(projectId), listIssues(projectId), listSprints(projectId)]);
      setProject(p); setIssues(i); setSprints(s);
    } catch (err: any) {
      setError(err.response?.status === 404 ? "Project not found." : msg(err, "Could not load the project."));
    }
  };

  useEffect(() => { reload(); listPeople().then(setPeople).catch(() => {}); }, [projectId]);

  const activeSprint = sprints.find(s => s.status === "ACTIVE") || null;

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return issues.filter(i => {
      const inSprint =
        sprintFilter === "all" ? true
        : sprintFilter === "backlog" ? i.sprintId === null
        : sprintFilter === "active" ? (activeSprint ? i.sprintId === activeSprint.id : true)
        : i.sprintId === Number(sprintFilter);
      return inSprint && (!onlyMine || i.assigneeEmpId === myEmpId) &&
        (!q || i.title.toLowerCase().includes(q) || i.key.toLowerCase().includes(q) ||
          i.labels.some(l => l.includes(q)));
    });
  }, [issues, search, onlyMine, myEmpId, sprintFilter, activeSprint]);

  const canChange = (i: Issue) => !archived && (canManage || i.reporterEmpId === myEmpId || i.assigneeEmpId === myEmpId);

  const move = async (issue: Issue, status: IssueStatus) => {
    if (issue.status === status) return;
    const before = issues;
    setIssues(issues.map(i => (i.id === issue.id ? { ...i, status } : i))); // optimistic
    try {
      const saved = await moveIssue(issue.id, status);
      setIssues(cur => cur.map(i => (i.id === saved.id ? saved : i)));
      setOpen(o => (o && o.id === saved.id ? saved : o));
    } catch (err: any) {
      setIssues(before);
      setError(msg(err, "Could not move the issue."));
    }
  };

  const toggleArchive = async () => {
    if (!project) return;
    try {
      const saved = await updateProject(project.id, {
        name: project.name, description: project.description || undefined, leadEmpId: project.leadEmpId,
        status: archived ? "ACTIVE" : "ARCHIVED",
      });
      setProject(saved);
    } catch (err: any) { setError(msg(err, "Could not update the project.")); }
  };

  const removeProject = async () => {
    if (!project || !window.confirm(`Delete ${project.name} and ALL its issues? This cannot be undone.`)) return;
    try { await deleteProject(project.id); navigate(`${base}/projects`); }
    catch (err: any) { setError(msg(err, "Could not delete the project.")); }
  };

  if (error && !project) return <div className="alert-ems alert-error">{error}</div>;
  if (!project) return <div style={{ color: "var(--text-3)" }}>Loading...</div>;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">
            <span style={{ color: "var(--accent-light)", marginRight: 8 }}>{project.key}</span>{project.name}
            {archived && <span style={{ fontSize: 12, marginLeft: 10, color: "var(--text-3)" }}>ARCHIVED (read-only)</span>}
          </div>
          <div className="page-sub">{project.description || "No description"}{project.leadName ? ` · Lead: ${project.leadName}` : ""}</div>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button className="btn-ems btn-secondary-ems" onClick={() => navigate(`${base}/projects`)}>All projects</button>
          <button className="btn-ems btn-secondary-ems" onClick={() => navigate(`${base}/projects/${project.id}/backlog`)}>Backlog &amp; sprints</button>
          {canManage && <button className="btn-ems btn-secondary-ems" onClick={toggleArchive}>{archived ? "Unarchive" : "Archive"}</button>}
          {canManage && archived && <button className="btn-ems btn-secondary-ems" onClick={removeProject}>Delete</button>}
          {!archived && <button className="btn-ems btn-primary-ems" onClick={() => setCreating(true)}>New issue</button>}
        </div>
      </div>

      {error && <div className="alert-ems alert-error" onClick={() => setError("")}>{error}</div>}

      <div style={{ display: "flex", gap: 12, marginBottom: 16, alignItems: "center", flexWrap: "wrap" }}>
        <select className="form-control-ems" style={{ maxWidth: 220 }} value={sprintFilter} onChange={e => setSprintFilter(e.target.value)}>
          <option value="active">{activeSprint ? `Active: ${activeSprint.name}` : "All issues (no active sprint)"}</option>
          <option value="all">All issues</option>
          <option value="backlog">Backlog only</option>
          {sprints.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status.toLowerCase()})</option>)}
        </select>
        <input className="form-control-ems" style={{ maxWidth: 280 }} placeholder="Search title, key or label"
               value={search} onChange={e => setSearch(e.target.value)} />
        <label style={{ fontSize: 13, color: "var(--text-3)", display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={onlyMine} onChange={e => setOnlyMine(e.target.checked)} /> Assigned to me
        </label>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))", gap: 14, alignItems: "start" }}>
        {COLUMNS.map(col => {
          const items = visible.filter(i => i.status === col.status);
          return (
            <div key={col.status}
                 onDragOver={e => e.preventDefault()}
                 onDrop={() => { const i = issues.find(x => x.id === dragId); if (i && canChange(i)) move(i, col.status); setDragId(null); }}
                 style={{ background: "var(--bg-elevated)", borderRadius: 10, padding: 10, minHeight: 160 }}>
              <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: 0.5, color: "var(--text-3)", marginBottom: 10 }}>
                {col.label.toUpperCase()} · {items.length}
              </div>
              {items.map(i => (
                <div key={i.id} className="ems-card" draggable={canChange(i)}
                     onDragStart={() => setDragId(i.id)} onClick={() => setOpen(i)}
                     style={{ padding: 12, marginBottom: 8, cursor: "pointer", borderLeft: `3px solid ${PRIORITY_COLOR[i.priority]}` }}>
                  <div style={{ fontSize: 11, color: "var(--text-3)", display: "flex", justifyContent: "space-between" }}>
                    <span>{i.key} · {i.type}</span><span>{i.priority}</span>
                  </div>
                  <div style={{ fontSize: 14, color: "var(--text-1)", margin: "4px 0" }}>{i.title}</div>
                  {(i.labels.length > 0 || i.storyPoints !== null) && (
                    <div style={{ display: "flex", gap: 4, flexWrap: "wrap", margin: "4px 0" }}>
                      {i.labels.map(l => <span key={l} style={{ fontSize: 11, padding: "1px 6px", borderRadius: 8, background: "var(--bg-surface)", color: "var(--text-2)" }}>{l}</span>)}
                      {i.storyPoints !== null && <span style={{ fontSize: 11, padding: "1px 6px", borderRadius: 8, background: "var(--accent-light)", color: "#fff" }}>{i.storyPoints} pt</span>}
                    </div>
                  )}
                  <div style={{ fontSize: 12, color: "var(--text-3)", display: "flex", justifyContent: "space-between" }}>
                    <span>{i.assigneeName || "Unassigned"}</span>
                    {i.dueDate && <span>Due {i.dueDate}</span>}
                  </div>
                </div>
              ))}
            </div>
          );
        })}
      </div>

      {creating && (
        <IssueForm title="New issue" people={people} initial={emptyInput()} submitLabel="Create issue"
          onClose={() => setCreating(false)}
          onSubmit={async input => { await createIssue(projectId, input); setCreating(false); await reload(); }} />
      )}

      {open && (
        <IssueDetail issue={open} people={people} canChange={canChange(open)} archived={archived}
          onClose={() => setOpen(null)} onMove={s => move(open, s)}
          onSaved={saved => { setOpen(saved); setIssues(cur => cur.map(i => (i.id === saved.id ? saved : i))); }}
          onDeleted={async () => { setOpen(null); await reload(); }} />
      )}
    </div>
  );
}

// ---- create / edit form ------------------------------------------------------

function IssueForm(props: {
  title: string; submitLabel: string; people: Person[]; initial: IssueInput;
  onClose: () => void; onSubmit: (input: IssueInput) => Promise<void>;
}) {
  const [f, setF] = useState<IssueInput>(props.initial);
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const [labelText, setLabelText] = useState((props.initial.labels || []).join(", "));

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setErr(""); setBusy(true);
    try {
      await props.onSubmit({ ...f, title: f.title.trim(), description: f.description?.trim() || undefined, dueDate: f.dueDate || null,
        labels: labelText.split(",").map(l => l.trim()).filter(Boolean) });
    } catch (error: any) { setErr(msg(error, "Could not save the issue.")); setBusy(false); }
  };

  return (
    <div className="ems-modal-backdrop" onClick={props.onClose}>
      <form className="ems-modal" style={{ width: 520 }} onClick={e => e.stopPropagation()} onSubmit={submit}>
        <div className="modal-title">{props.title}</div>
        {err && <div className="alert-ems alert-error">{err}</div>}
        <div className="form-group">
          <label className="form-label">Title *</label>
          <input className="form-control-ems" required maxLength={200} value={f.title} onChange={e => setF({ ...f, title: e.target.value })} />
        </div>
        <div className="form-group">
          <label className="form-label">Description</label>
          <textarea className="form-control-ems" rows={4} value={f.description || ""} onChange={e => setF({ ...f, description: e.target.value })} />
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 14px" }}>
          <div className="form-group">
            <label className="form-label">Type</label>
            <select className="form-control-ems" value={f.type} onChange={e => setF({ ...f, type: e.target.value as IssueType })}>
              {TYPES.map(t => <option key={t}>{t}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Priority</label>
            <select className="form-control-ems" value={f.priority} onChange={e => setF({ ...f, priority: e.target.value as IssuePriority })}>
              {PRIORITIES.map(p => <option key={p}>{p}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Assignee</label>
            <select className="form-control-ems" value={f.assigneeEmpId ?? ""} onChange={e => setF({ ...f, assigneeEmpId: e.target.value ? Number(e.target.value) : null })}>
              <option value="">Unassigned</option>
              {props.people.map(p => <option key={p.empId} value={p.empId}>{p.name}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Due date</label>
            <input type="date" className="form-control-ems" value={f.dueDate || ""} onChange={e => setF({ ...f, dueDate: e.target.value || null })} />
          </div>
          <div className="form-group">
            <label className="form-label">Story points</label>
            <input type="number" min={0} max={100} className="form-control-ems" value={f.storyPoints ?? ""}
                   onChange={e => setF({ ...f, storyPoints: e.target.value === "" ? null : Number(e.target.value) })} />
          </div>
          <div className="form-group">
            <label className="form-label">Labels (comma separated)</label>
            <input className="form-control-ems" value={labelText} onChange={e => setLabelText(e.target.value)} placeholder="backend, api" />
          </div>
        </div>
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button type="button" className="btn-ems btn-secondary-ems" onClick={props.onClose}>Cancel</button>
          <button type="submit" className="btn-ems btn-primary-ems" disabled={busy}>{props.submitLabel}</button>
        </div>
      </form>
    </div>
  );
}

// ---- detail with comments ------------------------------------------------------

function IssueDetail(props: {
  issue: Issue; people: Person[]; canChange: boolean; archived: boolean;
  onClose: () => void; onMove: (s: IssueStatus) => void;
  onSaved: (i: Issue) => void; onDeleted: () => Promise<void>;
}) {
  const { issue } = props;
  const [comments, setComments] = useState<IssueComment[]>([]);
  const [text, setText] = useState("");
  const [editing, setEditing] = useState(false);
  const [err, setErr] = useState("");

  useEffect(() => { listComments(issue.id).then(setComments).catch(() => setErr("Could not load comments.")); }, [issue.id]);

  const post = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    try { const c = await addComment(issue.id, text.trim()); setComments([...comments, c]); setText(""); }
    catch (error: any) { setErr(msg(error, "Could not add the comment.")); }
  };

  const remove = async () => {
    if (!window.confirm(`Delete ${issue.key}?`)) return;
    try { await deleteIssue(issue.id); await props.onDeleted(); }
    catch (error: any) { setErr(msg(error, "Could not delete the issue.")); }
  };

  if (editing) {
    return (
      <IssueForm title={`Edit ${issue.key}`} submitLabel="Save" people={props.people}
        initial={{ title: issue.title, description: issue.description || "", type: issue.type, priority: issue.priority,
                   assigneeEmpId: issue.assigneeEmpId, dueDate: issue.dueDate,
                   storyPoints: issue.storyPoints, labels: issue.labels }}
        onClose={() => setEditing(false)}
        onSubmit={async input => { props.onSaved(await updateIssue(issue.id, input)); setEditing(false); }} />
    );
  }

  return (
    <div className="ems-modal-backdrop" onClick={props.onClose}>
      <div className="ems-modal" style={{ width: 620 }} onClick={e => e.stopPropagation()}>
        <div style={{ fontSize: 12, color: "var(--text-3)" }}>{issue.key} · {issue.type} · {issue.priority}</div>
        <div className="modal-title" style={{ marginTop: 4 }}>{issue.title}</div>
        {err && <div className="alert-ems alert-error">{err}</div>}
        <div style={{ whiteSpace: "pre-wrap", color: "var(--text-2)", fontSize: 14, marginBottom: 14 }}>
          {issue.description || "No description."}
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontSize: 13, color: "var(--text-3)", marginBottom: 14 }}>
          <div>Assignee: <b style={{ color: "var(--text-1)" }}>{issue.assigneeName || "Unassigned"}</b></div>
          <div>Reporter: <b style={{ color: "var(--text-1)" }}>{issue.reporterName || "Unknown"}</b></div>
          <div>Due: <b style={{ color: "var(--text-1)" }}>{issue.dueDate || "None"}</b></div>
          <div>Sprint: <b style={{ color: "var(--text-1)" }}>{issue.sprintName || "Backlog"}</b></div>
          <div>Points: <b style={{ color: "var(--text-1)" }}>{issue.storyPoints ?? "-"}</b></div>
          <div>Labels: <b style={{ color: "var(--text-1)" }}>{issue.labels.length ? issue.labels.join(", ") : "None"}</b></div>
          <div>
            Status:{" "}
            <select className="form-control-ems" style={{ display: "inline-block", width: "auto", padding: "2px 8px" }}
                    disabled={!props.canChange} value={issue.status} onChange={e => props.onMove(e.target.value as IssueStatus)}>
              {COLUMNS.map(c => <option key={c.status} value={c.status}>{c.label}</option>)}
            </select>
          </div>
        </div>

        <div style={{ fontWeight: 600, margin: "10px 0 6px", color: "var(--text-1)" }}>Comments ({comments.length})</div>
        <div style={{ maxHeight: 200, overflowY: "auto", marginBottom: 10 }}>
          {comments.length === 0 && <div style={{ color: "var(--text-3)", fontSize: 13 }}>No comments yet.</div>}
          {comments.map(c => (
            <div key={c.id} style={{ marginBottom: 8, fontSize: 13 }}>
              <b style={{ color: "var(--text-1)" }}>{c.authorName}</b>{" "}
              <span style={{ color: "var(--text-3)", fontSize: 11 }}>{new Date(c.createdAt).toLocaleString()}</span>
              <div style={{ whiteSpace: "pre-wrap", color: "var(--text-2)" }}>{c.body}</div>
            </div>
          ))}
        </div>
        {!props.archived && (
          <form onSubmit={post} style={{ display: "flex", gap: 8, marginBottom: 14 }}>
            <input className="form-control-ems" placeholder="Add a comment" maxLength={5000} value={text} onChange={e => setText(e.target.value)} />
            <button className="btn-ems btn-primary-ems" type="submit">Post</button>
          </form>
        )}

        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          {props.canChange && <button className="btn-ems btn-secondary-ems" onClick={remove}>Delete</button>}
          {props.canChange && <button className="btn-ems btn-secondary-ems" onClick={() => setEditing(true)}>Edit</button>}
          <button className="btn-ems btn-primary-ems" onClick={props.onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}

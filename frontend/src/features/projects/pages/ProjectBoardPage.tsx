import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  addComment, createIssue, deleteIssue, deleteProject, getProject, listComments, listIssues,
  listPeople, listSprints, MANAGER_ROLES, moveIssue, updateIssue, updateProject,
} from "../services/projectService";
import TaskGeneratorModal from "../../ai/components/TaskGeneratorModal";
import ProjectReportModal from "../../ai/components/ProjectReportModal";
import { can } from "../../../shared/lib/roles";
import type {
  Issue, IssueComment, IssueInput, IssuePriority, IssueStatus, IssueType, Person, Project, Sprint,
} from "../../../shared/types/models";
import { Alert, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, Select, SkeletonList, Textarea, useConfirm } from "../../../shared/ui";
import "./projects.css";

const COLUMNS: { status: IssueStatus; label: string }[] = [
  { status: "TODO", label: "To do" },
  { status: "IN_PROGRESS", label: "In progress" },
  { status: "IN_REVIEW", label: "In review" },
  { status: "DONE", label: "Done" },
];
const PRIORITY_COLOR: Record<IssuePriority, string> = { LOW: "#64748b", MEDIUM: "#3b82f6", HIGH: "#f59e0b", URGENT: "#ef4444" };
const pretty = (t: string) => t.charAt(0) + t.slice(1).toLowerCase();
const TYPES: IssueType[] = ["TASK", "BUG", "STORY"];
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const emptyInput = (): IssueInput => ({ title: "", description: "", type: "TASK", priority: "MEDIUM", assigneeEmpId: null, dueDate: null, storyPoints: null, labels: [] });
const msg = (err: any, fallback: string) => err?.response?.data?.message || fallback;

export default function ProjectBoardPage() {
  const confirmAction = useConfirm();
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
  const [aiTasks, setAiTasks] = useState(false);
  const [aiReport, setAiReport] = useState(false);
  const [notice, setNotice] = useState("");

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
    if (!project || !(await confirmAction({ title: `Delete ${project.name}?`, message: "The project and all of its issues will be deleted. This cannot be undone.", confirmLabel: "Delete project" }))) return;
    try { await deleteProject(project.id); navigate(`${base}/projects`); }
    catch (err: any) { setError(msg(err, "Could not delete the project.")); }
  };

  if (error && !project) return <ErrorState message={error} onRetry={() => void reload()} />;
  if (!project) return <SkeletonList rows={4} />;

  return (
    <div>
      <PageHeader
        title={<><span style={{ color: "var(--accent-light)", marginRight: 8 }}>{project.key}</span>{project.name}{archived && <> <Badge>Archived · read-only</Badge></>}</>}
        subtitle={`${project.description || "No description"}${project.leadName ? ` · Lead: ${project.leadName}` : ""}`}
        actions={<>
          <Button onClick={() => navigate(`${base}/projects`)}>All projects</Button>
          <Button onClick={() => navigate(`${base}/projects/${project.id}/backlog`)}>Backlog &amp; sprints</Button>
          {canManage && <Button onClick={toggleArchive}>{archived ? "Unarchive" : "Archive"}</Button>}
          {canManage && archived && <Button variant="danger" onClick={removeProject}>Delete</Button>}
          {can("ai:use") && <Button onClick={() => setAiReport(true)}>AI report</Button>}
          {!archived && can("ai:use") && can("issue:create") && <Button onClick={() => setAiTasks(true)}>AI suggest tasks</Button>}
          {!archived && <Button variant="primary" onClick={() => setCreating(true)}>New issue</Button>}
        </>} />

      <div style={{ display: "grid", gap: 10, marginBottom: 12 }}>
        {error && <Alert tone="error">{error}</Alert>}
        {notice && <Alert tone="success">{notice}</Alert>}
      </div>
      {aiTasks && <TaskGeneratorModal projectId={project.id} onClose={() => setAiTasks(false)}
        onCreated={n => { setNotice(`Created ${n} AI-suggested issue(s). They are labelled "ai-draft".`); void reload(); }} />}
      {aiReport && <ProjectReportModal projectId={project.id} sprints={sprints} onClose={() => setAiReport(false)} />}

      <div className="pj-filters">
        <Select aria-label="Sprint" value={sprintFilter} onChange={e => setSprintFilter(e.target.value)}>
          <option value="active">{activeSprint ? `Active: ${activeSprint.name}` : "All issues (no active sprint)"}</option>
          <option value="all">All issues</option>
          <option value="backlog">Backlog only</option>
          {sprints.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status.toLowerCase()})</option>)}
        </Select>
        <Input aria-label="Search issues" placeholder="Search title, key or label" value={search} onChange={e => setSearch(e.target.value)} />
        <label className="pj-check"><input type="checkbox" checked={onlyMine} onChange={e => setOnlyMine(e.target.checked)} /> Assigned to me</label>
      </div>

      <div className="pj-board">
        {COLUMNS.map(col => {
          const items = visible.filter(i => i.status === col.status);
          return (
            <section key={col.status} className="pj-col" aria-label={`${col.label}, ${items.length} issues`}
              onDragOver={e => e.preventDefault()}
              onDrop={() => { const i = issues.find(x => x.id === dragId); if (i && canChange(i)) void move(i, col.status); setDragId(null); }}>
              <h3 className="pj-col-h">{col.label}<span>{items.length}</span></h3>
              {items.length === 0 && <p className="pj-empty">Nothing here</p>}
              {items.map(i => (
                <button key={i.id} className="pj-issue" draggable={canChange(i)} onDragStart={() => setDragId(i.id)} onClick={() => setOpen(i)}
                  style={{ borderLeftColor: PRIORITY_COLOR[i.priority] }}>
                  <span className="pj-meta"><span>{i.key} · {pretty(i.type)}</span><span>{pretty(i.priority)}</span></span>
                  <span className="pj-title">{i.title}</span>
                  {(i.labels.length > 0 || i.storyPoints !== null) && (
                    <span className="pj-tags">
                      {i.labels.map(l => <span key={l} className="pj-tag">{l}</span>)}
                      {i.storyPoints !== null && <span className="pj-tag pt">{i.storyPoints} pt</span>}
                    </span>
                  )}
                  <span className="pj-meta"><span>{i.assigneeName || "Unassigned"}</span>{i.dueDate && <span>Due {i.dueDate}</span>}</span>
                </button>
              ))}
            </section>
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
          onClose={() => setOpen(null)} onMove={s => void move(open, s)}
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
    } catch (error: any) { setErr(msg(error, "We couldn't save the issue.")); setBusy(false); }
  };

  return (
    <Modal open onClose={props.onClose} dismissible={!busy} title={props.title}
      footer={<><Button variant="secondary" disabled={busy} onClick={props.onClose}>Cancel</Button><Button variant="primary" type="submit" form="issue-form" loading={busy}>{props.submitLabel}</Button></>}>
      <form id="issue-form" onSubmit={submit} style={{ display: "grid", gap: 12 }}>
        {err && <Alert tone="error">{err}</Alert>}
        <Field label="Title" required><Input required maxLength={200} value={f.title} onChange={e => setF({ ...f, title: e.target.value })} data-autofocus /></Field>
        <Field label="Description"><Textarea rows={4} value={f.description || ""} onChange={e => setF({ ...f, description: e.target.value })} /></Field>
        <div className="tn-form-grid">
          <Field label="Type"><Select value={f.type} onChange={e => setF({ ...f, type: e.target.value as IssueType })}>{TYPES.map(t => <option key={t} value={t}>{pretty(t)}</option>)}</Select></Field>
          <Field label="Priority"><Select value={f.priority} onChange={e => setF({ ...f, priority: e.target.value as IssuePriority })}>{PRIORITIES.map(p => <option key={p} value={p}>{pretty(p)}</option>)}</Select></Field>
          <Field label="Assignee">
            <Select value={f.assigneeEmpId ?? ""} onChange={e => setF({ ...f, assigneeEmpId: e.target.value ? Number(e.target.value) : null })}>
              <option value="">Unassigned</option>
              {props.people.map(p => <option key={p.empId} value={p.empId}>{p.name}</option>)}
            </Select>
          </Field>
          <Field label="Due date"><Input type="date" value={f.dueDate || ""} onChange={e => setF({ ...f, dueDate: e.target.value || null })} /></Field>
          <Field label="Story points"><Input type="number" min={0} max={100} value={f.storyPoints ?? ""} onChange={e => setF({ ...f, storyPoints: e.target.value === "" ? null : Number(e.target.value) })} /></Field>
          <Field label="Labels" hint="Separate with commas"><Input value={labelText} onChange={e => setLabelText(e.target.value)} placeholder="backend, api" /></Field>
        </div>
      </form>
    </Modal>
  );
}

// ---- detail with comments ------------------------------------------------------

function IssueDetail(props: {
  issue: Issue; people: Person[]; canChange: boolean; archived: boolean;
  onClose: () => void; onMove: (s: IssueStatus) => void;
  onSaved: (i: Issue) => void; onDeleted: () => Promise<void>;
}) {
  const confirmAction = useConfirm();
  const { issue } = props;
  const [comments, setComments] = useState<IssueComment[]>([]);
  const [text, setText] = useState("");
  const [editing, setEditing] = useState(false);
  const [err, setErr] = useState("");

  useEffect(() => { listComments(issue.id).then(setComments).catch(() => setErr("We couldn't load the comments.")); }, [issue.id]);

  const post = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    try { const c = await addComment(issue.id, text.trim()); setComments([...comments, c]); setText(""); }
    catch (error: any) { setErr(msg(error, "We couldn't add the comment.")); }
  };

  const remove = async () => {
    if (!(await confirmAction({ title: `Delete ${issue.key}?`, message: "This issue and its comments will be deleted.", confirmLabel: "Delete issue" }))) return;
    try { await deleteIssue(issue.id); await props.onDeleted(); }
    catch (error: any) { setErr(msg(error, "We couldn't delete the issue.")); }
  };

  if (editing) {
    return (
      <IssueForm title={`Edit ${issue.key}`} submitLabel="Save" people={props.people}
        initial={{ title: issue.title, description: issue.description || "", type: issue.type, priority: issue.priority,
                   assigneeEmpId: issue.assigneeEmpId, dueDate: issue.dueDate, storyPoints: issue.storyPoints, labels: issue.labels }}
        onClose={() => setEditing(false)}
        onSubmit={async input => { props.onSaved(await updateIssue(issue.id, input)); setEditing(false); }} />
    );
  }

  const facts: [string, string][] = [
    ["Assignee", issue.assigneeName || "Unassigned"], ["Reporter", issue.reporterName || "Unknown"],
    ["Due", issue.dueDate || "None"], ["Sprint", issue.sprintName || "Backlog"],
    ["Points", issue.storyPoints === null ? "–" : String(issue.storyPoints)], ["Labels", issue.labels.length ? issue.labels.join(", ") : "None"],
  ];

  return (
    <Modal open onClose={props.onClose} size="lg" title={issue.title} description={`${issue.key} · ${pretty(issue.type)} · ${pretty(issue.priority)}`}
      footer={<>
        {props.canChange && <Button variant="danger" onClick={remove}>Delete</Button>}
        {props.canChange && <Button onClick={() => setEditing(true)}>Edit</Button>}
        <Button variant="primary" onClick={props.onClose}>Close</Button>
      </>}>
      {err && <Alert tone="error">{err}</Alert>}
      <p className="pj-desc-full">{issue.description || "No description."}</p>
      <dl className="pj-facts">
        {facts.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
        <div>
          <dt>Status</dt>
          <dd><Select aria-label="Status" disabled={!props.canChange} value={issue.status} onChange={e => props.onMove(e.target.value as IssueStatus)}>
            {COLUMNS.map(c => <option key={c.status} value={c.status}>{c.label}</option>)}
          </Select></dd>
        </div>
      </dl>

      <h4 className="pj-h4">Comments ({comments.length})</h4>
      <div className="pj-comments">
        {comments.length === 0 && <p className="pj-empty">No comments yet.</p>}
        {comments.map(c => (
          <div key={c.id} className="pj-comment">
            <b>{c.authorName}</b> <small>{new Date(c.createdAt).toLocaleString()}</small>
            <div>{c.body}</div>
          </div>
        ))}
      </div>
      {!props.archived && (
        <form onSubmit={post} className="pj-post">
          <Input aria-label="Add a comment" placeholder="Add a comment" maxLength={5000} value={text} onChange={e => setText(e.target.value)} />
          <Button variant="primary" type="submit" disabled={!text.trim()}>Post</Button>
        </form>
      )}
    </Modal>
  );
}

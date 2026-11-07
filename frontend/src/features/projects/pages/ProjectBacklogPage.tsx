import { useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  assignToSprint, completeSprint, createSprint, deleteSprint, getProject, listIssues, listSprints,
  MANAGER_ROLES, startSprint, updateSprint,
} from "../services/projectService";
import type { Issue, Project, Sprint, SprintInput } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, ErrorState, Field, Input, Modal, PageHeader, Select, SkeletonList, Textarea, useConfirm, type Tone } from "../../../shared/ui";
import "./projects.css";

const msg = (err: any, fallback: string) => err?.response?.data?.message || fallback;
const STATUS_TONE: Record<string, Tone> = { PLANNED: "neutral", ACTIVE: "success", COMPLETED: "info" };
const pretty = (t: string) => t.charAt(0) + t.slice(1).toLowerCase();

export default function ProjectBacklogPage() {
  const confirmAction = useConfirm();
  const { id } = useParams();
  const projectId = Number(id);
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";
  const canManage = MANAGER_ROLES.includes(localStorage.getItem("userRole") || "");

  const [project, setProject] = useState<Project | null>(null);
  const [sprints, setSprints] = useState<Sprint[]>([]);
  const [issues, setIssues] = useState<Issue[]>([]);
  const [error, setError] = useState("");
  const [form, setForm] = useState<{ sprint: Sprint | null } | null>(null);
  const [completing, setCompleting] = useState<Sprint | null>(null);
  const [dragId, setDragId] = useState<number | null>(null);

  const archived = project?.status === "ARCHIVED";
  const editable = canManage && !archived;

  const reload = async () => {
    try {
      const [p, s, i] = await Promise.all([getProject(projectId), listSprints(projectId), listIssues(projectId)]);
      setProject(p); setSprints(s); setIssues(i);
    } catch (err: any) {
      setError(err.response?.status === 404 ? "Project not found." : msg(err, "Could not load the project."));
    }
  };
  useEffect(() => { reload(); }, [projectId]);

  const run = async (fn: () => Promise<unknown>, fallback: string) => {
    setError("");
    try { await fn(); await reload(); } catch (err: any) { setError(msg(err, fallback)); }
  };

  const drop = (sprintId: number | null) => {
    const issue = issues.find(i => i.id === dragId);
    setDragId(null);
    if (!issue || issue.sprintId === sprintId || !editable) return;
    run(() => assignToSprint(issue.id, sprintId), "Could not move the issue.");
  };

  if (error && !project) return <ErrorState message={error} onRetry={() => void reload()} />;
  if (!project) return <SkeletonList rows={4} />;

  const backlog = issues.filter(i => i.sprintId === null);
  const open = sprints.filter(s => s.status !== "COMPLETED");
  const done = sprints.filter(s => s.status === "COMPLETED").reverse();

  const issueRow = (i: Issue, movable: boolean) => (
    <li key={i.id} className="bl-row" draggable={movable && editable} onDragStart={() => setDragId(i.id)}>
      <span className="bl-key">{i.key}</span>
      <span className="bl-title">{i.title}</span>
      <span className="bl-tags">
        {[...new Set(i.labels)].map(l => <span key={l} className="pj-tag">{l}</span>)}
        {i.storyPoints !== null && <span className="pj-tag pt">{i.storyPoints} pt</span>}
      </span>
      <span className="bl-status">{pretty(i.status.replace("_", " "))}</span>
      {editable && movable && (
        <Select aria-label={`Move ${i.key} to`} className="bl-move" value={i.sprintId ?? ""}
          onChange={e => run(() => assignToSprint(i.id, e.target.value ? Number(e.target.value) : null), "We couldn't move the issue.")}>
          <option value="">Backlog</option>
          {open.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </Select>
      )}
    </li>
  );

  return (
    <div>
      <PageHeader title={<><span style={{ color: "var(--accent-light)", marginRight: 8 }}>{project.key}</span>Backlog &amp; sprints</>}
        subtitle="Plan the work: drag issues into a sprint, start it, complete it when the time is up."
        actions={<>
          <Button onClick={() => navigate(`${base}/projects/${project.id}`)}>Board</Button>
          {editable && <Button variant="primary" onClick={() => setForm({ sprint: null })}>New sprint</Button>}
        </>} />

      <div style={{ display: "grid", gap: 10, marginBottom: 12 }}>
        {error && <Alert tone="error">{error}</Alert>}
        {archived && <Alert tone="info">This project is archived, so planning is read-only.</Alert>}
      </div>

      {open.map(s => (
        <Card key={s.id} pad={false} style={{ marginBottom: 16 }} onDragOver={e => e.preventDefault()} onDrop={() => drop(s.id)}>
          <div className="bl-head">
            <div className="bl-head-main">
              <b>{s.name}</b> <Badge tone={STATUS_TONE[s.status]}>{pretty(s.status)}</Badge>
              <small>{s.startDate ? `${s.startDate} → ${s.endDate ?? "?"}` : "No dates yet"} · {s.doneIssues}/{s.totalIssues} done · {s.donePoints}/{s.totalPoints} pts</small>
              {s.goal && <small>Goal: {s.goal}</small>}
            </div>
            {editable && s.status === "PLANNED" && <div className="bl-actions">
              <Button size="sm" onClick={() => setForm({ sprint: s })}>Edit</Button>
              <Button size="sm" variant="danger" onClick={async () => { if (await confirmAction({ title: `Delete ${s.name}?`, message: "Its issues return to the backlog.", confirmLabel: "Delete sprint" })) void run(() => deleteSprint(s.id), "We couldn't delete the sprint."); }}>Delete</Button>
              <Button size="sm" variant="primary" onClick={() => run(() => startSprint(s.id), "We couldn't start the sprint.")}>Start sprint</Button>
            </div>}
            {editable && s.status === "ACTIVE" && <Button size="sm" variant="primary" onClick={() => setCompleting(s)}>Complete sprint</Button>}
          </div>
          <ul className="bl-list">{issues.filter(i => i.sprintId === s.id).map(i => issueRow(i, true))}</ul>
          {s.totalIssues === 0 && <p className="pj-empty" style={{ margin: 14 }}>Empty. Drag issues here from the backlog.</p>}
        </Card>
      ))}

      <Card pad={false} style={{ marginBottom: 16 }} onDragOver={e => e.preventDefault()} onDrop={() => drop(null)}>
        <div className="bl-head"><div className="bl-head-main"><b>Backlog</b><small>{backlog.length} issues · {backlog.reduce((n, i) => n + (i.storyPoints ?? 0), 0)} pts</small></div></div>
        <ul className="bl-list">{backlog.map(i => issueRow(i, true))}</ul>
        {backlog.length === 0 && <p className="pj-empty" style={{ margin: 14 }}>The backlog is empty.</p>}
      </Card>

      {done.length > 0 && (
        <Card>
          <b style={{ color: "var(--text-1)" }}>Completed sprints</b>
          {done.map(s => (
            <div key={s.id} className="bl-done">
              {s.name}: {s.completedCount ?? 0} done, {s.carriedOverCount ?? 0} carried over{s.completedAt ? ` · ${new Date(s.completedAt).toLocaleDateString()}` : ""}
            </div>
          ))}
        </Card>
      )}

      {form && (
        <SprintForm sprint={form.sprint} onClose={() => setForm(null)}
          onSubmit={async input => {
            if (form.sprint) await updateSprint(form.sprint.id, input); else await createSprint(projectId, input);
            setForm(null); await reload();
          }} />
      )}

      {completing && (
        <CompleteDialog sprint={completing} planned={sprints.filter(s => s.status === "PLANNED")}
          unfinished={completing.totalIssues - completing.doneIssues}
          onClose={() => setCompleting(null)}
          onConfirm={async target => { await completeSprint(completing.id, target); setCompleting(null); await reload(); }} />
      )}
    </div>
  );
}

function SprintForm(props: { sprint: Sprint | null; onClose: () => void; onSubmit: (i: SprintInput) => Promise<void> }) {
  const [f, setF] = useState<SprintInput>({
    name: props.sprint?.name ?? "", goal: props.sprint?.goal ?? "",
    startDate: props.sprint?.startDate ?? null, endDate: props.sprint?.endDate ?? null,
  });
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setErr(""); setBusy(true);
    try { await props.onSubmit({ ...f, name: f.name.trim(), goal: f.goal?.trim() || undefined, startDate: f.startDate || null, endDate: f.endDate || null }); }
    catch (error: any) { setErr(msg(error, "We couldn't save the sprint.")); setBusy(false); }
  };

  return (
    <Modal open onClose={props.onClose} dismissible={!busy} title={props.sprint ? "Edit sprint" : "New sprint"}
      description="Leave the dates empty to default to a 14-day sprint starting when you press Start."
      footer={<><Button variant="secondary" disabled={busy} onClick={props.onClose}>Cancel</Button><Button variant="primary" type="submit" form="sprint-form" loading={busy}>Save</Button></>}>
      <form id="sprint-form" onSubmit={submit} style={{ display: "grid", gap: 12 }}>
        {err && <Alert tone="error">{err}</Alert>}
        <Field label="Name" required><Input required maxLength={100} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} data-autofocus /></Field>
        <Field label="Goal"><Textarea rows={2} maxLength={500} value={f.goal || ""} onChange={e => setF({ ...f, goal: e.target.value })} /></Field>
        <div className="tn-form-grid">
          <Field label="Start date"><Input type="date" value={f.startDate || ""} onChange={e => setF({ ...f, startDate: e.target.value || null })} /></Field>
          <Field label="End date"><Input type="date" value={f.endDate || ""} onChange={e => setF({ ...f, endDate: e.target.value || null })} /></Field>
        </div>
      </form>
    </Modal>
  );
}

function CompleteDialog(props: {
  sprint: Sprint; planned: Sprint[]; unfinished: number;
  onClose: () => void; onConfirm: (target: number | null) => Promise<void>;
}) {
  const [target, setTarget] = useState<string>("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  const confirm = async () => {
    setErr(""); setBusy(true);
    try { await props.onConfirm(target ? Number(target) : null); }
    catch (error: any) { setErr(msg(error, "We couldn't complete the sprint.")); setBusy(false); }
  };

  return (
    <Modal open onClose={props.onClose} dismissible={!busy} size="sm" title={`Complete ${props.sprint.name}`}
      footer={<><Button variant="secondary" disabled={busy} onClick={props.onClose}>Cancel</Button><Button variant="primary" loading={busy} onClick={confirm}>Complete sprint</Button></>}>
      <div style={{ display: "grid", gap: 12 }}>
        {err && <Alert tone="error">{err}</Alert>}
        <p style={{ margin: 0, fontSize: 14, color: "var(--text-2)" }}>{props.sprint.doneIssues} issue(s) are done. {props.unfinished} unfinished issue(s) will move to:</p>
        <Select aria-label="Move unfinished issues to" value={target} onChange={e => setTarget(e.target.value)} data-autofocus>
          <option value="">Backlog</option>
          {props.planned.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </Select>
      </div>
    </Modal>
  );
}

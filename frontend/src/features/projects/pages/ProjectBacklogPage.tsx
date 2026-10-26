import { useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  assignToSprint, completeSprint, createSprint, deleteSprint, getProject, listIssues, listSprints,
  MANAGER_ROLES, startSprint, updateSprint,
} from "../services/projectService";
import type { Issue, Project, Sprint, SprintInput } from "../types/models";

const msg = (err: any, fallback: string) => err?.response?.data?.message || fallback;
const STATUS_COLOR = { PLANNED: "#64748b", ACTIVE: "#22c55e", COMPLETED: "#3b82f6" } as const;

export default function ProjectBacklogPage() {
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

  if (error && !project) return <div className="alert-ems alert-error">{error}</div>;
  if (!project) return <div style={{ color: "var(--text-3)" }}>Loading...</div>;

  const backlog = issues.filter(i => i.sprintId === null);
  const open = sprints.filter(s => s.status !== "COMPLETED");
  const done = sprints.filter(s => s.status === "COMPLETED").reverse();

  const issueRow = (i: Issue, movable: boolean) => (
    <div key={i.id} draggable={movable} onDragStart={() => setDragId(i.id)}
         style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 10px", borderBottom: "1px solid var(--border)", fontSize: 13, cursor: movable ? "grab" : "default" }}>
      <span style={{ color: "var(--text-3)", minWidth: 70 }}>{i.key}</span>
      <span style={{ flex: 1, color: "var(--text-1)" }}>{i.title}</span>
      {i.labels.map(l => <span key={l} style={{ fontSize: 11, padding: "1px 6px", borderRadius: 8, background: "var(--bg-surface)", color: "var(--text-2)" }}>{l}</span>)}
      <span style={{ color: "var(--text-3)", minWidth: 40, textAlign: "right" }}>{i.storyPoints !== null ? `${i.storyPoints} pt` : ""}</span>
      <span style={{ color: "var(--text-3)", minWidth: 90 }}>{i.status.replace("_", " ")}</span>
      {editable && movable && (
        <select className="form-control-ems" style={{ width: "auto", padding: "2px 6px" }} value={i.sprintId ?? ""}
                onChange={e => run(() => assignToSprint(i.id, e.target.value ? Number(e.target.value) : null), "Could not move the issue.")}>
          <option value="">Backlog</option>
          {open.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      )}
    </div>
  );

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title"><span style={{ color: "var(--accent-light)", marginRight: 8 }}>{project.key}</span>Backlog &amp; sprints</div>
          <div className="page-sub">Plan the work: drag issues into a sprint, start it, complete it when the time is up.</div>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button className="btn-ems btn-secondary-ems" onClick={() => navigate(`${base}/projects/${project.id}`)}>Board</button>
          {editable && <button className="btn-ems btn-primary-ems" onClick={() => setForm({ sprint: null })}>New sprint</button>}
        </div>
      </div>

      {error && <div className="alert-ems alert-error" onClick={() => setError("")}>{error}</div>}
      {archived && <div className="alert-ems">This project is archived, so planning is read-only.</div>}

      {open.map(s => (
        <div key={s.id} className="ems-card" style={{ marginBottom: 16, padding: 0 }}
             onDragOver={e => e.preventDefault()} onDrop={() => drop(s.id)}>
          <div style={{ padding: 14, display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap", borderBottom: "1px solid var(--border)" }}>
            <div style={{ flex: 1, minWidth: 220 }}>
              <b style={{ color: "var(--text-1)" }}>{s.name}</b>{" "}
              <span style={{ fontSize: 11, color: STATUS_COLOR[s.status], fontWeight: 700 }}>{s.status}</span>
              <div style={{ fontSize: 12, color: "var(--text-3)" }}>
                {s.startDate ? `${s.startDate} → ${s.endDate ?? "?"}` : "No dates yet"} · {s.doneIssues}/{s.totalIssues} done · {s.donePoints}/{s.totalPoints} pts
              </div>
              {s.goal && <div style={{ fontSize: 12, color: "var(--text-2)" }}>Goal: {s.goal}</div>}
            </div>
            {editable && s.status === "PLANNED" && <>
              <button className="btn-ems btn-secondary-ems" onClick={() => setForm({ sprint: s })}>Edit</button>
              <button className="btn-ems btn-secondary-ems" onClick={() => window.confirm(`Delete ${s.name}? Its issues return to the backlog.`) && run(() => deleteSprint(s.id), "Could not delete the sprint.")}>Delete</button>
              <button className="btn-ems btn-primary-ems" onClick={() => run(() => startSprint(s.id), "Could not start the sprint.")}>Start sprint</button>
            </>}
            {editable && s.status === "ACTIVE" && (
              <button className="btn-ems btn-primary-ems" onClick={() => setCompleting(s)}>Complete sprint</button>
            )}
          </div>
          {issues.filter(i => i.sprintId === s.id).map(i => issueRow(i, true))}
          {s.totalIssues === 0 && <div style={{ padding: 14, fontSize: 13, color: "var(--text-3)" }}>Empty. Drag issues here from the backlog.</div>}
        </div>
      ))}

      <div className="ems-card" style={{ marginBottom: 16, padding: 0 }} onDragOver={e => e.preventDefault()} onDrop={() => drop(null)}>
        <div style={{ padding: 14, borderBottom: "1px solid var(--border)" }}>
          <b style={{ color: "var(--text-1)" }}>Backlog</b>{" "}
          <span style={{ fontSize: 12, color: "var(--text-3)" }}>{backlog.length} issues · {backlog.reduce((n, i) => n + (i.storyPoints ?? 0), 0)} pts</span>
        </div>
        {backlog.map(i => issueRow(i, true))}
        {backlog.length === 0 && <div style={{ padding: 14, fontSize: 13, color: "var(--text-3)" }}>The backlog is empty.</div>}
      </div>

      {done.length > 0 && (
        <div className="ems-card" style={{ padding: 14 }}>
          <b style={{ color: "var(--text-1)" }}>Completed sprints</b>
          {done.map(s => (
            <div key={s.id} style={{ fontSize: 13, color: "var(--text-2)", marginTop: 8 }}>
              {s.name}: {s.completedCount ?? 0} done, {s.carriedOverCount ?? 0} carried over
              {s.completedAt ? ` · ${new Date(s.completedAt).toLocaleDateString()}` : ""}
            </div>
          ))}
        </div>
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
    catch (error: any) { setErr(msg(error, "Could not save the sprint.")); setBusy(false); }
  };

  return (
    <div className="ems-modal-backdrop" onClick={props.onClose}>
      <form className="ems-modal" style={{ width: 480 }} onClick={e => e.stopPropagation()} onSubmit={submit}>
        <div className="modal-title">{props.sprint ? "Edit sprint" : "New sprint"}</div>
        {err && <div className="alert-ems alert-error">{err}</div>}
        <div className="form-group">
          <label className="form-label">Name *</label>
          <input className="form-control-ems" required maxLength={100} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} />
        </div>
        <div className="form-group">
          <label className="form-label">Goal</label>
          <textarea className="form-control-ems" rows={2} maxLength={500} value={f.goal || ""} onChange={e => setF({ ...f, goal: e.target.value })} />
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 14px" }}>
          <div className="form-group">
            <label className="form-label">Start date</label>
            <input type="date" className="form-control-ems" value={f.startDate || ""} onChange={e => setF({ ...f, startDate: e.target.value || null })} />
          </div>
          <div className="form-group">
            <label className="form-label">End date</label>
            <input type="date" className="form-control-ems" value={f.endDate || ""} onChange={e => setF({ ...f, endDate: e.target.value || null })} />
          </div>
        </div>
        <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 12 }}>Leave the dates empty to default to a 14-day sprint starting when you press Start.</div>
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button type="button" className="btn-ems btn-secondary-ems" onClick={props.onClose}>Cancel</button>
          <button type="submit" className="btn-ems btn-primary-ems" disabled={busy}>Save</button>
        </div>
      </form>
    </div>
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
    catch (error: any) { setErr(msg(error, "Could not complete the sprint.")); setBusy(false); }
  };

  return (
    <div className="ems-modal-backdrop" onClick={props.onClose}>
      <div className="ems-modal" style={{ width: 460 }} onClick={e => e.stopPropagation()}>
        <div className="modal-title">Complete {props.sprint.name}</div>
        {err && <div className="alert-ems alert-error">{err}</div>}
        <p style={{ fontSize: 14, color: "var(--text-2)" }}>
          {props.sprint.doneIssues} issue(s) are done. {props.unfinished} unfinished issue(s) will move to:
        </p>
        <select className="form-control-ems" value={target} onChange={e => setTarget(e.target.value)} style={{ marginBottom: 16 }}>
          <option value="">Backlog</option>
          {props.planned.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
          <button className="btn-ems btn-secondary-ems" onClick={props.onClose}>Cancel</button>
          <button className="btn-ems btn-primary-ems" disabled={busy} onClick={confirm}>Complete sprint</button>
        </div>
      </div>
    </div>
  );
}

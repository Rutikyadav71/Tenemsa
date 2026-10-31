import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  approveMeeting, createIssuesFromMeeting, deleteMeeting, getMeeting, updateMeeting,
} from "../services/meetingService";
import { listPeople, listProjects } from "../../projects/services/projectService";
import type {
  IssuePriority, MeetingActionItem, MeetingDetail, Person, Project,
} from "../../../shared/types/models";

const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const msg = (err: any, fallback: string) => err?.response?.data?.message || fallback;

export default function MeetingDetailPage() {
  const { id } = useParams();
  const meetingId = Number(id);
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";

  const [meeting, setMeeting] = useState<MeetingDetail | null>(null);
  const [people, setPeople] = useState<Person[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [showNotes, setShowNotes] = useState(false);

  // editable copy (only used while DRAFT)
  const [title, setTitle] = useState("");
  const [meetingDate, setMeetingDate] = useState("");
  const [summary, setSummary] = useState("");
  const [decisions, setDecisions] = useState<string[]>([]);
  const [questions, setQuestions] = useState<string[]>([]);
  const [items, setItems] = useState<MeetingActionItem[]>([]);

  // issue creation (only when APPROVED)
  const [projectId, setProjectId] = useState("");
  const [selected, setSelected] = useState<number[]>([]);

  const adopt = (m: MeetingDetail) => {
    setMeeting(m);
    setTitle(m.title); setMeetingDate(m.meetingDate || ""); setSummary(m.summary);
    setDecisions(m.decisions); setQuestions(m.openQuestions); setItems(m.actionItems);
    setSelected(m.actionItems.filter(a => a.issueId === null && a.id !== null).map(a => a.id as number));
    if (m.projectId) setProjectId(String(m.projectId));
  };

  useEffect(() => {
    getMeeting(meetingId).then(adopt)
      .catch(err => setError(err.response?.status === 404 ? "Meeting summary not found." : msg(err, "Could not load the summary.")));
    listPeople().then(setPeople).catch(() => {});
    listProjects().then(p => setProjects(p.filter(x => x.status === "ACTIVE"))).catch(() => {});
  }, [meetingId]);

  if (error && !meeting) return <div className="alert-ems alert-error">{error}</div>;
  if (!meeting) return <div style={{ color: "var(--text-3)" }}>Loading...</div>;

  const draft = meeting.status === "DRAFT";

  const payload = () => ({
    title: title.trim(), meetingDate: meetingDate || null, summary: summary.trim(),
    decisions: decisions.map(d => d.trim()).filter(Boolean),
    openQuestions: questions.map(q => q.trim()).filter(Boolean),
    actionItems: items.filter(a => a.title.trim()).map(a => ({
      id: a.id, title: a.title.trim(), description: a.description?.trim() || undefined,
      ownerName: a.ownerName?.trim() || undefined, ownerEmpId: a.ownerEmpId,
      dueDate: a.dueDate || null, priority: a.priority,
    })),
  });

  const run = async (fn: () => Promise<void>, fallback: string) => {
    setError(""); setNotice(""); setBusy(true);
    try { await fn(); } catch (err: any) { setError(msg(err, fallback)); } finally { setBusy(false); }
  };

  const save = () => run(async () => { adopt(await updateMeeting(meeting.id, payload())); setNotice("Saved."); }, "Could not save.");

  const approve = () => run(async () => {
    if (!window.confirm("Approve this summary? It will be locked and its tasks can then be created as issues.")) return;
    await updateMeeting(meeting.id, payload());
    adopt(await approveMeeting(meeting.id));
    setNotice("Approved. Choose a project below to create issues from the action items.");
  }, "Could not approve.");

  const remove = () => run(async () => {
    if (!window.confirm("Delete this summary and its original notes? This cannot be undone.")) return;
    await deleteMeeting(meeting.id);
    navigate(`${base}/meetings`);
  }, "Could not delete.");

  const create = () => run(async () => {
    if (!projectId) { setError("Choose a project first."); return; }
    if (selected.length === 0) { setError("Select at least one action item."); return; }
    const res = await createIssuesFromMeeting(meeting.id, Number(projectId), selected);
    adopt(await getMeeting(meeting.id));
    setNotice(`Created ${res.created.length} issue(s): ${res.created.map(c => c.issueKey).join(", ")}.`);
  }, "Could not create issues.");

  const setItem = (i: number, patch: Partial<MeetingActionItem>) =>
    setItems(items.map((a, k) => (k === i ? { ...a, ...patch } : a)));

  const list = (values: string[], set: (v: string[]) => void, placeholder: string) => (
    <div>
      {values.map((v, i) => (
        <div key={i} style={{ display: "flex", gap: 8, marginBottom: 6 }}>
          <input className="form-control-ems" maxLength={1000} value={v} placeholder={placeholder}
                 onChange={e => set(values.map((x, k) => (k === i ? e.target.value : x)))} />
          <button type="button" className="btn-ems btn-secondary-ems" onClick={() => set(values.filter((_, k) => k !== i))}>Remove</button>
        </div>
      ))}
      <button type="button" className="btn-ems btn-secondary-ems" onClick={() => set([...values, ""])}>Add</button>
    </div>
  );

  const heading = (t: string) => <div style={{ fontWeight: 600, color: "var(--text-1)", margin: "18px 0 8px" }}>{t}</div>;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">{meeting.title}</div>
          <div className="page-sub">
            {draft ? "AI draft: review and correct it before approving." : `Approved${meeting.approvedAt ? " on " + new Date(meeting.approvedAt).toLocaleDateString() : ""}.`}
            {meeting.model ? ` Generated with ${meeting.model}.` : ""}
          </div>
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button className="btn-ems btn-secondary-ems" onClick={() => navigate(`${base}/meetings`)}>All summaries</button>
          <button className="btn-ems btn-secondary-ems" disabled={busy} onClick={remove}>Delete</button>
          {draft && <button className="btn-ems btn-secondary-ems" disabled={busy} onClick={save}>Save changes</button>}
          {draft && <button className="btn-ems btn-primary-ems" disabled={busy} onClick={approve}>Approve</button>}
        </div>
      </div>

      {error && <div className="alert-ems alert-error" onClick={() => setError("")}>{error}</div>}
      {notice && <div className="alert-ems" onClick={() => setNotice("")}>{notice}</div>}
      {draft && (
        <div className="alert-ems">
          AI can make mistakes. Check names, dates and tasks against the original notes before approving.
        </div>
      )}

      <div className="ems-card" style={{ padding: 18 }}>
        {draft ? (
          <>
            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "0 14px" }}>
              <div className="form-group">
                <label className="form-label">Title</label>
                <input className="form-control-ems" maxLength={200} value={title} onChange={e => setTitle(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="form-label">Meeting date</label>
                <input type="date" className="form-control-ems" value={meetingDate} onChange={e => setMeetingDate(e.target.value)} />
              </div>
            </div>
            <label className="form-label">Summary</label>
            <textarea className="form-control-ems" rows={5} maxLength={4000} value={summary} onChange={e => setSummary(e.target.value)} />
            {heading("Decisions")}
            {list(decisions, setDecisions, "A decision that was made")}
            {heading("Open questions")}
            {list(questions, setQuestions, "A question nobody resolved")}
          </>
        ) : (
          <>
            <div style={{ color: "var(--text-2)", whiteSpace: "pre-wrap" }}>{meeting.summary}</div>
            {heading("Decisions")}
            {meeting.decisions.length === 0 ? <div style={{ color: "var(--text-3)" }}>None recorded.</div>
              : <ul style={{ color: "var(--text-2)" }}>{meeting.decisions.map((d, i) => <li key={i}>{d}</li>)}</ul>}
            {heading("Open questions")}
            {meeting.openQuestions.length === 0 ? <div style={{ color: "var(--text-3)" }}>None recorded.</div>
              : <ul style={{ color: "var(--text-2)" }}>{meeting.openQuestions.map((q, i) => <li key={i}>{q}</li>)}</ul>}
          </>
        )}

        {heading(`Action items (${items.length})`)}
        {draft ? (
          <div>
            {items.map((a, i) => (
              <div key={a.id ?? `new-${i}`} className="ems-card" style={{ padding: 12, marginBottom: 10 }}>
                <input className="form-control-ems" placeholder="Task title" maxLength={200} value={a.title}
                       onChange={e => setItem(i, { title: e.target.value })} style={{ marginBottom: 8 }} />
                <textarea className="form-control-ems" rows={2} placeholder="Details (optional)" maxLength={2000}
                          value={a.description || ""} onChange={e => setItem(i, { description: e.target.value })} style={{ marginBottom: 8 }} />
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 8, alignItems: "end" }}>
                  <div>
                    <label className="form-label">Owner</label>
                    <select className="form-control-ems" value={a.ownerEmpId ?? ""}
                            onChange={e => {
                              const emp = e.target.value ? Number(e.target.value) : null;
                              setItem(i, { ownerEmpId: emp, ownerName: people.find(p => p.empId === emp)?.name ?? a.ownerName });
                            }}>
                      <option value="">{a.ownerName ? `Not matched: ${a.ownerName}` : "Unassigned"}</option>
                      {people.map(p => <option key={p.empId} value={p.empId}>{p.name}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="form-label">Due date</label>
                    <input type="date" className="form-control-ems" value={a.dueDate || ""} onChange={e => setItem(i, { dueDate: e.target.value || null })} />
                  </div>
                  <div>
                    <label className="form-label">Priority</label>
                    <select className="form-control-ems" value={a.priority} onChange={e => setItem(i, { priority: e.target.value as IssuePriority })}>
                      {PRIORITIES.map(p => <option key={p}>{p}</option>)}
                    </select>
                  </div>
                  <button type="button" className="btn-ems btn-secondary-ems" onClick={() => setItems(items.filter((_, k) => k !== i))}>Remove</button>
                </div>
              </div>
            ))}
            <button type="button" className="btn-ems btn-secondary-ems"
                    onClick={() => setItems([...items, { id: null, title: "", description: "", ownerName: null, ownerEmpId: null, dueDate: null, priority: "MEDIUM", issueId: null, issueKey: null }])}>
              Add action item
            </button>
          </div>
        ) : (
          <div>
            {items.length === 0 && <div style={{ color: "var(--text-3)" }}>No action items.</div>}
            {items.map(a => (
              <label key={a.id ?? a.title} style={{ display: "flex", gap: 10, alignItems: "flex-start", padding: "8px 0", borderBottom: "1px solid var(--border)", fontSize: 14 }}>
                {a.issueId === null ? (
                  <input type="checkbox" style={{ marginTop: 4 }} checked={a.id !== null && selected.includes(a.id)}
                         onChange={e => setSelected(e.target.checked ? [...selected, a.id as number] : selected.filter(x => x !== a.id))} />
                ) : <span style={{ width: 13 }} />}
                <span style={{ flex: 1 }}>
                  <span style={{ color: "var(--text-1)" }}>{a.title}</span>
                  <span style={{ display: "block", fontSize: 12, color: "var(--text-3)" }}>
                    {a.ownerName || "No owner"} · {a.priority}{a.dueDate ? ` · due ${a.dueDate}` : ""}
                  </span>
                </span>
                {a.issueKey && (
                  <span style={{ color: "#22c55e", fontSize: 12, cursor: "pointer" }}
                        onClick={() => meeting.projectId && navigate(`${base}/projects/${meeting.projectId}`)}>{a.issueKey}</span>
                )}
              </label>
            ))}
            {items.some(a => a.issueId === null) && (
              <div style={{ display: "flex", gap: 10, marginTop: 14, flexWrap: "wrap", alignItems: "center" }}>
                <select className="form-control-ems" style={{ maxWidth: 260 }} value={projectId} onChange={e => setProjectId(e.target.value)}>
                  <option value="">Choose a project…</option>
                  {projects.map(p => <option key={p.id} value={p.id}>{p.key} · {p.name}</option>)}
                </select>
                <button className="btn-ems btn-primary-ems" disabled={busy} onClick={create}>Create {selected.length} issue(s)</button>
              </div>
            )}
          </div>
        )}

        <div style={{ marginTop: 20 }}>
          <button type="button" className="btn-ems btn-secondary-ems" onClick={() => setShowNotes(!showNotes)}>
            {showNotes ? "Hide original notes" : "Show original notes"}
          </button>
          {meeting.sourceRef && <span style={{ marginLeft: 10, fontSize: 12, color: "var(--text-3)" }}>Source: {meeting.sourceRef}</span>}
          {showNotes && (
            <pre style={{ whiteSpace: "pre-wrap", marginTop: 10, padding: 12, background: "var(--bg-elevated)", borderRadius: 8, fontSize: 13, color: "var(--text-2)" }}>
              {meeting.sourceText}
            </pre>
          )}
        </div>
      </div>
    </div>
  );
}

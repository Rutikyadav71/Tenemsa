import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import {
  approveMeeting, createIssuesFromMeeting, deleteMeeting, getMeeting, updateMeeting,
} from "../services/meetingService";
import { listPeople, listProjects } from "../../projects/services/projectService";
import type {
  IssuePriority, MeetingActionItem, MeetingDetail, Person, Project,
} from "../../../shared/types/models";
import { Alert, Badge, Button, Card, ErrorState, Field, Input, PageHeader, Select, SkeletonList, Textarea, useConfirm } from "../../../shared/ui";
import "./meetings.css";

const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const msg = (err: any, fallback: string) => err?.response?.data?.message || fallback;

export default function MeetingDetailPage() {
  const confirmAction = useConfirm();
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

  if (error && !meeting) return <ErrorState message={error} onRetry={() => navigate(`${base}/meetings`)} />;
  if (!meeting) return <SkeletonList rows={4} />;

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
    if (!(await confirmAction({ title: "Approve this summary?", message: "It will be locked, and its action items can then be created as issues.", confirmLabel: "Approve", tone: "primary" }))) return;
    await updateMeeting(meeting.id, payload());
    adopt(await approveMeeting(meeting.id));
    setNotice("Approved. Choose a project below to create issues from the action items.");
  }, "Could not approve.");

  const remove = () => run(async () => {
    if (!(await confirmAction({ title: "Delete this summary?", message: "The summary and its original notes will be deleted. This cannot be undone.", confirmLabel: "Delete" }))) return;
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

  const list = (values: string[], set: (v: string[]) => void, placeholder: string, label: string) => (
    <div className="md-list">
      {values.map((v, i) => (
        <div key={i} className="md-line">
          <Input aria-label={`${label} ${i + 1}`} maxLength={1000} value={v} placeholder={placeholder} onChange={e => set(values.map((x, k) => (k === i ? e.target.value : x)))} />
          <Button size="sm" onClick={() => set(values.filter((_, k) => k !== i))}>Remove</Button>
        </div>
      ))}
      <div><Button size="sm" onClick={() => set([...values, ""])}>Add</Button></div>
    </div>
  );
  const heading = (t: string) => <h3 className="md-h">{t}</h3>;

  return (
    <div>
      <PageHeader title={meeting.title}
        subtitle={<>{draft ? "Draft from Tenemsa AI: review and correct it before approving." : `Approved${meeting.approvedAt ? " on " + new Date(meeting.approvedAt).toLocaleDateString() : ""}.`}</>}
        actions={<>
          <Button onClick={() => navigate(`${base}/meetings`)}>All summaries</Button>
          <Button variant="danger" disabled={busy} onClick={remove}>Delete</Button>
          {draft && <Button disabled={busy} onClick={save}>Save changes</Button>}
          {draft && <Button variant="primary" loading={busy} onClick={approve}>Approve</Button>}
        </>} />

      <div style={{ display: "grid", gap: 10, marginBottom: 16 }}>
        {error && <Alert tone="error">{error}</Alert>}
        {notice && <Alert tone="success">{notice}</Alert>}
        {draft && <Alert tone="info">AI can make mistakes. Check names, dates and tasks against the original notes before approving.</Alert>}
      </div>

      <Card>
        {draft ? (
          <>
            <div className="tn-form-grid">
              <Field label="Title"><Input maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></Field>
              <Field label="Meeting date"><Input type="date" value={meetingDate} onChange={e => setMeetingDate(e.target.value)} /></Field>
            </div>
            <Field label="Summary"><Textarea rows={5} maxLength={4000} value={summary} onChange={e => setSummary(e.target.value)} /></Field>
            {heading("Decisions")}
            {list(decisions, setDecisions, "A decision that was made", "Decision")}
            {heading("Open questions")}
            {list(questions, setQuestions, "A question nobody resolved", "Open question")}
          </>
        ) : (
          <>
            <p className="md-summary">{meeting.summary}</p>
            {heading("Decisions")}
            {meeting.decisions.length === 0 ? <p className="mt-muted">None recorded.</p> : <ul className="md-ul">{meeting.decisions.map((d, i) => <li key={i}>{d}</li>)}</ul>}
            {heading("Open questions")}
            {meeting.openQuestions.length === 0 ? <p className="mt-muted">None recorded.</p> : <ul className="md-ul">{meeting.openQuestions.map((q, i) => <li key={i}>{q}</li>)}</ul>}
          </>
        )}

        {heading(`Action items (${items.length})`)}
        {draft ? (
          <div style={{ display: "grid", gap: 10 }}>
            {items.map((a, i) => (
              <Card key={a.id ?? `new-${i}`} className="md-item">
                <div style={{ display: "grid", gap: 8 }}>
                  <Input aria-label="Task title" placeholder="Task title" maxLength={200} value={a.title} onChange={e => setItem(i, { title: e.target.value })} />
                  <Textarea aria-label="Details" rows={2} placeholder="Details (optional)" maxLength={2000} value={a.description || ""} onChange={e => setItem(i, { description: e.target.value })} />
                  <div className="md-item-row">
                    <Field label="Owner">
                      <Select value={a.ownerEmpId ?? ""} onChange={e => {
                        const emp = e.target.value ? Number(e.target.value) : null;
                        setItem(i, { ownerEmpId: emp, ownerName: people.find(p => p.empId === emp)?.name ?? a.ownerName });
                      }}>
                        <option value="">{a.ownerName ? `Not matched: ${a.ownerName}` : "Unassigned"}</option>
                        {people.map(p => <option key={p.empId} value={p.empId}>{p.name}</option>)}
                      </Select>
                    </Field>
                    <Field label="Due date"><Input type="date" value={a.dueDate || ""} onChange={e => setItem(i, { dueDate: e.target.value || null })} /></Field>
                    <Field label="Priority"><Select value={a.priority} onChange={e => setItem(i, { priority: e.target.value as IssuePriority })}>{PRIORITIES.map(p => <option key={p}>{p}</option>)}</Select></Field>
                    <Button size="sm" onClick={() => setItems(items.filter((_, k) => k !== i))}>Remove</Button>
                  </div>
                </div>
              </Card>
            ))}
            <div><Button onClick={() => setItems([...items, { id: null, title: "", description: "", ownerName: null, ownerEmpId: null, dueDate: null, priority: "MEDIUM", issueId: null, issueKey: null }])}>Add action item</Button></div>
          </div>
        ) : (
          <div>
            {items.length === 0 && <p className="mt-muted">No action items.</p>}
            {items.map(a => (
              <label key={a.id ?? a.title} className="md-ai">
                {a.issueId === null
                  ? <input type="checkbox" checked={a.id !== null && selected.includes(a.id)} onChange={e => setSelected(e.target.checked ? [...selected, a.id as number] : selected.filter(x => x !== a.id))} />
                  : <span style={{ width: 16 }} />}
                <span className="md-ai-main"><b>{a.title}</b><small>{a.ownerName || "No owner"} · {a.priority}{a.dueDate ? ` · due ${a.dueDate}` : ""}</small></span>
                {a.issueKey && (
                  <button type="button" className="tn-link" style={{ background: "none", border: 0, cursor: "pointer" }}
                    onClick={() => meeting.projectId && navigate(`${base}/projects/${meeting.projectId}`)}><Badge tone="success">{a.issueKey}</Badge></button>
                )}
              </label>
            ))}
            {items.some(a => a.issueId === null) && (
              <div className="md-create">
                <Select aria-label="Project" value={projectId} onChange={e => setProjectId(e.target.value)}>
                  <option value="">Choose a project…</option>
                  {projects.map(p => <option key={p.id} value={p.id}>{p.key} · {p.name}</option>)}
                </Select>
                <Button variant="primary" loading={busy} onClick={create}>Create {selected.length} issue{selected.length === 1 ? "" : "s"}</Button>
              </div>
            )}
          </div>
        )}

        <div style={{ marginTop: 20 }}>
          <Button size="sm" onClick={() => setShowNotes(!showNotes)}>{showNotes ? "Hide original notes" : "Show original notes"}</Button>
          {meeting.sourceRef && <span className="mt-muted" style={{ marginLeft: 10 }}>Source: {meeting.sourceRef}</span>}
          {showNotes && <pre className="md-pre">{meeting.sourceText}</pre>}
        </div>
      </Card>
    </div>
  );
}

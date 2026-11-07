import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { LuSend, LuSparkles } from "react-icons/lu";
import { Alert, Badge, Button, Card, CardHeader, EmptyState, Field, Input, PageHeader, Pagination, Select, Textarea, useConfirm, useToast } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";
import { getEmployees } from "../../employees/services/EmployeeService";
import { DEPARTMENTS } from "../../employees/components/EmployeeForm";
import type { User } from "../../../shared/types/models";
import { getAnnouncements, previewAudience, sendAnnouncement, type AnnouncementInput, type AnnouncementRow, type AudienceType, type Paged, type Preview } from "../services/announcementService";
import "../announcements.css";

const ROLES: [string, string][] = [["OWNER", "Owners"], ["ADMIN", "Admins"], ["HR", "HR"], ["MANAGER", "Managers"], ["EMPLOYEE", "Employees"], ["INTERN", "Interns"]];

const when = (s: string) => new Date(s).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

export default function AnnouncementsPage() {
  const toast = useToast();
  const confirm = useConfirm();
  const base = `/${useLocation().pathname.split("/")[1]}`;
  const draft = (useLocation().state ?? {}) as { subject?: string; body?: string };

  const [subject, setSubject] = useState(draft.subject ?? "");
  const [body, setBody] = useState(draft.body ?? "");
  const [audience, setAudience] = useState<AudienceType>("ALL");
  const [roles, setRoles] = useState<string[]>([]);
  const [depts, setDepts] = useState<string[]>([]);
  const [empIds, setEmpIds] = useState<number[]>([]);
  const [sendEmail, setSendEmail] = useState(true);
  const [people, setPeople] = useState<User[]>([]);
  const [find, setFind] = useState("");
  const [preview, setPreview] = useState<Preview | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [hist, setHist] = useState<Paged<AnnouncementRow> | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => { getEmployees().then(r => setPeople(Array.isArray(r.data) ? r.data : [])).catch(() => {}); }, []);
  const loadHistory = useCallback((p = page) => { getAnnouncements(p).then(setHist).catch(() => setHist({ content: [], page: 0, totalPages: 0, totalElements: 0 })); }, [page]);
  useEffect(() => { loadHistory(page); }, [page, loadHistory]);
  // Rows still sending their emails refresh themselves a few times.
  useEffect(() => {
    if (!hist?.content.some(h => h.status === "SENDING")) return;
    const t = setTimeout(() => loadHistory(page), 4000);
    return () => clearTimeout(t);
  }, [hist, page, loadHistory]);

  const departments = useMemo(() => Array.from(new Set([...DEPARTMENTS, ...people.map(p => p.department || "").filter(Boolean)])).sort(), [people]);
  const shownPeople = useMemo(() => people.filter(p => !p.frozen && p.empId != null && (p.name + " " + p.email).toLowerCase().includes(find.toLowerCase())), [people, find]);

  const input = (): AnnouncementInput => ({ subject: subject.trim(), body: body.trim(), audience, roles, departments: depts, empIds, sendEmail });
  const ready = audience === "ALL" || (audience === "ROLES" && roles.length > 0) || (audience === "DEPARTMENTS" && depts.length > 0) || (audience === "PEOPLE" && empIds.length > 0);

  useEffect(() => {
    if (!ready) { setPreview(null); return; }
    const t = setTimeout(() => { previewAudience(input()).then(setPreview).catch(() => setPreview(null)); }, 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [audience, roles, depts, empIds, ready]);

  const toggle = <T,>(list: T[], set: (v: T[]) => void, v: T) => set(list.includes(v) ? list.filter(x => x !== v) : [...list, v]);

  const send = async () => {
    setError("");
    if (subject.trim().length < 3) return setError("Add a subject.");
    if (body.trim().length < 5) return setError("Write a message.");
    if (!ready) return setError("Choose who should receive it.");
    const n = preview?.count;
    const ok = await confirm({
      title: "Send this announcement?",
      message: `${n ?? "These"} ${n === 1 ? "person" : "people"} will get it in Tenemsa${sendEmail ? " and by email" : ""}. It can't be recalled once sent.`,
      confirmLabel: "Send now",
    });
    if (!ok) return;
    setSending(true);
    try {
      const r = await sendAnnouncement(input());
      toast.success(`Sent to ${r.recipients} ${r.recipients === 1 ? "person" : "people"}${r.emailing ? ". Emails are going out now." : "."}`);
      setSubject(""); setBody(""); setPage(0); loadHistory(0);
    } catch (e) { setError(errorMessage(e, "We couldn't send it. Please try again.")); }
    finally { setSending(false); }
  };

  return (
    <div>
      <PageHeader title="Announcements" subtitle="Send a message to everyone, to certain roles or departments, or to chosen people. They get it in Tenemsa and, if you choose, by email." />
      <div className="an-grid">
        <Card>
          <CardHeader title="New announcement" actions={<Link className="au-link" to={`${base}/writer`}><LuSparkles size={14} /> Draft with AI Writer</Link>} />
          {error && <Alert tone="error">{error}</Alert>}
          <div style={{ display: "grid", gap: 14 }}>
            <Field label="Subject" required><Input value={subject} maxLength={200} onChange={e => setSubject(e.target.value)} /></Field>
            <Field label="Message" required hint={`${body.length} / 5000 · Plain text. Blank lines start a new paragraph.`}><Textarea rows={9} maxLength={5000} value={body} onChange={e => setBody(e.target.value)} /></Field>
            <Field label="Send to">
              <Select value={audience} onChange={e => setAudience(e.target.value as AudienceType)}>
                <option value="ALL">Everyone in the company</option><option value="ROLES">Certain roles</option><option value="DEPARTMENTS">Certain departments</option><option value="PEOPLE">Chosen people</option>
              </Select>
            </Field>
            {audience === "ROLES" && <div className="an-checks" role="group" aria-label="Roles">{ROLES.map(([v, l]) => <label key={v}><input type="checkbox" checked={roles.includes(v)} onChange={() => toggle(roles, setRoles, v)} /> {l}</label>)}</div>}
            {audience === "DEPARTMENTS" && <div className="an-checks" role="group" aria-label="Departments">{departments.map(d => <label key={d}><input type="checkbox" checked={depts.includes(d)} onChange={() => toggle(depts, setDepts, d)} /> {d}</label>)}</div>}
            {audience === "PEOPLE" && (
              <div style={{ display: "grid", gap: 8 }}>
                <Input type="search" placeholder="Search by name or email" aria-label="Search people" value={find} onChange={e => setFind(e.target.value)} />
                <div className="an-people">
                  {shownPeople.map(p => <label key={p.id}><input type="checkbox" checked={empIds.includes(p.empId as number)} onChange={() => toggle(empIds, setEmpIds, p.empId as number)} /> {p.name} <small>{p.department || p.role}</small></label>)}
                  {shownPeople.length === 0 && <small>No one found.</small>}
                </div>
                <span className="ai-muted">{empIds.length} chosen</span>
              </div>
            )}
            <label className="an-checks" style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 14 }}><input type="checkbox" checked={sendEmail} onChange={e => setSendEmail(e.target.checked)} /> Also send by email</label>
            {ready && preview && (
              <p className="an-reach" role="status">
                {preview.tooMany ? <b>That is too many people for one announcement. Choose a smaller audience.</b> : <>Reaches <b>{preview.count}</b> {preview.count === 1 ? "person" : "people"}{preview.sample.length > 0 && <> such as {preview.sample.slice(0, 3).join(", ")}</>}.</>}
              </p>
            )}
            <div className="tn-form-actions" style={{ justifyContent: "flex-start" }}>
              <Button variant="primary" icon={<LuSend size={15} />} loading={sending} disabled={!ready || preview?.tooMany || preview?.count === 0} onClick={send}>Review and send</Button>
            </div>
          </div>
        </Card>

        <Card>
          <CardHeader title="Sent announcements" />
          {!hist ? <p className="ai-muted">Loading…</p> : hist.content.length === 0 ? <EmptyState title="Nothing sent yet" text="Announcements you send will be listed here." icon={<LuSend size={22} />} /> : (
            <>
              {hist.content.map(h => (
                <div key={h.id} className="an-item">
                  <h3>{h.subject}</h3>
                  <div className="an-meta"><span>{h.audience}</span><span>{when(h.createdAt)}</span><span>by {h.sentBy}</span></div>
                  <div className="an-meta">
                    {h.sendEmail ? (h.status === "SENDING" ? <Badge tone="info">Sending emails…</Badge> : <Badge tone={h.emailsFailed ? "warning" : "success"}>{h.emailsSent} emailed{h.emailsFailed ? `, ${h.emailsFailed} failed` : ""}</Badge>) : <Badge>In Tenemsa only</Badge>}
                  </div>
                </div>
              ))}
              <Pagination page={hist.page} pageCount={hist.totalPages} total={hist.totalElements} onPage={setPage} label="announcements" />
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

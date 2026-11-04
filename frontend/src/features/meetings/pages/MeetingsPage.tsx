import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { LuPlus, LuClipboardList } from "react-icons/lu";
import { getAiStatus } from "../../ai/services/aiService";
import { generateMeeting, listMeetings } from "../services/meetingService";
import type { AiStatus, MeetingListItem } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, SkeletonList, Textarea } from "../../../shared/ui";
import "./meetings.css";

const MAX_NOTES = 20000;

export default function MeetingsPage() {
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";

  const [items, setItems] = useState<MeetingListItem[] | null>(null);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [failed, setFailed] = useState(false);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [form, setForm] = useState({ title: "", meetingDate: "", sourceRef: "", notes: "" });

  const load = useCallback(() => {
    setFailed(false);
    listMeetings().then(setItems).catch(() => setFailed(true));
  }, []);
  useEffect(() => { load(); getAiStatus().then(setStatus).catch(() => {}); }, [load]);

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError(""); setBusy(true);
    try {
      const created = await generateMeeting({
        title: form.title.trim(), meetingDate: form.meetingDate || null, sourceRef: form.sourceRef.trim() || undefined, notes: form.notes,
      });
      navigate(`${base}/meetings/${created.id}`);
    } catch (err: any) {
      setFormError(err.response?.data?.message || (err.code === "ECONNABORTED" ? "Tenemsa AI took too long. Please try again." : "We couldn't create the summary."));
      setBusy(false);
      getAiStatus().then(setStatus).catch(() => {});
    }
  };

  const off = status !== null && !status.enabled;

  return (
    <div>
      <PageHeader title="Meeting summaries" subtitle="Paste meeting notes, let Tenemsa AI draft a summary and tasks, then review before anything is created."
        actions={<Button variant="primary" icon={<LuPlus size={16} />} disabled={off} onClick={() => setCreating(true)}>New summary</Button>} />
      {off && (
        <Alert tone="warning">
          {status && !status.companyEnabled
            ? "AI is switched off for your company. An owner or admin can turn it on in Settings."
            : "Tenemsa AI isn't available right now. Please contact your administrator."}
        </Alert>
      )}
      {status?.enabled && <p className="mt-muted">{status.requestsLeftThisHour} Tenemsa AI requests left this hour for you</p>}

      {failed ? <ErrorState message="We couldn't load the meeting summaries." onRetry={load} />
        : items === null ? <SkeletonList rows={3} />
        : items.length === 0 ? <EmptyState icon={<LuClipboardList size={22} />} title="No summaries yet" text="Create one from your meeting notes."
            action={!off ? <Button variant="primary" onClick={() => setCreating(true)}>New summary</Button> : undefined} />
        : (
          <Card pad={false}>
            <ul className="tn-list">
              {items.map(m => (
                <li key={m.id}>
                  <button className="mt-row" onClick={() => navigate(`${base}/meetings/${m.id}`)}>
                    <span className="mt-main">
                      <b>{m.title}</b>
                      <small>{m.meetingDate || new Date(m.createdAt).toLocaleDateString()}{m.createdByName ? ` · ${m.createdByName}` : ""}</small>
                    </span>
                    <span className="mt-side">
                      <small>{m.convertedCount}/{m.actionItemCount} tasks created</small>
                      <Badge tone={m.status === "APPROVED" ? "success" : "warning"}>{m.status === "APPROVED" ? "Approved" : "Draft: needs review"}</Badge>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </Card>
        )}

      <Modal open={creating} onClose={() => setCreating(false)} dismissible={!busy} size="lg" title="New meeting summary"
        description="Tenemsa AI reads the notes to write the summary. Don't paste anything you aren't allowed to share."
        footer={<><Button variant="secondary" disabled={busy} onClick={() => setCreating(false)}>Cancel</Button>
          <Button variant="primary" type="submit" form="meeting-form" loading={busy}>{busy ? "Summarising… (up to a minute)" : "Generate summary"}</Button></>}>
        <form id="meeting-form" onSubmit={submit} style={{ display: "grid", gap: 12 }}>
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="tn-form-grid">
            <Field label="Title" required><Input required maxLength={200} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} data-autofocus /></Field>
            <Field label="Meeting date"><Input type="date" value={form.meetingDate} onChange={e => setForm({ ...form, meetingDate: e.target.value })} /></Field>
          </div>
          <Field label="Source (link or recording name, optional)"><Input maxLength={300} value={form.sourceRef} onChange={e => setForm({ ...form, sourceRef: e.target.value })} /></Field>
          <Field label="Notes or transcript" required hint={`${form.notes.length} / ${MAX_NOTES} · Write names as they appear in Tenemsa so owners can be matched. At least 30 characters.`}>
            <Textarea rows={10} required minLength={30} maxLength={MAX_NOTES} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} placeholder="Paste the notes or transcript here." />
          </Field>
        </form>
      </Modal>
    </div>
  );
}

import { useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getAiStatus } from "../../ai/services/aiService";
import { generateMeeting, listMeetings } from "../services/meetingService";
import type { AiStatus, MeetingListItem } from "../../../shared/types/models";

const MAX_NOTES = 20000;

export default function MeetingsPage() {
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";

  const [items, setItems] = useState<MeetingListItem[]>([]);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [form, setForm] = useState({ title: "", meetingDate: "", sourceRef: "", notes: "" });

  const load = () =>
    listMeetings().then(setItems).catch(() => setError("Could not load meeting summaries.")).finally(() => setLoading(false));

  useEffect(() => {
    load();
    getAiStatus().then(setStatus).catch(() => {});
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError(""); setBusy(true);
    try {
      const created = await generateMeeting({
        title: form.title.trim(),
        meetingDate: form.meetingDate || null,
        sourceRef: form.sourceRef.trim() || undefined,
        notes: form.notes,
      });
      navigate(`${base}/meetings/${created.id}`);
    } catch (err: any) {
      setFormError(err.response?.data?.message
        || (err.code === "ECONNABORTED" ? "The AI took too long. Please try again." : "Could not create the summary."));
      setBusy(false);
      getAiStatus().then(setStatus).catch(() => {});
    }
  };

  const off = status !== null && !status.enabled;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Meeting summaries</div>
          <div className="page-sub">Paste meeting notes, let AI draft a summary and tasks, then review before anything is created.</div>
        </div>
        <button className="btn-ems btn-primary-ems" disabled={off} onClick={() => setCreating(true)}>New summary</button>
      </div>

      {off && (
        <div className="alert-ems alert-error">
          {status && !status.companyEnabled
            ? "AI is switched off for your company. An owner or admin can turn it on in Company settings."
            : "AI is switched off on this server. An administrator can turn it on by setting AI_PROVIDER and GEMINI_API_KEY."}
        </div>
      )}
      {status?.enabled && (
        <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 12 }}>
          {status.requestsLeftThisHour} AI request(s) left this hour for you · model {status.model}
        </div>
      )}
      {error && <div className="alert-ems alert-error">{error}</div>}

      <div className="ems-card" style={{ padding: 0 }}>
        {loading && <div style={{ padding: 16, color: "var(--text-3)" }}>Loading...</div>}
        {!loading && items.length === 0 && (
          <div style={{ padding: 24, color: "var(--text-3)" }}>No summaries yet. Create one from your meeting notes.</div>
        )}
        {items.map(m => (
          <div key={m.id} onClick={() => navigate(`${base}/meetings/${m.id}`)}
               style={{ display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", borderBottom: "1px solid var(--border)", cursor: "pointer", flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 200 }}>
              <div style={{ color: "var(--text-1)", fontWeight: 600 }}>{m.title}</div>
              <div style={{ fontSize: 12, color: "var(--text-3)" }}>
                {m.meetingDate || new Date(m.createdAt).toLocaleDateString()}{m.createdByName ? ` · ${m.createdByName}` : ""}
              </div>
            </div>
            <span style={{ fontSize: 12, color: "var(--text-3)" }}>{m.convertedCount}/{m.actionItemCount} tasks created</span>
            <span style={{ fontSize: 11, fontWeight: 700, color: m.status === "APPROVED" ? "#22c55e" : "#f59e0b" }}>
              {m.status === "APPROVED" ? "APPROVED" : "DRAFT: needs review"}
            </span>
          </div>
        ))}
      </div>

      {creating && (
        <div className="ems-modal-backdrop" onClick={() => !busy && setCreating(false)}>
          <form className="ems-modal" style={{ width: 640 }} onClick={e => e.stopPropagation()} onSubmit={submit}>
            <div className="modal-title">New meeting summary</div>
            {formError && <div className="alert-ems alert-error">{formError}</div>}
            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: "0 14px" }}>
              <div className="form-group">
                <label className="form-label">Title *</label>
                <input className="form-control-ems" required maxLength={200} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
              </div>
              <div className="form-group">
                <label className="form-label">Meeting date</label>
                <input type="date" className="form-control-ems" value={form.meetingDate} onChange={e => setForm({ ...form, meetingDate: e.target.value })} />
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Source (link or recording name, optional)</label>
              <input className="form-control-ems" maxLength={300} value={form.sourceRef} onChange={e => setForm({ ...form, sourceRef: e.target.value })} />
            </div>
            <div className="form-group">
              <label className="form-label">Notes or transcript *</label>
              <textarea className="form-control-ems" rows={10} required minLength={30} maxLength={MAX_NOTES}
                        value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })}
                        placeholder="Paste the notes or transcript here. Tip: write names exactly as they appear in Tenemsa so owners can be matched." />
              <div style={{ fontSize: 11, color: "var(--text-3)", textAlign: "right" }}>{form.notes.length} / {MAX_NOTES}</div>
            </div>
            <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 12 }}>
              The notes are sent to the AI provider to create the summary. Do not paste anything you are not allowed to share.
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button type="button" className="btn-ems btn-secondary-ems" disabled={busy} onClick={() => setCreating(false)}>Cancel</button>
              <button type="submit" className="btn-ems btn-primary-ems" disabled={busy}>{busy ? "Summarising… (up to a minute)" : "Generate summary"}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

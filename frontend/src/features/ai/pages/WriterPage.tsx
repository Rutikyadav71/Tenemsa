import { useEffect, useState } from "react";
import { aiErrorMessage, draftMessage, getAiStatus } from "../services/aiService";
import type { AiStatus, CommunicationDraft, CommunicationKind, CommunicationTone } from "../../../shared/types/models";

const KINDS: [CommunicationKind, string][] = [
  ["ANNOUNCEMENT", "Announcement"], ["REMINDER", "Reminder"], ["FEEDBACK", "Feedback"],
  ["POLICY_UPDATE", "Policy update"], ["WELCOME", "Welcome"], ["THANK_YOU", "Thank you"], ["OTHER", "Other"],
];
const TONES: [CommunicationTone, string][] = [
  ["FRIENDLY", "Friendly"], ["FORMAL", "Formal"], ["CONCISE", "Short and direct"], ["ENCOURAGING", "Encouraging"],
];

export default function WriterPage() {
  const [kind, setKind] = useState<CommunicationKind>("ANNOUNCEMENT");
  const [tone, setTone] = useState<CommunicationTone>("FRIENDLY");
  const [audience, setAudience] = useState("");
  const [points, setPoints] = useState("");
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [draft, setDraft] = useState<CommunicationDraft | null>(null);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState("");

  useEffect(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);

  const run = async () => {
    setError(""); setBusy(true);
    try {
      const d = await draftMessage({ kind, audience: audience.trim(), tone, points: points.trim() });
      setDraft(d); setSubject(d.subject); setBody(d.body);
      getAiStatus().then(setStatus).catch(() => {});
    } catch (err: any) { setError(aiErrorMessage(err, "Could not write the draft.")); }
    finally { setBusy(false); }
  };

  const copy = async (label: string, text: string) => {
    try { await navigator.clipboard.writeText(text); setCopied(label); setTimeout(() => setCopied(""), 1500); } catch { /* clipboard blocked */ }
  };

  const off = status !== null && !status.enabled;
  const valid = audience.trim().length > 0 && points.trim().length >= 15;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">AI message writer</div>
          <div className="page-sub">Draft an announcement, reminder or feedback note. You edit and send it yourself; nothing is sent from here.</div>
        </div>
      </div>
      {off && (
        <div className="alert-ems alert-error">
          {status && !status.companyEnabled
            ? "AI is switched off for your company. An owner or admin can turn it on in Company settings."
            : "AI is not configured on this server."}
        </div>
      )}
      {error && <div className="alert-ems alert-error" onClick={() => setError("")}>{error}</div>}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16, alignItems: "start" }}>
        <div className="ems-card" style={{ padding: 18 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <div>
              <label className="form-label">Kind</label>
              <select className="form-control-ems" value={kind} onChange={e => setKind(e.target.value as CommunicationKind)}>
                {KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </div>
            <div>
              <label className="form-label">Tone</label>
              <select className="form-control-ems" value={tone} onChange={e => setTone(e.target.value as CommunicationTone)}>
                {TONES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </div>
          </div>
          <label className="form-label" style={{ marginTop: 10 }}>Who is it for?</label>
          <input className="form-control-ems" maxLength={200} value={audience} onChange={e => setAudience(e.target.value)} placeholder="For example: all engineering staff" />
          <label className="form-label" style={{ marginTop: 10 }}>Key points</label>
          <textarea className="form-control-ems" rows={7} maxLength={3000} value={points} onChange={e => setPoints(e.target.value)}
                    placeholder="The facts the message must contain: what, when, what people need to do" />
          <div style={{ fontSize: 12, color: "var(--text-3)", margin: "4px 0 12px" }}>
            {points.length} / 3000 · sent to the AI provider, so do not include secrets or private details about individuals.
          </div>
          <button className="btn-ems btn-primary-ems" disabled={busy || off || !valid} onClick={run}>{busy ? "Writing…" : "Write draft"}</button>
          {status?.enabled && <span style={{ fontSize: 12, color: "var(--text-3)", marginLeft: 12 }}>{status.requestsLeftThisHour} request(s) left this hour</span>}
        </div>

        <div className="ems-card" style={{ padding: 18 }}>
          {!draft ? (
            <div style={{ color: "var(--text-3)", fontSize: 14 }}>Your draft will appear here. You can edit it before copying.</div>
          ) : (
            <>
              <label className="form-label">Subject</label>
              <input className="form-control-ems" value={subject} maxLength={200} onChange={e => setSubject(e.target.value)} />
              <label className="form-label" style={{ marginTop: 10 }}>Message</label>
              <textarea className="form-control-ems" rows={12} value={body} onChange={e => setBody(e.target.value)} />
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
                <button className="btn-ems btn-secondary-ems" onClick={() => copy("subject", subject)}>{copied === "subject" ? "Copied" : "Copy subject"}</button>
                <button className="btn-ems btn-secondary-ems" onClick={() => copy("body", body)}>{copied === "body" ? "Copied" : "Copy message"}</button>
                <a className="btn-ems btn-secondary-ems" style={{ textDecoration: "none" }}
                   href={`mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`}>Open in mail app</a>
              </div>
              <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 10 }}>
                AI can make mistakes. Check names, dates and tone, and replace any [placeholders] before sending.
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

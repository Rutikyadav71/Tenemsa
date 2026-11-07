import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { LuCopy, LuMail, LuSend, LuSparkles } from "react-icons/lu";
import { can } from "../../../shared/lib/roles";
import { aiErrorMessage, draftMessage, getAiStatus } from "../services/aiService";
import type { AiStatus, CommunicationDraft, CommunicationKind, CommunicationTone } from "../../../shared/types/models";
import { Alert, Button, Card, CardHeader, EmptyState, Field, Input, PageHeader, Select, Textarea, useToast } from "../../../shared/ui";
import "./aiPages.css";

const KINDS: [CommunicationKind, string][] = [
  ["ANNOUNCEMENT", "Announcement"], ["REMINDER", "Reminder"], ["FEEDBACK", "Feedback"],
  ["POLICY_UPDATE", "Policy update"], ["WELCOME", "Welcome"], ["THANK_YOU", "Thank you"], ["OTHER", "Other"],
];
const TONES: [CommunicationTone, string][] = [
  ["FRIENDLY", "Friendly"], ["FORMAL", "Formal"], ["CONCISE", "Short and direct"], ["ENCOURAGING", "Encouraging"],
];

export default function WriterPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const base = `/${useLocation().pathname.split("/")[1]}`;
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

  useEffect(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);

  const run = async () => {
    setError(""); setBusy(true);
    try {
      const d = await draftMessage({ kind, audience: audience.trim(), tone, points: points.trim() });
      setDraft(d); setSubject(d.subject); setBody(d.body);
      getAiStatus().then(setStatus).catch(() => {});
    } catch (err) { setError(aiErrorMessage(err, "We couldn't write the draft.")); }
    finally { setBusy(false); }
  };

  const copy = async (what: string, text: string) => {
    try { await navigator.clipboard.writeText(text); toast.success(`${what} copied.`); }
    catch { toast.error("Your browser blocked copying. Select the text and copy it by hand."); }
  };

  const off = status !== null && !status.enabled;
  const valid = audience.trim().length > 0 && points.trim().length >= 15;

  return (
    <div>
      <PageHeader title="AI writer" subtitle="Draft an announcement, reminder or feedback note. Edit it, then copy it or send it to people from Announcements. Nothing is sent until you confirm." />
      {off && (
        <Alert tone="warning">
          {status && !status.companyEnabled
            ? "AI is switched off for your company. An owner or admin can turn it on in Settings."
            : "Tenemsa AI isn't available right now. Please contact your administrator."}
        </Alert>
      )}
      {error && <Alert tone="error">{error}</Alert>}

      <div className="ai-two">
        <Card>
          <CardHeader title="What do you need?" />
          <div className="tn-form-grid">
            <Field label="Kind"><Select value={kind} onChange={e => setKind(e.target.value as CommunicationKind)}>{KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</Select></Field>
            <Field label="Tone"><Select value={tone} onChange={e => setTone(e.target.value as CommunicationTone)}>{TONES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</Select></Field>
          </div>
          <Field label="Who is it for?" required><Input maxLength={200} value={audience} onChange={e => setAudience(e.target.value)} placeholder="For example: all engineering staff" /></Field>
          <Field label="Key points" required hint={`${points.length} / 3000 · Sent to Tenemsa AI, so leave out secrets and private details about individuals. At least 15 characters.`}>
            <Textarea rows={7} maxLength={3000} value={points} onChange={e => setPoints(e.target.value)} placeholder="The facts the message must contain: what, when, what people need to do" />
          </Field>
          <div className="tn-form-actions" style={{ alignItems: "center", justifyContent: "flex-start" }}>
            <Button variant="primary" icon={<LuSparkles size={16} />} loading={busy} disabled={off || !valid} onClick={run}>Write draft</Button>
            {status?.enabled && <span className="ai-muted">{status.requestsLeftThisHour} requests left this hour</span>}
          </div>
        </Card>

        <Card>
          <CardHeader title="Your draft" />
          {!draft ? <EmptyState title="Nothing written yet" text="Your draft will appear here. You can edit it before copying." icon={<LuSparkles size={22} />} /> : (
            <>
              <Field label="Subject"><Input value={subject} maxLength={200} onChange={e => setSubject(e.target.value)} /></Field>
              <Field label="Message"><Textarea rows={12} value={body} onChange={e => setBody(e.target.value)} /></Field>
              <div className="tn-form-actions" style={{ justifyContent: "flex-start", flexWrap: "wrap" }}>
                {can("announcement:send") && <Button variant="primary" icon={<LuSend size={15} />} onClick={() => navigate(`${base}/announcements`, { state: { subject, body } })}>Send to people</Button>}
                <Button icon={<LuCopy size={15} />} onClick={() => copy("Subject", subject)}>Copy subject</Button>
                <Button icon={<LuCopy size={15} />} onClick={() => copy("Message", body)}>Copy message</Button>
                <a href={`mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`} style={{ textDecoration: "none" }}><Button icon={<LuMail size={15} />}>Open in mail app</Button></a>
              </div>
              <p className="ai-muted" style={{ marginTop: 10 }}>AI can make mistakes. Check names, dates and tone, and replace any [placeholders] before sending.</p>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

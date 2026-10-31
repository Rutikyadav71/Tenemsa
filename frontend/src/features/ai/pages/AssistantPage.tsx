import { useEffect, useRef, useState } from "react";
import { aiErrorMessage, askHrAssistant, getAiStatus } from "../services/aiService";
import type { AiStatus } from "../../../shared/types/models";

interface Turn { question: string; answer?: string; facts?: string[]; error?: string; }

const EXAMPLES = [
  "How many days of leave have I had approved this year?",
  "Do I have any leave requests still pending?",
  "How many days was I present in the last two months?",
];

export default function AssistantPage() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const end = useRef<HTMLDivElement>(null);

  useEffect(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);
  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [turns]);

  const ask = async (q: string) => {
    const text = q.trim();
    if (text.length < 3 || busy) return;
    setQuestion(""); setBusy(true);
    setTurns(t => [...t, { question: text }]);
    try {
      const res = await askHrAssistant(text);
      setTurns(t => t.map((x, i) => (i === t.length - 1 ? { ...x, answer: res.answer, facts: res.factsUsed } : x)));
      getAiStatus().then(setStatus).catch(() => {});
    } catch (err: any) {
      setTurns(t => t.map((x, i) => (i === t.length - 1 ? { ...x, error: aiErrorMessage(err, "Could not get an answer.") } : x)));
    } finally { setBusy(false); }
  };

  const off = status !== null && !status.enabled;

  return (
    <div className="fade-in" style={{ maxWidth: 760 }}>
      <div className="page-header">
        <div>
          <div className="page-title">HR assistant</div>
          <div className="page-sub">Ask about your own leave and attendance. It sees only your records, cannot change anything, and has no access to company policy.</div>
        </div>
      </div>
      {off && (
        <div className="alert-ems alert-error">
          {status && !status.companyEnabled
            ? "AI is switched off for your company."
            : "AI is not configured on this server."}
        </div>
      )}

      <div className="ems-card" style={{ padding: 16, minHeight: 280 }}>
        {turns.length === 0 && (
          <div>
            <div style={{ color: "var(--text-3)", fontSize: 14, marginBottom: 10 }}>Try one of these:</div>
            {EXAMPLES.map(e => (
              <button key={e} className="btn-ems btn-secondary-ems" style={{ display: "block", marginBottom: 8 }} disabled={off || busy} onClick={() => ask(e)}>{e}</button>
            ))}
          </div>
        )}
        {turns.map((t, i) => (
          <div key={i} style={{ marginBottom: 16 }}>
            <div style={{ textAlign: "right" }}>
              <span style={{ display: "inline-block", background: "var(--accent)", color: "white", padding: "8px 12px", borderRadius: 12, maxWidth: "85%", fontSize: 14 }}>{t.question}</span>
            </div>
            <div style={{ marginTop: 8 }}>
              {t.error ? <div className="alert-ems alert-error">{t.error}</div>
                : t.answer === undefined ? <span style={{ color: "var(--text-3)", fontSize: 14 }}>Thinking…</span>
                : (
                  <div style={{ background: "var(--bg-elevated)", padding: "10px 12px", borderRadius: 12, maxWidth: "90%", fontSize: 14, color: "var(--text-1)", whiteSpace: "pre-wrap" }}>
                    {t.answer}
                    {t.facts && t.facts.length > 0 && (
                      <div style={{ marginTop: 8, fontSize: 12, color: "var(--text-3)" }}>
                        Based on: {t.facts.join(" · ")}
                      </div>
                    )}
                  </div>
                )}
            </div>
          </div>
        ))}
        <div ref={end} />
      </div>

      <form onSubmit={e => { e.preventDefault(); ask(question); }} style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <input className="form-control-ems" maxLength={500} value={question} onChange={e => setQuestion(e.target.value)}
               placeholder="Ask about your leave or attendance" disabled={off} />
        <button className="btn-ems btn-primary-ems" disabled={busy || off || question.trim().length < 3}>Ask</button>
      </form>
      <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 8 }}>
        AI can make mistakes. For decisions about leave, check the Leave page or ask HR.
        {status?.enabled && ` ${status.requestsLeftThisHour} question(s) left this hour.`}
      </div>
    </div>
  );
}

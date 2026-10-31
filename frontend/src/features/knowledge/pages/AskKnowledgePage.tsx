import { useEffect, useRef, useState } from "react";
import { aiErrorMessage } from "../../ai/services/aiService";
import { askKnowledge, kbStatus } from "../services/knowledgeService";
import type { KbAnswer, KbServiceStatus } from "../../../shared/types/models";

interface Turn { question: string; result?: KbAnswer; error?: string; }

export default function AskKnowledgePage() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<KbServiceStatus | null>(null);
  const end = useRef<HTMLDivElement>(null);

  useEffect(() => { kbStatus().then(setStatus).catch(() => {}); }, []);
  useEffect(() => { end.current?.scrollIntoView({ behavior: "smooth" }); }, [turns]);

  const ask = async (q: string) => {
    const text = q.trim();
    if (text.length < 3 || busy) return;
    setQuestion(""); setBusy(true);
    setTurns(t => [...t, { question: text }]);
    try {
      const result = await askKnowledge(text);
      setTurns(t => t.map((x, i) => (i === t.length - 1 ? { ...x, result } : x)));
    } catch (err: any) {
      setTurns(t => t.map((x, i) => (i === t.length - 1 ? { ...x, error: aiErrorMessage(err, "Could not get an answer.") } : x)));
    } finally { setBusy(false); }
  };

  const off = status !== null && !status.available;
  const empty = status !== null && status.available && status.readyDocuments === 0;

  return (
    <div className="fade-in" style={{ maxWidth: 800 }}>
      <div className="page-header">
        <div>
          <div className="page-title">Ask company documents</div>
          <div className="page-sub">Ask about policies and procedures. Answers come only from your company's documents and show where they were found.</div>
        </div>
      </div>
      {off && (
        <div className="alert-ems alert-error">
          {status && !status.companyEnabled ? "AI is switched off for your company." : "Document search is not configured on this server."}
        </div>
      )}
      {empty && <div className="alert-ems alert-error">No documents are available to you yet. Ask HR to add the policies.</div>}

      <div className="ems-card" style={{ padding: 16, minHeight: 280 }}>
        {turns.length === 0 && <div style={{ color: "var(--text-3)", fontSize: 14 }}>For example: "How many days of notice do I need for annual leave?"</div>}
        {turns.map((t, i) => (
          <div key={i} style={{ marginBottom: 18 }}>
            <div style={{ textAlign: "right" }}>
              <span style={{ display: "inline-block", background: "var(--accent)", color: "white", padding: "8px 12px", borderRadius: 12, maxWidth: "85%", fontSize: 14 }}>{t.question}</span>
            </div>
            <div style={{ marginTop: 8 }}>
              {t.error ? <div className="alert-ems alert-error">{t.error}</div>
                : !t.result ? <span style={{ color: "var(--text-3)", fontSize: 14 }}>Searching documents…</span>
                : (
                  <div style={{ background: "var(--bg-elevated)", padding: "10px 12px", borderRadius: 12, maxWidth: "95%", fontSize: 14, color: "var(--text-1)" }}>
                    <div style={{ whiteSpace: "pre-wrap" }}>{t.result.answer}</div>
                    {t.result.found && t.result.sources.length > 0 && (
                      <div style={{ marginTop: 10, borderTop: "1px solid var(--border)", paddingTop: 8 }}>
                        <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 4 }}>Sources</div>
                        {t.result.sources.map(s => (
                          <details key={s.n} style={{ fontSize: 12, color: "var(--text-2)", marginBottom: 4 }}>
                            <summary style={{ cursor: "pointer" }}>[{s.n}] {s.title}</summary>
                            <div style={{ padding: "4px 0 4px 14px", color: "var(--text-3)", whiteSpace: "pre-wrap" }}>{s.snippet}</div>
                          </details>
                        ))}
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
               placeholder="Ask a question about company policy" disabled={off} />
        <button className="btn-ems btn-primary-ems" disabled={busy || off || question.trim().length < 3}>Ask</button>
      </form>
      <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 8 }}>
        AI can make mistakes. Check the cited source, and ask HR for anything important.
      </div>
    </div>
  );
}

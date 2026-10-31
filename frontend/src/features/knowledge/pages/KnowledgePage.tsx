import { useEffect, useRef, useState, type FormEvent } from "react";
import { aiErrorMessage } from "../../ai/services/aiService";
import { addTextDocument, deleteDocument, kbStatus, listDocuments, reindexDocument, uploadDocument } from "../services/knowledgeService";
import type { KbAudience, KbDocument, KbServiceStatus } from "../../../shared/types/models";

const STATUS_COLOR: Record<string, string> = { READY: "#22c55e", PROCESSING: "#f59e0b", FAILED: "#ef4444" };
const MAX_CHARS = 200000;

export default function KnowledgePage() {
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const [status, setStatus] = useState<KbServiceStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [mode, setMode] = useState<"file" | "text" | null>(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [title, setTitle] = useState("");
  const [audience, setAudience] = useState<KbAudience>("ALL_STAFF");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const load = () =>
    listDocuments().then(setDocs).catch(() => setError("Could not load documents.")).finally(() => setLoading(false));

  useEffect(() => { load(); kbStatus().then(setStatus).catch(() => {}); }, []);

  // Indexing runs in the background: keep refreshing while anything is still processing.
  const processing = docs.some(d => d.status === "PROCESSING");
  useEffect(() => {
    if (!processing) return;
    const t = setInterval(load, 3000);
    return () => clearInterval(t);
  }, [processing]);

  const close = () => { setMode(null); setTitle(""); setText(""); setFile(null); setAudience("ALL_STAFF"); setFormError(""); };

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError(""); setBusy(true);
    try {
      if (mode === "file") {
        if (!file) { setFormError("Choose a file."); setBusy(false); return; }
        await uploadDocument(file, title, audience);
      } else {
        await addTextDocument(title, audience, text);
      }
      close(); await load();
    } catch (err: any) {
      setFormError(aiErrorMessage(err, "Could not add the document."));
    } finally { setBusy(false); }
  };

  const reindex = async (d: KbDocument) => {
    setError("");
    try { await reindexDocument(d.id); await load(); } catch (err: any) { setError(aiErrorMessage(err, "Could not re-index.")); }
  };

  const remove = async (d: KbDocument) => {
    if (!window.confirm(`Delete "${d.title}"? It will no longer be used to answer questions.`)) return;
    setError("");
    try { await deleteDocument(d.id); await load(); } catch (err: any) { setError(aiErrorMessage(err, "Could not delete.")); }
  };

  const off = status !== null && !status.available;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Knowledge base</div>
          <div className="page-sub">
            Add company documents (policies, handbooks, FAQs). Everyone can then ask questions and get answers that cite these documents.
          </div>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button className="btn-ems btn-secondary-ems" disabled={off} onClick={() => setMode("text")}>Paste text</button>
          <button className="btn-ems btn-primary-ems" disabled={off} onClick={() => setMode("file")}>Upload file</button>
        </div>
      </div>

      {off && (
        <div className="alert-ems alert-error">
          {status && !status.companyEnabled
            ? "AI is switched off for your company. Turn it on in Company settings to index documents."
            : "Document search is not configured on this server. Set AI_PROVIDER=google-genai and GEMINI_API_KEY."}
        </div>
      )}
      {status && !off && (
        <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 12 }}>
          Search: {status.vectorStore} · embedding model {status.embeddingModel}
        </div>
      )}
      {error && <div className="alert-ems alert-error">{error}</div>}

      <div className="ems-card" style={{ padding: 0 }}>
        {loading && <div style={{ padding: 16, color: "var(--text-3)" }}>Loading...</div>}
        {!loading && docs.length === 0 && (
          <div style={{ padding: 24, color: "var(--text-3)" }}>No documents yet. Upload a PDF, Word or text file, or paste text.</div>
        )}
        {docs.map(d => (
          <div key={d.id} style={{ display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", borderBottom: "1px solid var(--border)", flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 220 }}>
              <div style={{ color: "var(--text-1)", fontWeight: 600 }}>{d.title}</div>
              <div style={{ fontSize: 12, color: "var(--text-3)" }}>
                {d.fileName ? `${d.fileName} · ` : ""}{d.chunkCount} passage(s) · {d.audience === "MANAGEMENT" ? "Management only" : "All staff"}
                {d.uploadedByName ? ` · ${d.uploadedByName}` : ""} · {new Date(d.createdAt).toLocaleDateString()}
              </div>
              {d.status === "FAILED" && d.errorMessage && <div style={{ fontSize: 12, color: "#ef4444" }}>{d.errorMessage}</div>}
              {d.stale && <div style={{ fontSize: 12, color: "#f59e0b" }}>Indexed with an older model. Re-index to include it in answers.</div>}
            </div>
            <span style={{ fontSize: 11, fontWeight: 700, color: STATUS_COLOR[d.status] }}>{d.status}</span>
            <button className="btn-ems btn-secondary-ems" disabled={d.status === "PROCESSING" || off} onClick={() => reindex(d)}>Re-index</button>
            <button className="btn-ems btn-secondary-ems" onClick={() => remove(d)}>Delete</button>
          </div>
        ))}
      </div>
      <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 10 }}>
        Only the extracted text is kept, not the original file. Scanned PDFs (images) contain no text and cannot be used.
      </div>

      {mode && (
        <div className="ems-modal-backdrop" onClick={() => !busy && close()}>
          <form className="ems-modal" style={{ width: 640 }} onClick={e => e.stopPropagation()} onSubmit={submit}>
            <div className="modal-title">{mode === "file" ? "Upload a document" : "Paste document text"}</div>
            {formError && <div className="alert-ems alert-error">{formError}</div>}
            <div className="form-group">
              <label className="form-label">Title {mode === "text" ? "*" : "(optional, defaults to the file name)"}</label>
              <input className="form-control-ems" required={mode === "text"} maxLength={200} value={title} onChange={e => setTitle(e.target.value)} />
            </div>
            <div className="form-group">
              <label className="form-label">Who can get answers from this document?</label>
              <select className="form-control-ems" value={audience} onChange={e => setAudience(e.target.value as KbAudience)}>
                <option value="ALL_STAFF">All staff</option>
                <option value="MANAGEMENT">Management only (owner, admin, HR, managers)</option>
              </select>
            </div>
            {mode === "file" ? (
              <div className="form-group">
                <label className="form-label">File (PDF, DOCX, TXT, MD or CSV, up to 10 MB) *</label>
                <input ref={fileInput} type="file" className="form-control-ems" accept=".pdf,.docx,.txt,.md,.csv,.log,.json"
                       onChange={e => setFile(e.target.files?.[0] ?? null)} />
              </div>
            ) : (
              <div className="form-group">
                <label className="form-label">Text *</label>
                <textarea className="form-control-ems" rows={10} required minLength={50} maxLength={MAX_CHARS} value={text} onChange={e => setText(e.target.value)} />
                <div style={{ fontSize: 11, color: "var(--text-3)", textAlign: "right" }}>{text.length} / {MAX_CHARS}</div>
              </div>
            )}
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button type="button" className="btn-ems btn-secondary-ems" disabled={busy} onClick={close}>Cancel</button>
              <button className="btn-ems btn-primary-ems" disabled={busy}>{busy ? "Adding..." : "Add document"}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

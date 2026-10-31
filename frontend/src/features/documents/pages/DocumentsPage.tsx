import { useEffect, useState, type FormEvent } from "react";
import { aiErrorMessage } from "../../ai/services/aiService";
import { can } from "../../../shared/lib/roles";
import { deleteDocument, downloadDocument, formatSize, listDocuments, uploadDocument } from "../services/documentService";
import type { DocCategory, DocVisibility, StoredDocument } from "../../../shared/types/models";

const CATEGORIES: { key: DocCategory | "ALL"; label: string }[] = [
  { key: "ALL", label: "All" }, { key: "POLICY", label: "Policies" }, { key: "HANDBOOK", label: "Handbooks" },
  { key: "CONTRACT", label: "Contracts" }, { key: "FORM", label: "Forms" }, { key: "PERSONAL", label: "Personal" }, { key: "OTHER", label: "Other" },
];
const VISIBILITY_LABEL: Record<DocVisibility, string> = { ALL_STAFF: "All staff", MANAGEMENT: "Management only", PRIVATE: "Private" };
const EXTRACTABLE = ["pdf", "docx", "txt", "md", "csv"];

export default function DocumentsPage() {
  const canUpload = can("document:upload");
  const canIndex = can("kb:manage");
  const [category, setCategory] = useState<DocCategory | "ALL">("ALL");
  const [docs, setDocs] = useState<StoredDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [cat, setCat] = useState<DocCategory>("POLICY");
  const [visibility, setVisibility] = useState<DocVisibility>("ALL_STAFF");
  const [ownerEmpId, setOwnerEmpId] = useState("");
  const [indexForAi, setIndexForAi] = useState(false);

  const load = () =>
    listDocuments(category === "ALL" ? undefined : category).then(setDocs)
      .catch(e => setError(aiErrorMessage(e, "Could not load documents."))).finally(() => setLoading(false));
  useEffect(() => { setLoading(true); load(); }, [category]);

  const ext = file ? file.name.split(".").pop()?.toLowerCase() ?? "" : "";
  const canIndexThis = canIndex && visibility !== "PRIVATE" && EXTRACTABLE.includes(ext);

  const reset = () => { setOpen(false); setFile(null); setTitle(""); setCat("POLICY"); setVisibility("ALL_STAFF"); setOwnerEmpId(""); setIndexForAi(false); setFormError(""); };

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError("");
    if (!file) { setFormError("Choose a file."); return; }
    if (visibility === "PRIVATE" && !/^\d+$/.test(ownerEmpId)) { setFormError("Enter the employee number this document is for."); return; }
    setBusy(true);
    try {
      const saved = await uploadDocument({ file, title, category: cat, visibility, ownerEmpId: ownerEmpId ? Number(ownerEmpId) : undefined, indexForAi: indexForAi && canIndexThis });
      setNotice(saved.indexNote ? `Saved. Not added to AI answers: ${saved.indexNote}` : saved.indexedForAi ? "Saved and added to AI answers." : "Saved.");
      reset(); await load();
    } catch (err: any) {
      setFormError(aiErrorMessage(err, "Could not upload the file."));
    } finally { setBusy(false); }
  };

  const remove = async (d: StoredDocument) => {
    if (!window.confirm(`Delete "${d.title}"? This cannot be undone.`)) return;
    try { await deleteDocument(d.id); setNotice("Deleted."); await load(); } catch (err: any) { setError(aiErrorMessage(err, "Could not delete.")); }
  };

  const save = async (d: StoredDocument) => {
    try { await downloadDocument(d); } catch (err: any) { setError(aiErrorMessage(err, "Could not download the file.")); }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Documents</div>
          <div className="page-sub">Company policies, handbooks and your own documents. You only see what you are allowed to.</div>
        </div>
        {canUpload && <button className="btn-ems btn-primary-ems" onClick={() => setOpen(true)}>Upload document</button>}
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
        {CATEGORIES.map(c => (
          <button key={c.key} className={`btn-ems ${category === c.key ? "btn-primary-ems" : "btn-secondary-ems"}`} style={{ fontSize: 12 }}
                  onClick={() => setCategory(c.key)}>{c.label}</button>
        ))}
      </div>

      {error && <div className="alert-ems alert-error">{error}</div>}
      {notice && <div className="alert-ems" style={{ marginBottom: 12 }}>{notice}</div>}

      <div className="ems-card" style={{ padding: 0 }}>
        {loading && <div style={{ padding: 16, color: "var(--text-3)" }}>Loading...</div>}
        {!loading && docs.length === 0 && <div style={{ padding: 28, textAlign: "center", color: "var(--text-3)" }}>No documents here yet.</div>}
        {!loading && docs.map(d => (
          <div key={d.id} style={{ display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", borderBottom: "1px solid var(--border)", flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 220 }}>
              <div style={{ color: "var(--text-1)", fontWeight: 600 }}>{d.title}</div>
              <div style={{ fontSize: 12, color: "var(--text-3)" }}>
                {d.fileName} · {formatSize(d.sizeBytes)} · {VISIBILITY_LABEL[d.visibility]}
                {d.visibility === "PRIVATE" && d.ownerName ? ` (for ${d.ownerName})` : ""}
                {d.uploadedByName ? ` · added by ${d.uploadedByName}` : ""} · {new Date(d.createdAt).toLocaleDateString()}
                {d.indexedForAi ? " · used for AI answers" : ""}
              </div>
            </div>
            <button className="btn-ems btn-secondary-ems" style={{ fontSize: 12 }} onClick={() => save(d)}>Download</button>
            {d.canDelete && <button className="btn-ems btn-secondary-ems" style={{ fontSize: 12 }} onClick={() => remove(d)}>Delete</button>}
          </div>
        ))}
      </div>

      {open && (
        <div className="ems-modal-backdrop" onClick={() => !busy && reset()}>
          <form className="ems-modal" style={{ width: 560, maxWidth: "95vw" }} onClick={e => e.stopPropagation()} onSubmit={submit}>
            <div className="modal-title">Upload a document</div>
            {formError && <div className="alert-ems alert-error">{formError}</div>}
            <div className="form-group">
              <label className="form-label">File (pdf, docx, xlsx, txt, md, csv, png, jpg; up to 10 MB) *</label>
              <input type="file" className="form-control-ems" accept=".pdf,.docx,.xlsx,.txt,.md,.csv,.png,.jpg,.jpeg" onChange={e => setFile(e.target.files?.[0] ?? null)} />
            </div>
            <div className="form-group">
              <label className="form-label">Title (optional, defaults to the file name)</label>
              <input className="form-control-ems" maxLength={200} value={title} onChange={e => setTitle(e.target.value)} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 14px" }}>
              <div className="form-group">
                <label className="form-label">Category</label>
                <select className="form-control-ems" value={cat} onChange={e => setCat(e.target.value as DocCategory)}>
                  {CATEGORIES.filter(c => c.key !== "ALL").map(c => <option key={c.key} value={c.key}>{c.label}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Who can open it</label>
                <select className="form-control-ems" value={visibility} onChange={e => setVisibility(e.target.value as DocVisibility)}>
                  <option value="ALL_STAFF">All staff</option>
                  <option value="MANAGEMENT">Management only</option>
                  <option value="PRIVATE">One employee only (and HR)</option>
                </select>
              </div>
            </div>
            {visibility === "PRIVATE" && (
              <div className="form-group">
                <label className="form-label">Employee number *</label>
                <input className="form-control-ems" inputMode="numeric" value={ownerEmpId} onChange={e => setOwnerEmpId(e.target.value)} />
              </div>
            )}
            {canIndexThis && (
              <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, color: "var(--text-2)", marginBottom: 12 }}>
                <input type="checkbox" checked={indexForAi} onChange={e => setIndexForAi(e.target.checked)} />
                Also use this document to answer questions (AI knowledge base)
              </label>
            )}
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button type="button" className="btn-ems btn-secondary-ems" disabled={busy} onClick={reset}>Cancel</button>
              <button className="btn-ems btn-primary-ems" disabled={busy}>{busy ? "Uploading..." : "Upload"}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

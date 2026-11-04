import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { LuDownload, LuFileText, LuFolderOpen, LuTrash2, LuUpload } from "react-icons/lu";
import { can } from "../../../shared/lib/roles";
import { deleteDocument, downloadDocument, formatSize, listDocuments, uploadDocument } from "../services/documentService";
import type { DocCategory, DocVisibility, StoredDocument } from "../../../shared/types/models";
import { Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, Select, SkeletonList, Tabs, useConfirm, useToast } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";

const CATEGORIES: { id: DocCategory | "ALL"; label: string }[] = [
  { id: "ALL", label: "All" }, { id: "POLICY", label: "Policies" }, { id: "HANDBOOK", label: "Handbooks" },
  { id: "CONTRACT", label: "Contracts" }, { id: "FORM", label: "Forms" }, { id: "PERSONAL", label: "Personal" }, { id: "OTHER", label: "Other" },
];
const VISIBILITY_LABEL: Record<DocVisibility, string> = { ALL_STAFF: "All staff", MANAGEMENT: "Management only", PRIVATE: "Private" };
const VISIBILITY_TONE: Record<DocVisibility, "success" | "warning" | "info"> = { ALL_STAFF: "success", MANAGEMENT: "warning", PRIVATE: "info" };
const EXTRACTABLE = ["pdf", "docx", "txt", "md", "csv"];

export default function DocumentsPage() {
  const confirmAction = useConfirm();
  const toast = useToast();
  const canUpload = can("document:upload");
  const canIndex = can("kb:manage");
  const [category, setCategory] = useState<DocCategory | "ALL">("ALL");
  const [docs, setDocs] = useState<StoredDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [cat, setCat] = useState<DocCategory>("POLICY");
  const [visibility, setVisibility] = useState<DocVisibility>("ALL_STAFF");
  const [ownerEmpId, setOwnerEmpId] = useState("");
  const [indexForAi, setIndexForAi] = useState(false);

  const load = useCallback(async () => {
    try { setDocs(await listDocuments(category === "ALL" ? undefined : category)); setFailed(false); }
    catch { setFailed(true); }
    finally { setLoading(false); }
  }, [category]);
  useEffect(() => { setLoading(true); void load(); }, [load]);

  const ext = file ? file.name.split(".").pop()?.toLowerCase() ?? "" : "";
  const canIndexThis = canIndex && visibility !== "PRIVATE" && EXTRACTABLE.includes(ext);
  const tabs = useMemo(() => CATEGORIES.map(c => ({ id: c.id, label: c.label })), []);

  const reset = () => { setOpen(false); setFile(null); setTitle(""); setCat("POLICY"); setVisibility("ALL_STAFF"); setOwnerEmpId(""); setIndexForAi(false); setFormError(""); };

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError("");
    if (!file) { setFormError("Choose a file to upload."); return; }
    if (visibility === "PRIVATE" && !/^\d+$/.test(ownerEmpId)) { setFormError("Enter the employee number this document is for."); return; }
    setBusy(true);
    try {
      const saved = await uploadDocument({ file, title, category: cat, visibility, ownerEmpId: ownerEmpId ? Number(ownerEmpId) : undefined, indexForAi: indexForAi && canIndexThis });
      if (saved.indexNote) toast.info(`Saved. It wasn't added to Tenemsa AI answers: ${saved.indexNote}`);
      else toast.success(saved.indexedForAi ? "Saved and added to Tenemsa AI answers." : "Document saved.");
      reset(); await load();
    } catch (err) { setFormError(errorMessage(err, "We couldn't upload that file.")); }
    finally { setBusy(false); }
  };

  const remove = async (d: StoredDocument) => {
    if (!(await confirmAction({ title: `Delete "${d.title}"?`, message: "This can't be undone.", confirmLabel: "Delete", tone: "danger" }))) return;
    try { await deleteDocument(d.id); toast.success("Document deleted."); await load(); }
    catch (err) { toast.error(errorMessage(err, "We couldn't delete that document.")); }
  };

  const save = async (d: StoredDocument) => {
    try { await downloadDocument(d); } catch (err) { toast.error(errorMessage(err, "We couldn't download that file.")); }
  };

  return (
    <div>
      <PageHeader title="Documents" subtitle="Company policies, handbooks and your own documents. You only see what you're allowed to."
        actions={canUpload ? <Button variant="primary" icon={<LuUpload size={16} />} onClick={() => setOpen(true)}>Upload document</Button> : undefined} />

      <div style={{ marginBottom: 14 }}><Tabs tabs={tabs} value={category} onChange={setCategory} label="Document categories" /></div>

      <Card pad={false}>
        {loading ? <div style={{ padding: 20 }}><SkeletonList rows={4} /></div>
          : failed ? <ErrorState message="The documents couldn't be loaded." onRetry={() => void load()} />
          : docs.length === 0 ? <EmptyState icon={<LuFolderOpen size={24} />} title="No documents here yet"
              text={category === "ALL" ? "Uploaded policies and files will appear here." : "Nothing in this category yet."}
              action={canUpload ? <Button variant="primary" onClick={() => setOpen(true)}>Upload document</Button> : undefined} />
          : (
            <ul className="tn-list">
              {docs.map(d => (
                <li key={d.id} className="tn-doc">
                  <span className="tn-doc-icon" aria-hidden="true"><LuFileText size={20} /></span>
                  <div className="tn-doc-main">
                    <div className="strong tn-doc-title">{d.title}</div>
                    <div className="tn-doc-meta">
                      <span>{d.fileName}</span><span>{formatSize(d.sizeBytes)}</span>
                      {d.uploadedByName && <span>Added by {d.uploadedByName}</span>}
                      <span>{new Date(d.createdAt).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}</span>
                    </div>
                    <div className="tn-doc-tags">
                      <Badge tone={VISIBILITY_TONE[d.visibility]}>{VISIBILITY_LABEL[d.visibility]}{d.visibility === "PRIVATE" && d.ownerName ? ` · ${d.ownerName}` : ""}</Badge>
                      {d.indexedForAi && <Badge tone="accent">Used by Tenemsa AI</Badge>}
                    </div>
                  </div>
                  <div className="tn-doc-actions">
                    <Button size="sm" icon={<LuDownload size={15} />} onClick={() => void save(d)}>Download</Button>
                    {d.canDelete && <Button size="sm" variant="ghost" aria-label={`Delete ${d.title}`} icon={<LuTrash2 size={15} />} onClick={() => void remove(d)} />}
                  </div>
                </li>
              ))}
            </ul>
          )}
      </Card>

      <Modal open={open} title="Upload a document" description="Choose who may open it before you save." onClose={reset} dismissible={!busy}
        footer={<><Button onClick={reset} disabled={busy}>Cancel</Button><Button variant="primary" type="submit" form="doc-form" loading={busy}>Upload</Button></>}>
        <form id="doc-form" onSubmit={submit} noValidate>
          {formError && <div role="alert" className="tn-error" style={{ marginBottom: 12 }}>{formError}</div>}
          <Field label="File" required hint="PDF, Word, Excel, text, Markdown, CSV, PNG or JPG, up to 10 MB">
            <Input type="file" accept=".pdf,.docx,.xlsx,.txt,.md,.csv,.png,.jpg,.jpeg" onChange={e => setFile(e.target.files?.[0] ?? null)} />
          </Field>
          <Field label="Title" hint="Optional. Defaults to the file name."><Input maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></Field>
          <div className="tn-form-grid">
            <Field label="Category"><Select value={cat} onChange={e => setCat(e.target.value as DocCategory)}>{CATEGORIES.filter(c => c.id !== "ALL").map(c => <option key={c.id} value={c.id}>{c.label}</option>)}</Select></Field>
            <Field label="Who can open it"><Select value={visibility} onChange={e => setVisibility(e.target.value as DocVisibility)}>
              <option value="ALL_STAFF">All staff</option><option value="MANAGEMENT">Management only</option><option value="PRIVATE">One employee only (and HR)</option></Select></Field>
          </div>
          {visibility === "PRIVATE" && <Field label="Employee ID" required hint="The ID shown next to their name in Employees."><Input inputMode="numeric" value={ownerEmpId} onChange={e => setOwnerEmpId(e.target.value)} /></Field>}
          {canIndexThis && (
            <label className="tn-check"><input type="checkbox" checked={indexForAi} onChange={e => setIndexForAi(e.target.checked)} />
              <span>Let Tenemsa AI use this document to answer questions</span></label>
          )}
        </form>
      </Modal>
    </div>
  );
}

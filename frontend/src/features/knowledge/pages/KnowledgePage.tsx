import { useCallback, useEffect, useState, type FormEvent } from "react";
import { LuFilePlus2, LuLibrary, LuRefreshCw, LuTrash2, LuUpload } from "react-icons/lu";
import { aiErrorMessage } from "../../ai/services/aiService";
import { addTextDocument, deleteDocument, kbStatus, listDocuments, reindexDocument, uploadDocument } from "../services/knowledgeService";
import type { KbAudience, KbDocument, KbServiceStatus } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, Select, SkeletonList, Textarea, useConfirm, useToast, type Tone } from "../../../shared/ui";
import "./knowledge.css";

const MAX_CHARS = 200000;
const TONE: Record<string, Tone> = { READY: "success", PROCESSING: "warning", FAILED: "danger" };
const LABEL: Record<string, string> = { READY: "Ready", PROCESSING: "Indexing", FAILED: "Failed" };

export default function KnowledgePage() {
  const confirmAction = useConfirm();
  const toast = useToast();
  const [docs, setDocs] = useState<KbDocument[] | null>(null);
  const [status, setStatus] = useState<KbServiceStatus | null>(null);
  const [failed, setFailed] = useState(false);
  const [mode, setMode] = useState<"file" | "text" | null>(null);
  const [busy, setBusy] = useState(false);
  const [rowBusy, setRowBusy] = useState<number | null>(null);
  const [formError, setFormError] = useState("");
  const [title, setTitle] = useState("");
  const [audience, setAudience] = useState<KbAudience>("ALL_STAFF");
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);

  const load = useCallback(() => {
    setFailed(false);
    return listDocuments().then(setDocs).catch(() => setFailed(true));
  }, []);
  useEffect(() => { void load(); kbStatus().then(setStatus).catch(() => {}); }, [load]);

  // Indexing runs in the background: keep refreshing while anything is still processing.
  const processing = (docs ?? []).some(d => d.status === "PROCESSING");
  useEffect(() => {
    if (!processing) return;
    const t = setInterval(() => void load(), 3000);
    return () => clearInterval(t);
  }, [processing, load]);

  const close = () => { setMode(null); setTitle(""); setText(""); setFile(null); setAudience("ALL_STAFF"); setFormError(""); };

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError("");
    if (mode === "file" && !file) { setFormError("Choose a file first."); return; }
    setBusy(true);
    try {
      if (mode === "file") await uploadDocument(file!, title, audience);
      else await addTextDocument(title, audience, text);
      toast.success("Document added. It will be ready to search in a moment.");
      close(); await load();
    } catch (err) { setFormError(aiErrorMessage(err, "We couldn't add the document.")); }
    finally { setBusy(false); }
  };

  const reindex = async (d: KbDocument) => {
    setRowBusy(d.id);
    try { await reindexDocument(d.id); await load(); toast.success("Re-indexing started."); }
    catch (err) { toast.error(aiErrorMessage(err, "We couldn't re-index that document.")); }
    finally { setRowBusy(null); }
  };

  const remove = async (d: KbDocument) => {
    if (!(await confirmAction({ title: `Delete “${d.title}”?`, message: "It will no longer be used to answer questions.", confirmLabel: "Delete" }))) return;
    setRowBusy(d.id);
    try { await deleteDocument(d.id); await load(); toast.success("Document deleted."); }
    catch (err) { toast.error(aiErrorMessage(err, "We couldn't delete that document.")); }
    finally { setRowBusy(null); }
  };

  const off = status !== null && !status.available;

  return (
    <div>
      <PageHeader title="Knowledge base" subtitle="Add policies, handbooks and FAQs. Everyone can then ask questions and get answers that cite these documents."
        actions={<>
          <Button icon={<LuFilePlus2 size={16} />} disabled={off} onClick={() => setMode("text")}>Paste text</Button>
          <Button variant="primary" icon={<LuUpload size={16} />} disabled={off} onClick={() => setMode("file")}>Upload file</Button>
        </>} />
      {off && (
        <Alert tone="warning">
          {status && !status.companyEnabled
            ? "AI is switched off for your company. Turn it on in Settings to index documents."
            : "Document search isn't available right now. Please contact your administrator."}
        </Alert>
      )}

      {failed ? <ErrorState message="We couldn't load the documents." onRetry={() => void load()} />
        : docs === null ? <SkeletonList rows={3} />
        : docs.length === 0 ? <EmptyState icon={<LuLibrary size={22} />} title="No documents yet" text="Upload a PDF, Word or text file, or paste text." />
        : (
          <Card pad={false}>
            <ul className="tn-list">
              {docs.map(d => (
                <li key={d.id} className="kb-row">
                  <div className="kb-main">
                    <b>{d.title}</b>
                    <small>
                      {d.fileName ? `${d.fileName} · ` : ""}{d.chunkCount} passages · {d.audience === "MANAGEMENT" ? "Management only" : "All staff"}
                      {d.uploadedByName ? ` · ${d.uploadedByName}` : ""} · {new Date(d.createdAt).toLocaleDateString()}
                    </small>
                    {d.status === "FAILED" && d.errorMessage && <small className="kb-bad">{d.errorMessage}</small>}
                    {d.stale && <small className="kb-warn">Added before a search update. Re-index it to include it in answers.</small>}
                  </div>
                  <div className="kb-side">
                    <Badge tone={TONE[d.status] ?? "neutral"}>{LABEL[d.status] ?? d.status}</Badge>
                    <Button size="sm" icon={<LuRefreshCw size={14} />} loading={rowBusy === d.id} disabled={d.status === "PROCESSING" || off} onClick={() => reindex(d)}>Re-index</Button>
                    <Button size="sm" variant="danger" icon={<LuTrash2 size={14} />} disabled={rowBusy === d.id} onClick={() => remove(d)}>Delete</Button>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        )}
      <p className="kb-note">Only the extracted text is kept for search; the original file is not. Scanned PDFs (images) contain no text and can't be used.</p>

      <Modal open={!!mode} onClose={close} dismissible={!busy} size="lg" title={mode === "file" ? "Upload a document" : "Paste document text"}
        footer={<><Button variant="secondary" disabled={busy} onClick={close}>Cancel</Button><Button variant="primary" type="submit" form="kb-form" loading={busy}>Add document</Button></>}>
        <form id="kb-form" onSubmit={submit} style={{ display: "grid", gap: 12 }}>
          {formError && <Alert tone="error">{formError}</Alert>}
          <Field label="Title" required={mode === "text"} hint={mode === "file" ? "Optional. Defaults to the file name." : undefined}>
            <Input required={mode === "text"} maxLength={200} value={title} onChange={e => setTitle(e.target.value)} data-autofocus />
          </Field>
          <Field label="Who can get answers from this document?">
            <Select value={audience} onChange={e => setAudience(e.target.value as KbAudience)}>
              <option value="ALL_STAFF">All staff</option>
              <option value="MANAGEMENT">Management only (owner, admin, HR, managers)</option>
            </Select>
          </Field>
          {mode === "file" ? (
            <Field label="File" required hint="PDF, DOCX, TXT, MD or CSV, up to 10 MB.">
              <Input type="file" accept=".pdf,.docx,.txt,.md,.csv,.log,.json" onChange={e => setFile(e.target.files?.[0] ?? null)} />
            </Field>
          ) : (
            <Field label="Text" required hint={`${text.length} / ${MAX_CHARS} · At least 50 characters.`}>
              <Textarea rows={10} required minLength={50} maxLength={MAX_CHARS} value={text} onChange={e => setText(e.target.value)} />
            </Field>
          )}
        </form>
      </Modal>
    </div>
  );
}

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { LuFolderKanban, LuPlus } from "react-icons/lu";
import { createProject, listPeople, listProjects, MANAGER_ROLES } from "../services/projectService";
import type { Person, Project } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, Select, SkeletonList, Textarea } from "../../../shared/ui";
import "./projects.css";

/** Shared by the admin and employee layouts: the base path comes from the URL. */
export default function ProjectsPage() {
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";
  const canManage = MANAGER_ROLES.includes(localStorage.getItem("userRole") || "");

  const [projects, setProjects] = useState<Project[] | null>(null);
  const [people, setPeople] = useState<Person[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [failed, setFailed] = useState(false);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({ key: "", name: "", description: "", leadEmpId: "" });
  const [formError, setFormError] = useState("");

  const load = useCallback(() => {
    setFailed(false);
    listProjects().then(setProjects).catch(() => setFailed(true));
  }, []);
  useEffect(() => { load(); listPeople().then(setPeople).catch(() => {}); }, [load]);

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError(""); setSaving(true);
    try {
      const created = await createProject({
        key: form.key.trim().toUpperCase(), name: form.name.trim(),
        description: form.description.trim() || undefined, leadEmpId: form.leadEmpId ? Number(form.leadEmpId) : null,
      });
      setCreating(false);
      setForm({ key: "", name: "", description: "", leadEmpId: "" });
      navigate(`${base}/projects/${created.id}`);
    } catch (err: any) {
      setFormError(err.response?.data?.message || "We couldn't create the project.");
    } finally { setSaving(false); }
  };

  const visible = (projects ?? []).filter(p => showArchived || p.status === "ACTIVE");

  return (
    <div>
      <PageHeader title="Projects" subtitle="Plan and track work with boards, issues and comments."
        actions={<>
          <label className="pj-check"><input type="checkbox" checked={showArchived} onChange={e => setShowArchived(e.target.checked)} /> Show archived</label>
          {canManage && <Button variant="primary" icon={<LuPlus size={16} />} onClick={() => setCreating(true)}>New project</Button>}
        </>} />

      {failed ? <ErrorState message="We couldn't load the projects." onRetry={load} />
        : projects === null ? <SkeletonList rows={3} />
        : visible.length === 0 ? (
          <Card><EmptyState icon={<LuFolderKanban size={24} />} title="No projects yet"
            text={canManage ? "Create your first project to start planning work on a board." : "A manager can create a project and add you to it."} /></Card>
        ) : (
          <div className="pj-grid">
            {visible.map(p => {
              const pct = p.totalIssues ? Math.round((p.doneIssues / p.totalIssues) * 100) : 0;
              return (
                <button key={p.id} className="pj-card" style={{ opacity: p.status === "ARCHIVED" ? 0.65 : 1 }} onClick={() => navigate(`${base}/projects/${p.id}`)}>
                  <span className="pj-top"><b className="pj-key">{p.key}</b>{p.status === "ARCHIVED" && <Badge>Archived</Badge>}</span>
                  <span className="pj-name">{p.name}</span>
                  <span className="pj-desc">{p.description || "No description"}</span>
                  <span className="pj-bar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={`${pct}% done`}><i style={{ width: `${pct}%` }} /></span>
                  <span className="pj-foot"><span>{p.doneIssues} of {p.totalIssues} done</span><span>{p.leadName ? `Lead: ${p.leadName}` : "No lead"}</span></span>
                </button>
              );
            })}
          </div>
        )}

      <Modal open={creating} onClose={() => setCreating(false)} dismissible={!saving} title="New project"
        description="The key becomes the prefix of every issue (for example WEB-1) and cannot be changed later."
        footer={<><Button variant="secondary" disabled={saving} onClick={() => setCreating(false)}>Cancel</Button><Button variant="primary" type="submit" form="project-form" loading={saving}>Create project</Button></>}>
        <form id="project-form" onSubmit={submit} style={{ display: "grid", gap: 12 }}>
          {formError && <Alert tone="error">{formError}</Alert>}
          <Field label="Key" required><Input required maxLength={10} placeholder="WEB" value={form.key} onChange={e => setForm({ ...form, key: e.target.value.toUpperCase() })} data-autofocus /></Field>
          <Field label="Name" required><Input required maxLength={150} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field>
          <Field label="Description"><Textarea rows={3} maxLength={1000} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field>
          <Field label="Lead">
            <Select value={form.leadEmpId} onChange={e => setForm({ ...form, leadEmpId: e.target.value })}>
              <option value="">No lead</option>
              {people.map(p => <option key={p.empId} value={p.empId}>{p.name}</option>)}
            </Select>
          </Field>
        </form>
      </Modal>
    </div>
  );
}

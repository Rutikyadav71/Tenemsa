import { useEffect, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { createProject, listPeople, listProjects, MANAGER_ROLES } from "../services/projectService";
import type { Person, Project } from "../../../shared/types/models";

/** Shared by the admin and employee layouts: the base path comes from the URL. */
export default function ProjectsPage() {
  const navigate = useNavigate();
  const base = useLocation().pathname.startsWith("/admin") ? "/admin" : "/user";
  const canManage = MANAGER_ROLES.includes(localStorage.getItem("userRole") || "");

  const [projects, setProjects] = useState<Project[]>([]);
  const [people, setPeople] = useState<Person[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ key: "", name: "", description: "", leadEmpId: "" });
  const [formError, setFormError] = useState("");

  const load = () =>
    listProjects().then(setProjects).catch(() => setError("Could not load projects.")).finally(() => setLoading(false));

  useEffect(() => {
    load();
    listPeople().then(setPeople).catch(() => {});
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFormError("");
    try {
      const created = await createProject({
        key: form.key.trim().toUpperCase(),
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        leadEmpId: form.leadEmpId ? Number(form.leadEmpId) : null,
      });
      setCreating(false);
      setForm({ key: "", name: "", description: "", leadEmpId: "" });
      navigate(`${base}/projects/${created.id}`);
    } catch (err: any) {
      setFormError(err.response?.data?.message || "Could not create the project.");
    }
  };

  const visible = projects.filter(p => showArchived || p.status === "ACTIVE");

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Projects</div>
          <div className="page-sub">Plan and track work with boards, issues and comments.</div>
        </div>
        <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
          <label style={{ fontSize: 13, color: "var(--text-3)", display: "flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={showArchived} onChange={e => setShowArchived(e.target.checked)} /> Show archived
          </label>
          {canManage && <button className="btn-ems btn-primary-ems" onClick={() => setCreating(true)}>New project</button>}
        </div>
      </div>

      {error && <div className="alert-ems alert-error">{error}</div>}
      {loading ? <div style={{ color: "var(--text-3)" }}>Loading...</div> : visible.length === 0 ? (
        <div className="ems-card" style={{ textAlign: "center", color: "var(--text-3)" }}>
          {canManage ? "No projects yet. Create the first one." : "No projects yet. A manager can create one."}
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 16 }}>
          {visible.map(p => {
            const pct = p.totalIssues ? Math.round((p.doneIssues / p.totalIssues) * 100) : 0;
            return (
              <div key={p.id} className="ems-card" style={{ cursor: "pointer", opacity: p.status === "ARCHIVED" ? 0.6 : 1 }}
                   onClick={() => navigate(`${base}/projects/${p.id}`)}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                  <span style={{ fontWeight: 700, color: "var(--accent-light)", letterSpacing: 1 }}>{p.key}</span>
                  {p.status === "ARCHIVED" && <span style={{ fontSize: 11, color: "var(--text-3)" }}>ARCHIVED</span>}
                </div>
                <div style={{ fontSize: 16, fontWeight: 600, color: "var(--text-1)", marginBottom: 4 }}>{p.name}</div>
                <div style={{ fontSize: 13, color: "var(--text-3)", minHeight: 36 }}>{p.description || "No description"}</div>
                <div style={{ height: 6, background: "var(--bg-elevated)", borderRadius: 3, margin: "12px 0 6px" }}>
                  <div style={{ width: `${pct}%`, height: "100%", background: "var(--accent-light)", borderRadius: 3 }} />
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--text-3)" }}>
                  <span>{p.doneIssues} of {p.totalIssues} done</span>
                  <span>{p.leadName ? `Lead: ${p.leadName}` : "No lead"}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {creating && (
        <div className="ems-modal-backdrop" onClick={() => setCreating(false)}>
          <form className="ems-modal" style={{ width: 460 }} onClick={e => e.stopPropagation()} onSubmit={submit}>
            <div className="modal-title">New project</div>
            <div className="modal-sub">The key becomes the prefix of every issue (for example WEB-1) and cannot be changed later.</div>
            {formError && <div className="alert-ems alert-error">{formError}</div>}
            <div className="form-group">
              <label className="form-label">Key *</label>
              <input className="form-control-ems" required maxLength={10} placeholder="WEB" value={form.key}
                     onChange={e => setForm({ ...form, key: e.target.value.toUpperCase() })} />
            </div>
            <div className="form-group">
              <label className="form-label">Name *</label>
              <input className="form-control-ems" required maxLength={150} value={form.name}
                     onChange={e => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="form-group">
              <label className="form-label">Description</label>
              <textarea className="form-control-ems" rows={3} maxLength={1000} value={form.description}
                        onChange={e => setForm({ ...form, description: e.target.value })} />
            </div>
            <div className="form-group">
              <label className="form-label">Lead</label>
              <select className="form-control-ems" value={form.leadEmpId} onChange={e => setForm({ ...form, leadEmpId: e.target.value })}>
                <option value="">No lead</option>
                {people.map(p => <option key={p.empId} value={p.empId}>{p.name}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button type="button" className="btn-ems btn-secondary-ems" onClick={() => setCreating(false)}>Cancel</button>
              <button type="submit" className="btn-ems btn-primary-ems">Create project</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}

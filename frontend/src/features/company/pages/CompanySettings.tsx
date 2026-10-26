import { useEffect, useState, type FormEvent } from "react";
import { getMyCompany, updateMyCompany } from "../services/companyService";
import type { CompanyInfo } from "../types/models";

export default function CompanySettings() {
  const [company, setCompany] = useState<CompanyInfo | null>(null);
  const [name, setName] = useState("");
  const [message, setMessage] = useState<{ text: string; type: "success" | "error" } | null>(null);
  const [saving, setSaving] = useState(false);
  const userRole = localStorage.getItem("userRole") || "";
  // Mirrors backend company:manage (Owner + Admin). The server enforces it regardless.
  const canManage = ["OWNER", "ADMIN", "SUPER_ADMIN"].includes(userRole);

  useEffect(() => {
    getMyCompany()
      .then(c => { setCompany(c); setName(c.name); })
      .catch(() => setMessage({ text: "Could not load company details.", type: "error" }));
  }, []);

  const handleSave = async (e: FormEvent) => {
    e.preventDefault(); setMessage(null); setSaving(true);
    try {
      const c = await updateMyCompany(name);
      setCompany(c); setName(c.name);
      localStorage.setItem("companyName", c.name);
      setMessage({ text: "Company name updated.", type: "success" });
    } catch (err: any) {
      const d = err.response?.data;
      setMessage({ text: d?.message || (typeof d === "string" ? d : "Update failed."), type: "error" });
    } finally { setSaving(false); }
  };

  if (!company) return <div className="page-container">{message ? message.text : "Loading..."}</div>;

  const rows: [string, string][] = [
    ["Workspace address", company.slug],
    ["Members", String(company.memberCount)],
    ["Created", new Date(company.createdAt).toLocaleDateString()],
  ];

  return (
    <div className="page-container" style={{ maxWidth: 640 }}>
      <h2 style={{ fontFamily: "'Outfit',sans-serif", marginBottom: 4 }}>Company settings</h2>
      <p style={{ color: "var(--text-3)", fontSize: 13, marginBottom: 20 }}>
        Employees sign in with the workspace address below.
      </p>

      {message && <div className={`alert-ems alert-${message.type}`}>{message.text}</div>}

      <div style={{ background: "var(--bg-surface)", border: "1px solid var(--border)", borderRadius: 10, padding: 20, marginBottom: 20 }}>
        {rows.map(([k, v]) => (
          <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", fontSize: 13.5, borderBottom: "1px solid var(--border)" }}>
            <span style={{ color: "var(--text-3)" }}>{k}</span><span style={{ color: "var(--text-1)", fontWeight: 600 }}>{v}</span>
          </div>
        ))}
      </div>

      {canManage && (
        <form onSubmit={handleSave}>
          <div className="form-group">
            <label className="form-label">Company name</label>
            <input className="form-control-ems" value={name} onChange={e => setName(e.target.value)} required />
          </div>
          <button type="submit" className="btn-ems btn-primary-ems" disabled={saving || name.trim() === company.name}>
            {saving ? "Saving..." : "Save changes"}
          </button>
          <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 8 }}>
            The workspace address stays the same when you rename the company.
          </div>
        </form>
      )}
    </div>
  );
}

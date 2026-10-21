import { useEffect, useState, type FormEvent } from "react";
import { listInvitations, revokeInvitation, sendInvitation } from "../services/invitationService";
import type { InvitationItem, Role } from "../types/models";

// Mirrors backend RoleAssignmentRegistry; the server is the real check.
const ASSIGNABLE: Record<string, { value: Role; label: string }[]> = {
  OWNER: [
    { value: "ADMIN", label: "Admin" }, { value: "HR", label: "HR" },
    { value: "MANAGER", label: "Manager" }, { value: "EMPLOYEE", label: "Employee" },
    { value: "INTERN", label: "Intern" },
  ],
  ADMIN: [{ value: "MANAGER", label: "Manager" }],
  HR: [{ value: "EMPLOYEE", label: "Employee" }, { value: "INTERN", label: "Intern" }],
};

export default function InvitationsPage() {
  const callerRole = localStorage.getItem("userRole") || "";
  const roles = ASSIGNABLE[callerRole] || [];
  const [form, setForm] = useState({ name: "", email: "", department: "", role: (roles[0]?.value || "EMPLOYEE") as Role });
  const [pending, setPending] = useState<InvitationItem[]>([]);
  const [message, setMessage] = useState<{ text: string; type: "success" | "error" } | null>(null);
  const [sending, setSending] = useState(false);

  const load = () => listInvitations().then(setPending).catch(() => setMessage({ text: "Could not load invitations.", type: "error" }));
  useEffect(() => { load(); }, []);

  const handleSend = async (e: FormEvent) => {
    e.preventDefault(); setMessage(null); setSending(true);
    try {
      await sendInvitation({ ...form, department: form.department || undefined });
      setMessage({ text: `Invitation sent to ${form.email}.`, type: "success" });
      setForm({ ...form, name: "", email: "", department: "" });
      load();
    } catch (err: any) {
      setMessage({ text: err.response?.data?.message || "Could not send the invitation.", type: "error" });
    } finally { setSending(false); }
  };

  const handleRevoke = async (id: number) => {
    try { await revokeInvitation(id); load(); }
    catch (err: any) { setMessage({ text: err.response?.data?.message || "Could not revoke.", type: "error" }); }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Invitations</div>
          <div className="page-sub">Invite people by email. They choose their own password and join your workspace.</div>
        </div>
      </div>

      {message && <div className={`alert-ems alert-${message.type}`}>{message.text}</div>}

      {roles.length === 0 ? (
        <div className="alert-ems alert-error">Your role cannot invite people.</div>
      ) : (
        <form className="ems-card" onSubmit={handleSend} style={{ marginBottom: 24 }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 }}>
            <div className="form-group">
              <label className="form-label">Full name</label>
              <input className="form-control-ems" required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="form-group">
              <label className="form-label">Email</label>
              <input type="email" className="form-control-ems" required value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
            </div>
            <div className="form-group">
              <label className="form-label">Role</label>
              <select className="form-control-ems" value={form.role} onChange={e => setForm({ ...form, role: e.target.value as Role })}>
                {roles.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Department (optional)</label>
              <input className="form-control-ems" value={form.department} onChange={e => setForm({ ...form, department: e.target.value })} />
            </div>
          </div>
          <button type="submit" className="btn-ems btn-primary-ems" disabled={sending}>
            {sending ? "Sending..." : "Send invitation"}
          </button>
        </form>
      )}

      <div className="page-sub" style={{ marginBottom: 8 }}>Pending invitations</div>
      <div className="ems-card" style={{ padding: 0 }}>
        <div className="ems-table-wrap" style={{ borderRadius: 0, border: "none" }}>
          <table className="ems-table">
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Expires</th><th></th></tr></thead>
            <tbody>
              {pending.length === 0 && <tr><td colSpan={5}>No pending invitations.</td></tr>}
              {pending.map(i => (
                <tr key={i.id}>
                  <td className="td-primary">{i.name}</td>
                  <td>{i.email}</td>
                  <td><span className="badge badge-blue">{i.role}</span></td>
                  <td>{new Date(i.expiresAt).toLocaleDateString()}</td>
                  <td><button className="btn-ems btn-danger-ems" onClick={() => handleRevoke(i.id)}>Revoke</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

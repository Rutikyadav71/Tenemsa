import { useCallback, useEffect, useState, type FormEvent } from "react";
import { LuMailPlus, LuSend, LuUserX } from "react-icons/lu";
import { listInvitations, revokeInvitation, sendInvitation } from "../services/invitationService";
import type { InvitationItem, Role } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, CardHeader, EmptyState, ErrorState, Field, Input, PageHeader, Select, SkeletonList, useConfirm, useToast } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";

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
const ROLE_LABEL: Record<string, string> = { ADMIN: "Admin", HR: "HR", MANAGER: "Manager", EMPLOYEE: "Employee", INTERN: "Intern" };

type Form = { name: string; email: string; department: string; role: Role };

export default function InvitationsPage() {
  const toast = useToast();
  const confirmAction = useConfirm();
  const callerRole = localStorage.getItem("userRole") || "";
  const roles = ASSIGNABLE[callerRole] || [];
  const [form, setForm] = useState<Form>({ name: "", email: "", department: "", role: (roles[0]?.value || "EMPLOYEE") as Role });
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [pending, setPending] = useState<InvitationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [sending, setSending] = useState(false);
  const [mailWarning, setMailWarning] = useState("");

  const load = useCallback(async () => {
    try { setPending(await listInvitations()); setFailed(false); }
    catch { setFailed(true); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const set = <K extends keyof Form>(key: K, value: Form[K]) => { setForm(f => ({ ...f, [key]: value })); setErrors(e => ({ ...e, [key]: "" })); };

  const handleSend = async (e: FormEvent) => {
    e.preventDefault();
    const next = { name: v.required(form.name, "Name"), email: v.email(form.email), department: "", role: "" };
    setErrors(next);
    if (v.hasErrors(next)) return;
    setSending(true); setMailWarning("");
    try {
      const sent = await sendInvitation({ ...form, name: form.name.trim(), email: form.email.trim(), department: form.department.trim() || undefined });
      if (sent.emailSent === false) {
        // Saved, but the mail server refused to send it, so the link was not delivered.
        setMailWarning(`The invitation for ${form.email} was created, but the email could not be sent. Check the email settings on the server, then revoke it and send it again.`);
      } else toast.success(`Invitation sent to ${form.email}.`);
      setForm(f => ({ ...f, name: "", email: "", department: "" }));
      void load();
    } catch (err) { toast.fail(err, "We couldn't send the invitation."); }
    finally { setSending(false); }
  };

  const handleRevoke = async (i: InvitationItem) => {
    if (!(await confirmAction({ title: `Revoke the invitation for ${i.name}?`, message: "The link in their email will stop working.", confirmLabel: "Revoke", tone: "danger" }))) return;
    try { await revokeInvitation(i.id); toast.success("Invitation revoked."); void load(); }
    catch (err) { toast.error(errorMessage(err, "We couldn't revoke that invitation.")); }
  };

  return (
    <div>
      <PageHeader title="Invitations" subtitle="Invite people by email. They choose their own password and join your workspace." />

      {roles.length === 0 ? <Alert tone="error">Your role can't invite people.</Alert> : (
        <Card pad={false} style={{ marginBottom: 20 }}>
          <CardHeader title="Invite someone" subtitle="They'll get an email with a secure link that expires." />
          <div style={{ padding: 20 }}>
          {mailWarning && <div style={{ marginBottom: 12 }}><Alert tone="warning">{mailWarning}</Alert></div>}
          <form onSubmit={handleSend} noValidate>
            <div className="tn-form-grid">
              <Field label="Full name" required error={errors.name}><Input value={form.name} autoComplete="off" onChange={e => set("name", e.target.value)} /></Field>
              <Field label="Work email" required error={errors.email}><Input type="email" value={form.email} autoComplete="off" onChange={e => set("email", e.target.value)} /></Field>
              <Field label="Role" required><Select value={form.role} onChange={e => set("role", e.target.value as Role)}>{roles.map(r => <option key={r.value} value={r.value}>{r.label}</option>)}</Select></Field>
              <Field label="Department" hint="Optional"><Input value={form.department} onChange={e => set("department", e.target.value)} /></Field>
            </div>
            <div className="tn-form-actions"><Button type="submit" variant="primary" loading={sending} icon={<LuSend size={15} />}>Send invitation</Button></div>
          </form>
          </div>
        </Card>
      )}

      <Card pad={false}>
        <CardHeader title="Pending invitations" subtitle={loading ? undefined : `${pending.length} waiting to be accepted`} />
        {loading ? <div style={{ padding: 20 }}><SkeletonList rows={3} /></div>
          : failed ? <ErrorState message="The invitations couldn't be loaded." onRetry={() => void load()} />
          : pending.length === 0 ? <EmptyState icon={<LuMailPlus size={24} />} title="No pending invitations" text="People you invite will show up here until they accept." />
          : (
            <div className="tn-table-wrap">
              <table className="tn-table tn-table-cards">
                <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Expires</th><th style={{ width: 110 }}><span className="sr-only">Actions</span></th></tr></thead>
                <tbody>
                  {pending.map(i => (
                    <tr key={i.id}>
                      <td><span className="strong">{i.name}</span></td>
                      <td data-label="Email" style={{ wordBreak: "break-all" }}>{i.email}</td>
                      <td data-label="Role"><Badge tone="accent">{ROLE_LABEL[i.role] ?? i.role}</Badge></td>
                      <td data-label="Expires">{new Date(i.expiresAt).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })}</td>
                      <td><div style={{ display: "flex", justifyContent: "flex-end" }}><Button variant="ghost" size="sm" icon={<LuUserX size={15} />} onClick={() => void handleRevoke(i)}>Revoke</Button></div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </Card>
    </div>
  );
}

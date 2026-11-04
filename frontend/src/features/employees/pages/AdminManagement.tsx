import { useCallback, useEffect, useState } from "react";
import { LuPlus, LuSnowflake, LuTrash2, LuShieldCheck } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import { errorMessage } from "../../../shared/lib/errors";
import type { User } from "../../../shared/types/models";
import { Alert, Avatar, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, Select, SkeletonList, useConfirm, useToast } from "../../../shared/ui";
import "./adminManagement.css";

type Action = "freeze" | "unfreeze" | "delete";
type NewAdmin = { name: string; email: string; password: string; role: "ADMIN" | "HR" };
const EMPTY: NewAdmin = { name: "", email: "", password: "", role: "ADMIN" };
const MAX_ADMINS = 4;

const COPY: Record<Action, (n: string) => { title: string; message: string; confirmLabel: string }> = {
  freeze: n => ({ title: `Freeze ${n}?`, message: "They are blocked from signing in straight away. Their data is kept and you can unfreeze them later.", confirmLabel: "Freeze" }),
  unfreeze: n => ({ title: `Unfreeze ${n}?`, message: "They can sign in normally again.", confirmLabel: "Unfreeze" }),
  delete: n => ({ title: `Delete ${n}?`, message: "This permanently removes the account and cannot be undone.", confirmLabel: "Delete account" }),
};
const DONE: Record<Action, string> = { freeze: "frozen", unfreeze: "unfrozen", delete: "deleted" };

export default function AdminManagement() {
  const toast = useToast();
  const confirmAction = useConfirm();
  const [admins, setAdmins] = useState<User[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState<NewAdmin>(EMPTY);
  const [formError, setFormError] = useState("");
  const [adding, setAdding] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const myId = Number(localStorage.getItem("id"));
  const isMain = localStorage.getItem("primaryOwner") === "true";

  const load = useCallback(() => {
    setFailed(false);
    axiosInstance.get<User[]>("/api/admin/all").then(r => setAdmins(r.data || [])).catch(() => setFailed(true));
  }, []);
  useEffect(load, [load]);

  const add = async () => {
    if (!form.name.trim() || !form.email.trim() || form.password.length < 6) { setFormError("Fill in every field. The password needs at least 6 characters."); return; }
    setAdding(true); setFormError("");
    try {
      await axiosInstance.post("/api/admin/register-by-admin", form);
      toast.success(`${form.role === "HR" ? "HR" : "Admin"} ${form.name} was added.`);
      setShowAdd(false); setForm(EMPTY); load();
    } catch (e) { setFormError(errorMessage(e, "We couldn't add this person.")); }
    finally { setAdding(false); }
  };

  const act = async (type: Action, admin: User) => {
    const c = COPY[type](admin.name);
    if (!(await confirmAction({ ...c, tone: type === "delete" ? "danger" : "primary" }))) return;
    setBusyId(admin.id);
    try {
      if (type === "freeze") await axiosInstance.put(`/api/admin/${admin.id}/freeze`);
      if (type === "unfreeze") await axiosInstance.put(`/api/admin/${admin.id}/unfreeze`);
      if (type === "delete") await axiosInstance.delete(`/api/admin/${admin.id}`);
      toast.success(`${admin.name} was ${DONE[type]}.`);
      load();
    } catch (e) { toast.fail(e, "That didn't work."); }
    finally { setBusyId(null); }
  };

  const list = admins ?? [];
  return (
    <div>
      <PageHeader title="Admin control" subtitle={admins ? `${list.length} of ${MAX_ADMINS} administrators` : undefined}
        actions={isMain && list.length < MAX_ADMINS ? <Button variant="primary" icon={<LuPlus size={16} />} onClick={() => setShowAdd(true)}>Add admin</Button> : undefined} />

      {isMain
        ? <Alert tone="info">You are the main administrator. You can add up to {MAX_ADMINS} admins, and freeze or delete the others.</Alert>
        : <Alert tone="info">Only the main administrator can add, freeze or delete admin accounts.</Alert>}

      <div style={{ marginTop: 16 }}>
        {failed ? <ErrorState message="We couldn't load the administrators." onRetry={load} />
          : admins === null ? <SkeletonList rows={2} />
          : list.length === 0 ? <EmptyState title="No administrators" text="Administrators will appear here." />
          : (
            <div style={{ display: "grid", gap: 12 }}>
              {list.map(a => {
                const me = a.id === myId;
                return (
                  <Card key={a.id} className="am-card">
                    <Avatar name={a.name} src={a.profileImageUrl || undefined} size={48} />
                    <div className="am-main">
                      <div className="am-title">
                        <b>{a.name}</b>
                        {a.primaryOwner && <Badge tone="accent">Main admin</Badge>}
                        {me && <Badge tone="neutral">You</Badge>}
                        {a.frozen ? <Badge tone="danger">Frozen</Badge> : !a.primaryOwner && <Badge tone="success">Active</Badge>}
                      </div>
                      <div className="am-sub">{a.email}</div>
                      <div className="am-sub">{a.role === "HR" ? "HR" : "Admin"} · ID #{a.id}</div>
                    </div>
                    {isMain && !a.primaryOwner && !me && (
                      <div className="am-actions">
                        {a.frozen
                          ? <Button size="sm" icon={<LuShieldCheck size={15} />} loading={busyId === a.id} onClick={() => act("unfreeze", a)}>Unfreeze</Button>
                          : <Button size="sm" icon={<LuSnowflake size={15} />} loading={busyId === a.id} onClick={() => act("freeze", a)}>Freeze</Button>}
                        <Button size="sm" variant="danger" icon={<LuTrash2 size={15} />} disabled={busyId === a.id} onClick={() => act("delete", a)}>Delete</Button>
                      </div>
                    )}
                  </Card>
                );
              })}
            </div>
          )}
      </div>

      <Card style={{ marginTop: 20 }}>
        <div className="am-how">How admin access works</div>
        {([
          ["Main admin", "The first admin set up for the company. Can add up to three more admins. Cannot be frozen or deleted."],
          ["Admin and HR", "Added by the main admin. Admins manage the workspace; HR manages the employee lifecycle, leave and attendance."],
          ["Freeze", "Blocks sign-in straight away. All data is kept."],
          ["Delete", "Permanently removes the account."],
        ] as const).map(([t, s]) => <div className="am-how-row" key={t}><b>{t}</b><span>{s}</span></div>)}
      </Card>

      <Modal open={showAdd} onClose={() => setShowAdd(false)} dismissible={!adding} title="Add admin"
        description={`Gives elevated access, so choose the role carefully (${list.length} of ${MAX_ADMINS} used).`}
        footer={<><Button variant="secondary" disabled={adding} onClick={() => setShowAdd(false)}>Cancel</Button><Button variant="primary" loading={adding} onClick={add}>Add admin</Button></>}>
        <div style={{ display: "grid", gap: 12 }}>
          {formError && <Alert tone="error">{formError}</Alert>}
          <Field label="Role">
            <Select value={form.role} onChange={e => setForm({ ...form, role: e.target.value as NewAdmin["role"] })}>
              <option value="ADMIN">Admin: manages the workspace, departments and teams</option>
              <option value="HR">HR: employees, leave and attendance</option>
            </Select>
          </Field>
          <Field label="Full name" required><Input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} data-autofocus /></Field>
          <Field label="Email" required><Input type="email" autoComplete="off" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field>
          <Field label="Temporary password" required hint="At least 6 characters. They can change it in Settings.">
            <Input type="password" autoComplete="new-password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />
          </Field>
        </div>
      </Modal>
    </div>
  );
}

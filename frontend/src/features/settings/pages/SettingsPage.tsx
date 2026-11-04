import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { LuLogOut, LuMonitor, LuMoon, LuSun } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import ProfileImageUpload from "../../../shared/components/ProfileImageUpload";
import { performLogout, performLogoutAllDevices } from "../../../shared/lib/logout";
import { can, type Workspace } from "../../../shared/lib/roles";
import { errorMessage } from "../../../shared/lib/errors";
import { password as passwordError } from "../../../shared/lib/validators";
import { useTheme, type ThemeMode } from "../../../shared/theme/ThemeProvider";
import {
  Alert, Badge, Button, Card, CardHeader, EmptyState, ErrorState, Field, Input, PageHeader, SkeletonList, Tabs, useConfirm, useToast,
} from "../../../shared/ui";
import { getMyCompany, updateMyCompany } from "../../company/services/companyService";
import { setCompanyAi } from "../../ai/services/aiService";
import { deleteAllChats } from "../../ai/services/chatHistoryService";
import type { CompanyInfo } from "../../../shared/types/models";
import { changePassword, deviceName, listSessions, revokeSession, type DeviceSession } from "../services/settingsService";
import "./settings.css";

type TabId = "account" | "appearance" | "notifications" | "security" | "workspace" | "ai";

const ROLE_LABEL: Record<string, string> = {
  OWNER: "Owner", ADMIN: "Admin", HR: "HR", MANAGER: "Manager", EMPLOYEE: "Employee", INTERN: "Intern", SUPER_ADMIN: "Super admin",
};

function Switch({ checked, onChange, disabled, label }: { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean; label: string }) {
  return (
    <label className="st-switch">
      <input type="checkbox" role="switch" checked={checked} disabled={disabled} onChange={e => onChange(e.target.checked)} aria-label={label} />
      <span className="st-track" aria-hidden="true" />
    </label>
  );
}

/* ------------------------------ My account ------------------------------ */
function AccountSection({ workspace }: { workspace: Workspace }) {
  const navigate = useNavigate();
  const confirmAction = useConfirm();
  const toast = useToast();
  const isAdminTier = workspace === "admin";
  const name = localStorage.getItem("name") || localStorage.getItem("adminName") || "Member";
  const email = localStorage.getItem("email") || localStorage.getItem("adminEmail") || "—";
  const role = localStorage.getItem("userRole") || "";
  const empId = localStorage.getItem("empId") || "";
  const userId = localStorage.getItem("id") || "";
  const [photo, setPhoto] = useState<string | null>(localStorage.getItem("profileImageUrl") || localStorage.getItem("adminProfileImageUrl") || null);
  const [busy, setBusy] = useState(false);

  const uploadAdmin = async (file: File): Promise<string> => {
    const fd = new FormData();
    fd.append("file", file, "profile.jpg");
    const res = await axiosInstance.post(`/api/upload/admin-profile/${userId}`, fd);
    return res.data.url as string;
  };
  const onUpload = (url: string) => {
    setPhoto(url);
    localStorage.setItem(isAdminTier ? "adminProfileImageUrl" : "profileImageUrl", url);
    localStorage.setItem("profileImageUrl", url);
  };

  const signOut = async (all: boolean) => {
    const ok = await confirmAction(all
      ? { title: "Sign out of every device?", message: "You will be signed out here and on every other browser or phone.", confirmLabel: "Sign out everywhere" }
      : { title: "Sign out of Tenemsa?", message: "You will be returned to the sign-in page.", confirmLabel: "Sign out", tone: "primary" });
    if (!ok) return;
    setBusy(true);
    try { await (all ? performLogoutAllDevices() : performLogout()); navigate("/"); }
    catch (e) { toast.fail(e, "We couldn't sign you out of every device."); setBusy(false); }
  };

  return (
    <div className="st-section">
      <Card>
        <CardHeader title="My account" subtitle="Your picture and details as colleagues see them." />
        <div className="st-me">
          <ProfileImageUpload empId={isAdminTier ? userId : empId} currentUrl={photo} name={name} onUpload={onUpload} size={84} editable
            customUploadFn={isAdminTier ? uploadAdmin : undefined} />
          <div style={{ minWidth: 0 }}>
            <div style={{ font: "600 18px 'Outfit Variable','Outfit',sans-serif", color: "var(--text-1)", overflowWrap: "anywhere" }}>{name}</div>
            <Badge tone="info">{ROLE_LABEL[role] ?? role}</Badge>
          </div>
        </div>
        <div className="st-kv"><span>Email</span><span>{email}</span></div>
        {empId && <div className="st-kv"><span>Employee ID</span><span>#{empId}</span></div>}
        <div className="st-kv"><span>Company</span><span>{localStorage.getItem("companyName") || "—"}</span></div>
        <div style={{ marginTop: 12 }}>
          <Link to={`/${workspace}/profile`} className="tn-link">Open my full profile</Link>
        </div>
      </Card>
      <Card>
        <CardHeader title="Sign out" />
        <div className="tn-form-actions" style={{ justifyContent: "flex-start" }}>
          <Button variant="secondary" icon={<LuLogOut size={15} />} loading={busy} onClick={() => signOut(false)}>Sign out</Button>
          <Button variant="secondary" loading={busy} onClick={() => signOut(true)}>Sign out of all devices</Button>
        </div>
      </Card>
    </div>
  );
}

/* ------------------------------ Appearance ------------------------------ */
function AppearanceSection() {
  const { mode, setMode } = useTheme();
  const options: { id: ThemeMode; label: string; cls: string; icon: typeof LuSun }[] = [
    { id: "light", label: "Light", cls: "l", icon: LuSun },
    { id: "dark", label: "Dark", cls: "d", icon: LuMoon },
    { id: "system", label: "System", cls: "s", icon: LuMonitor },
  ];
  return (
    <Card>
      <CardHeader title="Theme" subtitle="Saved on this device. System follows your device's setting." />
      <div className="st-themes" role="radiogroup" aria-label="Theme">
        {options.map(o => (
          <button key={o.id} role="radio" aria-checked={mode === o.id} className="st-theme" onClick={() => setMode(o.id)}>
            <i className={o.cls} aria-hidden="true" />{o.label}
          </button>
        ))}
      </div>
    </Card>
  );
}

/* ----------------------------- Notifications ---------------------------- */
function NotificationsSection() {
  const toast = useToast();
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [failed, setFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const load = useCallback(() => {
    setFailed(false);
    axiosInstance.get<{ emailEnabled: boolean }>("/api/notifications/settings").then(r => setEnabled(r.data.emailEnabled)).catch(() => setFailed(true));
  }, []);
  useEffect(load, [load]);

  const change = async (v: boolean) => {
    setSaving(true);
    try { setEnabled((await axiosInstance.put<{ emailEnabled: boolean }>("/api/notifications/settings", { emailEnabled: v })).data.emailEnabled); }
    catch (e) { toast.fail(e, "We couldn't change that setting."); }
    finally { setSaving(false); }
  };

  if (failed) return <ErrorState message="We couldn't load your notification settings." onRetry={load} />;
  if (enabled === null) return <SkeletonList rows={2} />;
  return (
    <Card>
      <CardHeader title="Notifications" subtitle="In-app notifications are always on." />
      <div className="st-row">
        <div><b>Also email me</b><small>Send a copy of each notification to your email address.</small></div>
        <Switch checked={enabled} disabled={saving} onChange={change} label="Also email me notifications" />
      </div>
    </Card>
  );
}

/* ------------------------------- Security ------------------------------- */
function PasswordCard() {
  const toast = useToast();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const rules = [
    { ok: next.length >= 8, text: "At least 8 characters" },
    { ok: /[a-z]/.test(next), text: "A lowercase letter" },
    { ok: /[A-Z]/.test(next), text: "An uppercase letter" },
    { ok: /\d/.test(next), text: "A number" },
  ];
  const mismatch = confirm !== "" && confirm !== next ? "The two passwords don't match." : "";

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true); setError("");
    if (!current || passwordError(next) || next !== confirm) return;
    setSaving(true);
    try {
      await changePassword(current, next);
      toast.success("Password updated. Your other devices were signed out.");
      setCurrent(""); setNext(""); setConfirm(""); setTouched(false);
    } catch (err) { setError(errorMessage(err, "We couldn't change your password.")); }
    finally { setSaving(false); }
  };

  return (
    <Card>
      <CardHeader title="Change password" subtitle="Changing it signs you out everywhere else." />
      <form onSubmit={submit} noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="tn-form-grid">
          <Field label="Current password" required error={touched && !current ? "Enter your current password." : undefined}>
            <Input type="password" autoComplete="current-password" value={current} onChange={e => setCurrent(e.target.value)} />
          </Field>
          <span />
          <Field label="New password" required error={touched ? passwordError(next) || undefined : undefined}>
            <Input type="password" autoComplete="new-password" value={next} onChange={e => setNext(e.target.value)} />
          </Field>
          <Field label="Confirm new password" required error={mismatch || (touched && !confirm ? "Re-enter the new password." : undefined)}>
            <Input type="password" autoComplete="new-password" value={confirm} onChange={e => setConfirm(e.target.value)} />
          </Field>
        </div>
        <ul className="st-rules" aria-label="Password rules">
          {rules.map(r => <li key={r.text} className={r.ok ? "ok" : ""}>{r.text}</li>)}
        </ul>
        <div className="tn-form-actions"><Button type="submit" loading={saving}>Update password</Button></div>
      </form>
    </Card>
  );
}

function DevicesCard() {
  const toast = useToast();
  const confirmAction = useConfirm();
  const [sessions, setSessions] = useState<DeviceSession[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    setFailed(false);
    listSessions().then(setSessions).catch(() => setFailed(true));
  }, []);
  useEffect(load, [load]);

  const revoke = async (s: DeviceSession) => {
    if (!(await confirmAction({ title: "Sign this device out?", message: `${deviceName(s.device)} will need to sign in again.`, confirmLabel: "Sign out device" }))) return;
    setBusyId(s.id);
    try { await revokeSession(s.id); setSessions(list => (list || []).filter(x => x.id !== s.id)); toast.success("Device signed out."); }
    catch (e) { toast.fail(e, "We couldn't sign that device out."); }
    finally { setBusyId(null); }
  };

  const when = (iso: string) => new Date(iso).toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });

  return (
    <Card>
      <CardHeader title="Where you're signed in" subtitle="Devices that can still open your account. Sign out any you don't recognise." />
      {failed ? <ErrorState message="We couldn't load your devices." onRetry={load} />
        : sessions === null ? <SkeletonList rows={2} />
        : sessions.length === 0 ? <EmptyState title="No active devices" text="Sign in again to see this device here." />
        : sessions.map(s => (
          <div className="st-device" key={s.id}>
            <div style={{ minWidth: 0 }}>
              <b>{deviceName(s.device)} {s.current && <Badge tone="success">This device</Badge>}</b>
              <small>Signed in {when(s.signedInAt)}</small>
            </div>
            {!s.current && <Button variant="secondary" size="sm" loading={busyId === s.id} onClick={() => revoke(s)}>Sign out</Button>}
          </div>
        ))}
    </Card>
  );
}

/* ------------------------------- Workspace ------------------------------ */
function WorkspaceSection({ workspace }: { workspace: Workspace }) {
  const toast = useToast();
  const [company, setCompany] = useState<CompanyInfo | null>(null);
  const [failed, setFailed] = useState(false);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const canManage = can("company:manage");

  const load = useCallback(() => {
    setFailed(false);
    getMyCompany().then(c => { setCompany(c); setName(c.name); }).catch(() => setFailed(true));
  }, []);
  useEffect(load, [load]);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setSaving(true);
    try {
      const c = await updateMyCompany(name.trim());
      setCompany(c); setName(c.name); localStorage.setItem("companyName", c.name);
      toast.success("Company name updated.");
    } catch (err) { toast.fail(err, "We couldn't rename the company."); }
    finally { setSaving(false); }
  };

  if (failed) return <ErrorState message="We couldn't load your workspace details." onRetry={load} />;
  if (!company) return <SkeletonList rows={3} />;
  return (
    <div className="st-section">
      <Card>
        <CardHeader title="Workspace" subtitle="Employees sign in with the workspace address." />
        <div className="st-kv"><span>Name</span><span>{company.name}</span></div>
        <div className="st-kv"><span>Workspace address</span><span>{company.slug}</span></div>
        <div className="st-kv"><span>Members</span><span>{company.memberCount}</span></div>
        <div className="st-kv"><span>Created</span><span>{new Date(company.createdAt).toLocaleDateString()}</span></div>
      </Card>
      {canManage && (
        <Card>
          <CardHeader title="Rename company" subtitle="The workspace address stays the same." />
          <form onSubmit={save}>
            <Field label="Company name" required><Input value={name} onChange={e => setName(e.target.value)} maxLength={120} /></Field>
            <div className="tn-form-actions"><Button type="submit" loading={saving} disabled={!name.trim() || name.trim() === company.name}>Save name</Button></div>
          </form>
        </Card>
      )}
      {workspace === "admin" && (
        <Card>
          <CardHeader title="People and access" subtitle="Who is in the workspace and what they can do." />
          {can("user:create") && (
            <div className="st-row"><div><b>Invitations</b><small>Invite people and see who has not joined yet.</small></div><Link to="/admin/invitations" className="tn-link">Open</Link></div>
          )}
          <div className="st-row"><div><b>Employees</b><small>Roles, status and departments.</small></div><Link to="/admin/employees" className="tn-link">Open</Link></div>
          {can("audit:view") && <div className="st-row"><div><b>Audit log</b><small>Who changed what, and when.</small></div><Link to="/admin/audit" className="tn-link">Open</Link></div>}
        </Card>
      )}
    </div>
  );
}

/* --------------------------- Tenemsa AI & privacy ------------------------ */
function AiSection({ workspace }: { workspace: Workspace }) {
  const toast = useToast();
  const confirmAction = useConfirm();
  const canManage = can("company:manage");
  const [company, setCompany] = useState<CompanyInfo | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => { if (canManage) getMyCompany().then(setCompany).catch(() => {}); }, [canManage]);

  const toggleAi = async (next: boolean) => {
    if (!company) return;
    if (!next && !(await confirmAction({ title: "Switch Tenemsa AI off?", message: "AI features stop working for everyone in this company until you switch them back on. Nothing is deleted.", confirmLabel: "Switch off" }))) return;
    setBusy("ai");
    try { setCompany(await setCompanyAi(next)); toast.success(next ? "AI features are on." : "AI features are off."); }
    catch (e) { toast.fail(e, "We couldn't change the AI setting."); }
    finally { setBusy(null); }
  };

  const clear = async (kind: "HR_ASSISTANT" | "KB_ASK", label: string) => {
    if (!(await confirmAction({ title: `Delete your ${label} chats?`, message: "Your saved conversations are removed for good. Other people's chats are not affected.", confirmLabel: "Delete chats" }))) return;
    setBusy(kind);
    try { await deleteAllChats(kind); toast.success("Your saved chats were deleted."); }
    catch (e) { toast.fail(e, "We couldn't delete those chats."); }
    finally { setBusy(null); }
  };

  return (
    <div className="st-section">
      {canManage && (
        <Card>
          <CardHeader title="Tenemsa AI" subtitle="Applies to everyone in the company." />
          <div className="st-row">
            <div>
              <b>AI features</b>
              <small>Meeting summaries, task suggestions, reports, the writer and the assistants send the text you give them (and related board or leave data) to Tenemsa AI. Switch off if your company does not allow that.</small>
            </div>
            {company ? <Switch checked={company.aiEnabled} disabled={busy === "ai"} onChange={toggleAi} label="AI features" /> : null}
          </div>
          {can("ai:usage:view") && workspace === "admin" && (
            <div className="st-row"><div><b>Usage</b><small>See how much AI the company has used.</small></div><Link to="/admin/ai-usage" className="tn-link">Open</Link></div>
          )}
        </Card>
      )}
      <Card>
        <CardHeader title="Data and privacy" subtitle="Your AI chats are private to you. Nobody else in the company can read them." />
        <div className="st-row">
          <div><b>AI Assistant chats</b><small>Delete every saved conversation with the HR assistant.</small></div>
          <Button variant="secondary" size="sm" loading={busy === "HR_ASSISTANT"} onClick={() => clear("HR_ASSISTANT", "AI Assistant")}>Delete all</Button>
        </div>
        <div className="st-row">
          <div><b>Ask documents chats</b><small>Delete every saved conversation about company documents.</small></div>
          <Button variant="secondary" size="sm" loading={busy === "KB_ASK"} onClick={() => clear("KB_ASK", "Ask documents")}>Delete all</Button>
        </div>
      </Card>
    </div>
  );
}

/* --------------------------------- Page --------------------------------- */
export default function SettingsPage({ workspace }: { workspace: Workspace }) {
  const [params, setParams] = useSearchParams();
  const tabs: { id: TabId; label: string }[] = [
    { id: "account", label: "My account" },
    { id: "appearance", label: "Appearance" },
    { id: "notifications", label: "Notifications" },
    { id: "security", label: "Security" },
    ...(workspace === "admin" ? [{ id: "workspace" as TabId, label: "Workspace" }] : []),
    { id: "ai", label: "AI and privacy" },
  ];
  const wanted = params.get("tab") as TabId | null;
  const value: TabId = tabs.some(t => t.id === wanted) ? (wanted as TabId) : "account";

  return (
    <div>
      <PageHeader title="Settings" subtitle="Your account, preferences and security." />
      <Tabs tabs={tabs} value={value} label="Settings sections" onChange={id => setParams({ tab: id }, { replace: true })} />
      <div className="st-layout" style={{ marginTop: 16 }}>
        {value === "account" && <AccountSection workspace={workspace} />}
        {value === "appearance" && <AppearanceSection />}
        {value === "notifications" && <NotificationsSection />}
        {value === "security" && <div className="st-section"><PasswordCard /><DevicesCard /></div>}
        {value === "workspace" && <WorkspaceSection workspace={workspace} />}
        {value === "ai" && <AiSection workspace={workspace} />}
      </div>
    </div>
  );
}

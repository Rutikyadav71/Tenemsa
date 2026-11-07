import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, ago, errText, fmtBytes, fmtDate, fmtNum } from "../api/client";
import { Alert, Badge, Button, Field, Input, Modal, PageHead, Select, Skeleton, Stat, Textarea, statusLabel, statusTone } from "../components/ui";

interface Detail {
  id: number; name: string; slug: string; status: "ACTIVE" | "FROZEN" | "DELETED"; statusReason: string | null; statusChangedAt: string | null; aiEnabled: boolean; createdAt: string;
  owner?: { name: string; email: string; emailVerified: boolean };
  people: { total: number; frozen: number; byRole: { role: string; count: number }[] };
  limits: { plan: string; maxUsers: number | null; aiDailyLimit: number | null };
  usage: Record<string, number>;
  activity: { signInsLast7Days: number; failedSignInsLast7Days: number; lastSignInAt: string | null };
  history: { action: string; admin: string; details: string; at: string }[];
}
type Act = "freeze" | "unfreeze" | "close" | "restore" | "ai" | "verify" | "limits";

const ACTION_TEXT: Record<string, string> = {
  COMPANY_FROZEN: "Frozen", COMPANY_UNFROZEN: "Unfrozen", COMPANY_CLOSED: "Closed", COMPANY_RESTORED: "Restored", COMPANY_AI_SET: "AI setting", COMPANY_LIMITS_SET: "Plan and limits", OWNER_VERIFICATION_SENT: "Owner email",
};

export default function CompanyDetail() {
  const { id } = useParams();
  const [d, setD] = useState<Detail | null>(null), [err, setErr] = useState<string | null>(null), [ok, setOk] = useState<string | null>(null);
  const [act, setAct] = useState<Act | null>(null);
  const load = useCallback(() => api<Detail>(`/api/platform/companies/${id}`).then(setD).catch(e => setErr(errText(e))), [id]);
  useEffect(() => { setD(null); setErr(null); load(); }, [load]);

  if (err && !d) return <><PageHead title="Company" /><Alert>{err}</Alert><Link to="/companies">Back to companies</Link></>;
  if (!d) return <><PageHead title="Company" /><Skeleton h={200} /></>;
  const u = d.usage;
  return (
    <>
      <p className="pc-crumb"><Link to="/companies">Companies</Link> / {d.name}</p>
      <PageHead title={d.name} sub={`Workspace ${d.slug} · created ${fmtDate(d.createdAt)}`}
        actions={<Badge tone={statusTone(d.status)} dot>{statusLabel(d.status)}</Badge>} />
      {ok && <Alert tone="success">{ok}</Alert>}
      {err && <Alert>{err}</Alert>}
      {d.status !== "ACTIVE" && (
        <Alert tone={d.status === "FROZEN" ? "info" : "error"}>
          {d.status === "FROZEN" ? "Frozen" : "Closed"} on {fmtDate(d.statusChangedAt)}. Reason: {d.statusReason ?? "—"}. Nobody from this company can sign in.
        </Alert>
      )}

      <section className="tn-card tn-card-pad pc-actions">
        <div><h2 className="tn-card-title">Actions</h2><p className="tn-card-sub">Every action needs a reason where it matters and is written to the audit log.</p></div>
        <div className="pc-actions-btns">
          {d.status === "ACTIVE" && <Button variant="danger" onClick={() => setAct("freeze")}>Freeze company</Button>}
          {d.status === "FROZEN" && <Button variant="primary" onClick={() => setAct("unfreeze")}>Unfreeze company</Button>}
          {d.status === "DELETED" && <Button variant="primary" onClick={() => setAct("restore")}>Restore company</Button>}
          {d.status !== "DELETED" && <Button variant="danger" onClick={() => setAct("close")}>Close company</Button>}
          {d.status !== "DELETED" && <Button onClick={() => setAct("ai")}>{d.aiEnabled ? "Switch AI off" : "Switch AI on"}</Button>}
          {d.status !== "DELETED" && <Button onClick={() => setAct("limits")}>Plan and limits</Button>}
          {d.status !== "DELETED" && d.owner && <Button onClick={() => setAct("verify")}>Resend owner verification</Button>}
        </div>
      </section>

      <div className="pc-two">
        <section className="tn-card tn-card-pad">
          <h2 className="tn-card-title">Owner</h2>
          {d.owner ? (
            <dl className="pc-dl">
              <dt>Name</dt><dd>{d.owner.name}</dd>
              <dt>Email</dt><dd>{d.owner.email}</dd>
              <dt>Email verified</dt><dd>{d.owner.emailVerified ? "Yes" : "No"}</dd>
            </dl>
          ) : <p className="pc-muted">No primary owner found.</p>}
          <h2 className="tn-card-title" style={{ marginTop: 18 }}>Activity</h2>
          <dl className="pc-dl">
            <dt>Last sign-in</dt><dd>{ago(d.activity.lastSignInAt)}</dd>
            <dt>Sign-ins (7 days)</dt><dd>{fmtNum(d.activity.signInsLast7Days)}</dd>
            <dt>Failed sign-ins (7 days)</dt><dd>{fmtNum(d.activity.failedSignInsLast7Days)}</dd>
            <dt>AI</dt><dd>{d.aiEnabled ? "On" : "Off"}</dd>
          </dl>
        </section>
        <section className="tn-card tn-card-pad">
          <h2 className="tn-card-title">People</h2>
          <p className="pc-big">{fmtNum(d.people.total)} <span>{d.people.frozen} frozen</span></p>
          <ul className="pc-roles">{d.people.byRole.map(r => <li key={r.role}><span>{r.role.charAt(0) + r.role.slice(1).toLowerCase()}</span><b>{r.count}</b></li>)}</ul>
          <p className="tn-hint">Counts only. Names, salaries and messages are not shown here.</p>
        </section>
      </div>

      <div className="pc-grid">
        <Stat label="Plan" value={d.limits.plan.charAt(0) + d.limits.plan.slice(1).toLowerCase()} sub={d.limits.maxUsers ? `${fmtNum(d.people.total)} of ${fmtNum(d.limits.maxUsers)} people` : "No people limit"} tone={d.limits.maxUsers && d.people.total >= d.limits.maxUsers ? "warning" : undefined} />
        <Stat label="AI per day" value={d.limits.aiDailyLimit == null ? "Default" : fmtNum(d.limits.aiDailyLimit)} sub="Requests for the whole company" />
        <Stat label="Projects" value={fmtNum(u.projects)} sub={`${fmtNum(u.issues)} issues`} />
        <Stat label="Leave requests" value={fmtNum(u.leaveRequests)} />
        <Stat label="Meetings" value={fmtNum(u.meetings)} />
        <Stat label="Documents" value={fmtNum(u.documents)} sub={`${fmtNum(u.knowledgeDocuments)} in the knowledge base`} />
        <Stat label="Storage" value={fmtBytes(u.storageBytes)} />
        <Stat label="AI requests (30 days)" value={fmtNum(u.aiRequestsLast30Days)} sub={`${fmtNum(u.aiFailedLast30Days)} failed`} />
        <Stat label="AI tokens (30 days)" value={fmtNum(u.aiInputTokensLast30Days + u.aiOutputTokensLast30Days)} sub="In plus out" />
      </div>

      <section className="tn-card">
        <div className="tn-card-head"><h2 className="tn-card-title">Platform history</h2></div>
        <ul className="pc-list">
          {d.history.map((h, i) => <li key={i}><div><b>{ACTION_TEXT[h.action] ?? h.action}</b><span>{h.details} · {h.admin}</span></div><span className="pc-muted">{fmtDate(h.at)}</span></li>)}
          {d.history.length === 0 && <li className="pc-muted">No platform actions yet.</li>}
        </ul>
      </section>

      {act && <ActionModal act={act} d={d} onClose={() => setAct(null)} onDone={m => { setAct(null); setOk(m); setErr(null); load(); }} />}
    </>
  );
}

function ActionModal({ act, d, onClose, onDone }: { act: Act; d: Detail; onClose: () => void; onDone: (m: string) => void }) {
  const [plan, setPlan] = useState(d.limits.plan), [maxUsers, setMaxUsers] = useState(d.limits.maxUsers?.toString() ?? ""), [aiLimit, setAiLimit] = useState(d.limits.aiDailyLimit?.toString() ?? "");
  const [reason, setReason] = useState(""), [confirm, setConfirm] = useState(""), [busy, setBusy] = useState(false), [err, setErr] = useState<string | null>(null);
  const base = `/api/platform/companies/${d.id}`;
  const cfg = {
    freeze: { title: "Freeze company", text: `Everyone at ${d.name} will be signed out at once and cannot sign in until you unfreeze it. Their data is kept.`, label: "Freeze", danger: true, needReason: true, run: () => api(`${base}/freeze`, { method: "POST", body: { reason } }), done: "Company frozen." },
    unfreeze: { title: "Unfreeze company", text: `People at ${d.name} will be able to sign in again.`, label: "Unfreeze", danger: false, needReason: false, run: () => api(`${base}/unfreeze`, { method: "POST", body: { reason } }), done: "Company unfrozen." },
    close: { title: "Close company", text: `${d.name} is closed: nobody can sign in. Nothing is deleted from the database and you can restore it later.`, label: "Close company", danger: true, needReason: true, run: () => api(`${base}/close`, { method: "POST", body: { reason, confirm } }), done: "Company closed." },
    restore: { title: "Restore company", text: `${d.name} becomes active again.`, label: "Restore", danger: false, needReason: false, run: () => api(`${base}/restore`, { method: "POST" }), done: "Company restored." },
    ai: { title: d.aiEnabled ? "Switch AI off" : "Switch AI on", text: d.aiEnabled ? "Tenemsa AI stops working for this company." : "Tenemsa AI works again for this company.", label: d.aiEnabled ? "Switch off" : "Switch on", danger: d.aiEnabled, needReason: false, run: () => api(`${base}/ai`, { method: "PUT", body: { enabled: !d.aiEnabled } }), done: "AI setting changed." },
    limits: { title: "Plan and limits", text: `Sets what ${d.name} may use. Leave a limit empty for no limit (or the default for AI).`, label: "Save", danger: false, needReason: false, run: () => api(`${base}/limits`, { method: "PUT", body: { plan, maxUsers: maxUsers === "" ? null : Number(maxUsers), aiDailyLimit: aiLimit === "" ? null : Number(aiLimit) } }), done: "Plan and limits saved." },
    verify: { title: "Resend owner verification", text: `Sends the verification email again to ${d.owner?.email}.`, label: "Send email", danger: false, needReason: false, run: () => api(`${base}/resend-owner-verification`, { method: "POST" }), done: "Verification email sent." },
  }[act];
  const blocked = (cfg.needReason && reason.trim().length < 5) || (act === "close" && confirm !== d.slug);

  async function go() {
    setBusy(true); setErr(null);
    try { await cfg.run(); onDone(cfg.done); } catch (e) { setErr(errText(e)); } finally { setBusy(false); }
  }
  return (
    <Modal title={cfg.title} onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant={cfg.danger ? "danger" : "primary"} loading={busy} disabled={blocked} onClick={go}>{cfg.label}</Button></>}>
      <p>{cfg.text}</p>
      {err && <Alert>{err}</Alert>}
      {(act === "freeze" || act === "close" || act === "unfreeze") && (
        <Field label={act === "unfreeze" ? "Note (optional)" : "Reason"} hint={act === "unfreeze" ? undefined : "At least 5 characters. Kept in the audit log."}>
          <Textarea value={reason} onChange={e => setReason(e.target.value)} maxLength={300} />
        </Field>
      )}
      {act === "limits" && (
        <div style={{ display: "grid", gap: 12 }}>
          <Field label="Plan"><Select value={plan} onChange={e => setPlan(e.target.value)}><option value="FREE">Free</option><option value="STARTER">Starter</option><option value="BUSINESS">Business</option><option value="ENTERPRISE">Enterprise</option></Select></Field>
          <Field label="Most people allowed" hint={`Now ${d.people.total}. People already in are never removed; new ones are refused at the limit.`}><Input type="number" min={1} value={maxUsers} onChange={e => setMaxUsers(e.target.value)} placeholder="No limit" /></Field>
          <Field label="AI requests per day" hint="Whole company. Empty uses the platform default."><Input type="number" min={0} value={aiLimit} onChange={e => setAiLimit(e.target.value)} placeholder="Default" /></Field>
        </div>
      )}
      {act === "close" && (
        <Field label={`Type the workspace name (${d.slug}) to confirm`}><Input value={confirm} onChange={e => setConfirm(e.target.value)} autoComplete="off" /></Field>
      )}
    </Modal>
  );
}

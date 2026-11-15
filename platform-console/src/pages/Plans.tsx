import { useCallback, useEffect, useState } from "react";
import { api, errText, fmtNum } from "../api/client";
import { Alert, Badge, Button, Field, Input, Modal, PageHead, Skeleton } from "../components/ui";

interface Plan {
  code: string; name: string; description: string | null; pricePaise: number; maxUsers: number | null; monthlyAiTokens: number | null;
  maxConnections: number | null; contactOnly: boolean; active: boolean; razorpayPlanId: string | null; activeSubscriptions: number;
}
interface Overview { paymentsEnabled: boolean; plans: Plan[] }

const rupees = (paise: number) => (paise === 0 ? "Free" : `₹${(paise / 100).toLocaleString("en-IN")}`);

export default function Plans() {
  const [o, setO] = useState<Overview | null>(null), [err, setErr] = useState<string | null>(null), [ok, setOk] = useState<string | null>(null);
  const [edit, setEdit] = useState<Plan | null>(null), [busy, setBusy] = useState<string | null>(null);
  const load = useCallback(() => { api<Overview>("/api/platform/billing").then(setO).catch(e => setErr(errText(e))); }, []);
  useEffect(load, [load]);

  const sync = async (p: Plan) => {
    setBusy(p.code); setErr(null); setOk(null);
    try { setO(await api<Overview>(`/api/platform/billing/plans/${p.code}/razorpay`, { method: "POST" })); setOk(`Razorpay plan created for ${p.name}.`); }
    catch (e) { setErr(errText(e)); } finally { setBusy(null); }
  };

  return (
    <>
      <PageHead title="Plans" sub="What each plan costs and allows. Companies see these on their Plan & billing page." />
      {err && <Alert>{err}</Alert>}
      {ok && <Alert tone="success">{ok}</Alert>}
      {o && !o.paymentsEnabled && <Alert tone="info">Razorpay keys are not set on the server, so companies cannot pay online. You can still assign plans from a company's page.</Alert>}
      {!o && !err && <Skeleton h={220} />}
      {o && (
        <section className="tn-card tn-card-pad" style={{ overflowX: "auto" }}>
          <table className="pc-table">
            <thead><tr><th>Plan</th><th>Price / month</th><th>People</th><th>AI tokens / month</th><th>Apps</th><th>Paying now</th><th>Razorpay</th><th /></tr></thead>
            <tbody>
              {o.plans.map(p => (
                <tr key={p.code}>
                  <td><b>{p.name}</b> <span style={{ opacity: .6 }}>{p.code}</span> {!p.active && <Badge tone="warning">Hidden</Badge>}</td>
                  <td>{p.contactOnly ? "Custom" : rupees(p.pricePaise)}</td>
                  <td>{p.maxUsers == null ? "No limit" : fmtNum(p.maxUsers)}</td>
                  <td>{p.monthlyAiTokens == null ? "No limit" : fmtNum(p.monthlyAiTokens)}</td>
                  <td>{p.maxConnections == null ? "No limit" : p.maxConnections}</td>
                  <td>{p.activeSubscriptions}</td>
                  <td>{p.contactOnly || p.pricePaise === 0 ? "—" : p.razorpayPlanId ? <Badge tone="success">Ready</Badge> : <Badge>Made at first checkout</Badge>}</td>
                  <td style={{ whiteSpace: "nowrap" }}>
                    <Button size="sm" onClick={() => setEdit(p)}>Edit</Button>{" "}
                    {o.paymentsEnabled && !p.contactOnly && p.pricePaise > 0 && !p.razorpayPlanId && <Button size="sm" loading={busy === p.code} onClick={() => sync(p)}>Create in Razorpay</Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="tn-hint">Changing a price affects new subscribers only. Existing subscribers keep the price they signed up at.</p>
        </section>
      )}
      {edit && <EditPlan plan={edit} onClose={() => setEdit(null)} onSaved={x => { setO(x); setEdit(null); setOk("Plan saved."); }} />}
    </>
  );
}

function EditPlan({ plan, onClose, onSaved }: { plan: Plan; onClose: () => void; onSaved: (o: Overview) => void }) {
  const [name, setName] = useState(plan.name), [desc, setDesc] = useState(plan.description ?? "");
  const [price, setPrice] = useState(String(plan.pricePaise / 100)), [users, setUsers] = useState(plan.maxUsers?.toString() ?? "");
  const [tok, setTok] = useState(plan.monthlyAiTokens?.toString() ?? ""), [conn, setConn] = useState(plan.maxConnections?.toString() ?? "");
  const [active, setActive] = useState(plan.active), [busy, setBusy] = useState(false), [err, setErr] = useState<string | null>(null);
  const save = async () => {
    setBusy(true); setErr(null);
    try {
      onSaved(await api<Overview>(`/api/platform/billing/plans/${plan.code}`, { method: "PUT", body: {
        name, description: desc, pricePaise: Math.round(Number(price || 0) * 100),
        maxUsers: users === "" ? null : Number(users), monthlyAiTokens: tok === "" ? 0 : Number(tok), maxConnections: conn === "" ? null : Number(conn), active } }));
    } catch (e) { setErr(errText(e)); setBusy(false); }
  };
  return (
    <Modal title={`Edit ${plan.name}`} onClose={onClose} footer={<><Button onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} onClick={save}>Save</Button></>}>
      {err && <Alert>{err}</Alert>}
      <div style={{ display: "grid", gap: 12 }}>
        <Field label="Name"><Input value={name} onChange={e => setName(e.target.value)} maxLength={60} /></Field>
        <Field label="Short description"><Input value={desc} onChange={e => setDesc(e.target.value)} maxLength={250} /></Field>
        <Field label="Price per month (₹)" hint="0 for free or custom plans."><Input type="number" min={0} value={price} onChange={e => setPrice(e.target.value)} /></Field>
        <Field label="Most people" hint="Empty means no limit."><Input type="number" min={1} value={users} onChange={e => setUsers(e.target.value)} placeholder="No limit" /></Field>
        <Field label="AI tokens per month" hint="Empty means no limit."><Input type="number" min={0} value={tok} onChange={e => setTok(e.target.value)} placeholder="No limit" /></Field>
        <Field label="Connected apps"><Input type="number" min={0} value={conn} onChange={e => setConn(e.target.value)} placeholder="No limit" /></Field>
        <label style={{ display: "flex", gap: 8, alignItems: "center" }}><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} /> Show on companies' billing page</label>
      </div>
    </Modal>
  );
}

import { useCallback, useEffect, useState } from "react";
import { Alert, Badge, Button, Card, CardHeader, EmptyState, ErrorState, Field, Input, PageHeader, SkeletonList, useConfirm, useToast } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";
import { errorMessage } from "../../../shared/lib/errors";
import {
  cancelSubscription, changePlan, getBilling, getDetails, getUsage, openCheckout, openTopupCheckout, rupees, saveDetails, startCheckout,
  startTopup, tokens, usageCsv, verifyPayment, verifyTopup,
  type BillingDetails, type BillingOverview, type PlanView, type TopupPack, type Usage,
} from "../services/billingService";
import "../billing.css";

function Meter({ label, used, max, format = String }: { label: string; used: number; max: number | null; format?: (n: number) => string }) {
  const pct = max ? Math.min(100, (used / max) * 100) : 0;
  const tone = pct >= 100 ? "crit" : pct >= 80 ? "warn" : "";
  return (
    <div className="bl-meter">
      <div className="row"><b>{label}</b><span>{format(used)} of {max == null ? "unlimited" : format(max)}</span></div>
      <div className={`bl-bar ${tone}`} role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={max ?? undefined} aria-valuenow={used}><i style={{ width: `${max ? pct : 0}%` }} /></div>
    </div>
  );
}

function UsagePanel() {
  const [u, setU] = useState<Usage | null>(null);
  const [err, setErr] = useState("");
  useEffect(() => { getUsage().then(setU).catch(e => setErr(errorMessage(e, "We couldn't load the usage."))); }, []);
  const download = () => {
    if (!u) return;
    const url = URL.createObjectURL(new Blob([usageCsv(u)], { type: "text/csv" }));
    const a = document.createElement("a"); a.href = url; a.download = `ai-usage-${u.period}.csv`; a.click(); URL.revokeObjectURL(url);
  };
  const max = (rows: { tokens: number }[]) => Math.max(1, ...rows.map(r => r.tokens));
  const rows = (title: string, data: { name: string; requests: number; tokens: number }[]) => (
    <div className="bl-usage-col">
      <h4>{title}</h4>
      {data.length === 0 ? <p className="bl-muted">No AI use yet this month.</p> : data.map(r => (
        <div className="bl-urow" key={r.name}>
          <div className="row"><span>{r.name}</span><b>{tokens(r.tokens)}</b></div>
          <div className="bl-bar"><i style={{ width: `${(r.tokens / max(data)) * 100}%` }} /></div>
        </div>
      ))}
    </div>
  );
  return (
    <Card>
      <CardHeader title="Where the AI tokens went" subtitle={u ? `This month (${u.period}).` : undefined}
        actions={u ? <Button size="sm" variant="ghost" onClick={download}>Download CSV</Button> : undefined} />
      {err ? <Alert tone="error">{err}</Alert> : !u ? <SkeletonList rows={3} /> : (
        <div className="bl-usage">
          {rows("By feature", u.byFeature)}
          {rows("By person", u.byPerson)}
          {rows("By day", u.byDay.map(d => ({ name: new Date(d.usage_day).toLocaleDateString("en-IN", { day: "numeric", month: "short" }), requests: d.requests, tokens: d.tokens })))}
        </div>
      )}
    </Card>
  );
}

function DetailsCard({ canEdit }: { canEdit: boolean }) {
  const toast = useToast();
  const [d, setD] = useState<BillingDetails | null>(null);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState("");
  useEffect(() => { getDetails().then(setD).catch(e => setErr(errorMessage(e, "We couldn't load the billing details."))); }, []);
  if (err && !d) return <Alert tone="error">{err}</Alert>;
  if (!d) return null;
  const set = (k: keyof BillingDetails) => (e: React.ChangeEvent<HTMLInputElement>) => setD({ ...d, [k]: e.target.value });
  const save = async () => {
    setSaving(true); setErr("");
    try { setD(await saveDetails(d)); toast.success("Billing details saved."); }
    catch (e) { setErr(errorMessage(e, "We couldn't save the details.")); }
    finally { setSaving(false); }
  };
  return (
    <Card>
      <CardHeader title="Billing details" subtitle="Shown on your receipts. Receipts are proof of payment, not GST tax invoices." />
      {err && <Alert tone="error">{err}</Alert>}
      <div className="bl-form">
        <Field label="Legal name"><Input value={d.legalName} onChange={set("legalName")} disabled={!canEdit} maxLength={200} /></Field>
        <Field label="GSTIN (optional)"><Input value={d.gstin} onChange={set("gstin")} disabled={!canEdit} maxLength={15} placeholder="27ABCDE1234F1Z5" /></Field>
        <Field label="Address"><Input value={d.address} onChange={set("address")} disabled={!canEdit} maxLength={300} /></Field>
        <Field label="City"><Input value={d.city} onChange={set("city")} disabled={!canEdit} maxLength={100} /></Field>
        <Field label="State"><Input value={d.state} onChange={set("state")} disabled={!canEdit} maxLength={100} /></Field>
        <Field label="Pincode"><Input value={d.pincode} onChange={set("pincode")} disabled={!canEdit} maxLength={12} /></Field>
        <Field label="Billing email"><Input type="email" value={d.billingEmail} onChange={set("billingEmail")} disabled={!canEdit} maxLength={200} /></Field>
      </div>
      {canEdit && <div style={{ marginTop: 12 }}><Button variant="primary" loading={saving} onClick={save}>Save details</Button></div>}
    </Card>
  );
}

export default function BillingPage() {
  const toast = useToast();
  const confirm = useConfirm();
  const [o, setO] = useState<BillingOverview | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const isOwner = can("subscription:manage");

  const load = useCallback(() => { setError(""); getBilling().then(setO).catch(e => setError(errorMessage(e, "We couldn't load billing."))); }, []);
  useEffect(load, [load]);

  const buy = async (p: PlanView) => {
    setBusy(p.code); setNotice("");
    try {
      const c = await startCheckout(p.code);
      const res = await openCheckout(c, { name: localStorage.getItem("name") || undefined, email: localStorage.getItem("email") || undefined });
      if (res) {
        setO(await verifyPayment({ paymentId: res.razorpay_payment_id, subscriptionId: res.razorpay_subscription_id, signature: res.razorpay_signature }));
        toast.success(`You are now on the ${p.name} plan.`);
      }
    } catch (e) { setNotice(errorMessage(e, "We couldn't start the payment.")); }
    finally { setBusy(null); }
  };

  const switchTo = async (p: PlanView) => {
    if (!await confirm({ title: `Switch to ${p.name}?`, message: `The change takes effect at your next renewal (${sub?.renewsOn ?? "the end of this period"}). Until then you stay on your current plan and are charged the same.`, confirmLabel: "Schedule the change" })) return;
    setBusy(p.code); setNotice("");
    try { setO(await changePlan(p.code)); toast.success(`You will move to ${p.name} at the next renewal.`); }
    catch (e) { setNotice(errorMessage(e, "We couldn't schedule the change.")); }
    finally { setBusy(null); }
  };

  const buyTopup = async (k: TopupPack) => {
    setBusy("topup:" + k.code); setNotice("");
    try {
      const c = await startTopup(k.code);
      const res = await openTopupCheckout(c, { name: localStorage.getItem("name") || undefined, email: localStorage.getItem("email") || undefined });
      if (res) {
        setO(await verifyTopup({ orderId: res.razorpay_order_id, paymentId: res.razorpay_payment_id, signature: res.razorpay_signature }));
        toast.success(`${tokens(k.tokens)} tokens added for this month.`);
      }
    } catch (e) { setNotice(errorMessage(e, "We couldn't start the payment.")); }
    finally { setBusy(null); }
  };

  const cancel = async () => {
    if (!await confirm({ title: "Cancel your subscription?", message: "You keep your plan until the end of the period you have paid for, then move to Free. You can subscribe again any time.", confirmLabel: "Cancel subscription", tone: "danger" })) return;
    setBusy("cancel"); setNotice("");
    try { setO(await cancelSubscription()); toast.success("Your subscription will end at the close of this period."); }
    catch (e) { setNotice(errorMessage(e, "We couldn't cancel it.")); }
    finally { setBusy(null); }
  };

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!o) return <SkeletonList rows={5} />;
  const sub = o.subscription;
  const current = o.plans.find(p => p.code === o.currentPlan);

  return (
    <div>
      <PageHeader title="Plan & billing" subtitle="Your plan, this month's usage and payments." />
      {notice && <Alert tone="error">{notice}</Alert>}
      {!o.paymentsEnabled && <Alert tone="info">Online payments are not switched on for this installation yet. The Tenemsa team can change your plan for you.</Alert>}

      <Card>
        <CardHeader title={<>Current plan: {current?.name ?? o.currentPlan}</>}
          subtitle={sub ? (sub.cancelAtPeriodEnd ? `Ends on ${sub.renewsOn ?? "the end of the period"}. It will not renew.`
            : sub.trial ? `Free trial until ${sub.renewsOn ?? "the trial ends"}. Your first payment is taken then; cancel before that to pay nothing.`
            : sub.renewsOn ? `Renews on ${sub.renewsOn}.` : "Active.") : undefined}
          actions={isOwner && sub && !sub.cancelAtPeriodEnd ? <Button variant="danger" size="sm" loading={busy === "cancel"} onClick={cancel}>Cancel subscription</Button> : undefined} />
        <div className="bl-meters">
          <Meter label="AI this month" used={o.usage.aiTokensUsed} max={o.usage.aiTokenBudget} format={tokens} />
          <Meter label="People" used={o.usage.people} max={o.usage.maxPeople} />
        </div>
        <p style={{ fontSize: 13, color: "var(--text-3)", margin: "12px 0 0" }}>
          {o.usage.aiTopupTokens > 0 && <>Includes {tokens(o.usage.aiTopupTokens)} bought tokens, valid this month only. </>}AI usage is counted in tokens (small pieces of text) across Writer, the HR assistant, meeting summaries and document search. It resets on {new Date(o.usage.aiResetsOn).toLocaleDateString("en-IN", { day: "numeric", month: "long" })}.
        </p>
      </Card>

      {sub?.pendingPlanCode && <Alert tone="info">You will move to the {o.plans.find(p => p.code === sub.pendingPlanCode)?.name ?? sub.pendingPlanCode} plan at your next renewal{sub.renewsOn ? ` on ${sub.renewsOn}` : ""}.</Alert>}

      <div className="bl-grid">
        {o.plans.map(p => {
          const here = p.code === o.currentPlan;
          return (
            <div key={p.code} className={`bl-plan ${here ? "current" : ""}`}>
              <h3>{p.name}{here && <Badge tone="accent">Current</Badge>}</h3>
              <div className="bl-price">{p.contactOnly ? "Custom" : p.pricePaise === 0 ? "Free" : <>{rupees(p.pricePaise)}<small> / month</small></>}</div>
              {p.description && <p>{p.description}</p>}
              {!p.contactOnly && p.pricePaise > 0 && p.trialDays > 0 && o.trialEligible && !sub && <Badge tone="success">{p.trialDays}-day free trial</Badge>}
              <ul>
                <li>{p.maxUsers == null ? "Unlimited people" : `Up to ${p.maxUsers} people`}</li>
                <li>{p.monthlyAiTokens == null ? "Unlimited AI" : `${tokens(p.monthlyAiTokens)} AI tokens a month`}</li>
                {p.maxConnections != null && <li>{p.maxConnections} connected apps</li>}
              </ul>
              {here ? null : p.contactOnly ? <p>Contact the Tenemsa team for this plan.</p>
                : p.pricePaise === 0 ? (sub && isOwner ? <p>Cancel your subscription to move to Free.</p> : null)
                : isOwner && o.paymentsEnabled ? (sub && !sub.cancelAtPeriodEnd
                    ? <Button variant="primary" loading={busy === p.code} disabled={!!sub.pendingPlanCode} onClick={() => switchTo(p)}>{sub.pendingPlanCode === p.code ? "Scheduled" : "Switch at renewal"}</Button>
                    : <Button variant="primary" loading={busy === p.code} onClick={() => buy(p)}>{p.trialDays > 0 && o.trialEligible ? `Start ${p.trialDays}-day trial` : `Choose ${p.name}`}</Button>)
                : <p>{isOwner ? "Payments are not switched on yet." : "Only an owner can change the plan."}</p>}
            </div>
          );
        })}
      </div>

      {o.topupPacks.length > 0 && o.usage.aiTokenBudget != null && (
        <Card>
          <CardHeader title="Need more AI this month?" subtitle="A one-off top-up adds tokens for the current month only. Unused tokens do not carry over." />
          <div className="bl-grid" style={{ marginTop: 0 }}>
            {o.topupPacks.map(k => (
              <div className="bl-plan" key={k.code}>
                <h3>{k.name}</h3>
                <div className="bl-price">{rupees(k.pricePaise)}</div>
                <p>{tokens(k.tokens)} AI tokens</p>
                {isOwner && o.paymentsEnabled ? <Button loading={busy === "topup:" + k.code} onClick={() => buyTopup(k)}>Buy</Button> : null}
              </div>
            ))}
          </div>
          {o.topups.map((t, i) => (
            <div className="bl-pay" key={i}>
              <div>{new Date(t.date).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })} · {tokens(t.tokens)} tokens<br /><span>{t.paymentId}</span></div>
              <b>{rupees(t.amountPaise)}</b>
            </div>
          ))}
        </Card>
      )}

      <UsagePanel />
      <DetailsCard canEdit={isOwner} />

      <Card>
        <CardHeader title="Payments" actions={o.payments.length > 0 ? <Button size="sm" variant="ghost" onClick={() => window.print()}>Print receipts</Button> : undefined} />
        {o.payments.length === 0 ? <EmptyState title="No payments yet" text="Charges for a paid plan will be listed here." /> :
          o.payments.map((p, i) => (
            <div className="bl-pay" key={i}>
              <div>{new Date(p.date).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}<br /><span>{p.paymentId}</span></div>
              <b>{p.amountPaise == null ? "" : rupees(p.amountPaise)}</b>
            </div>
          ))}
      </Card>
    </div>
  );
}

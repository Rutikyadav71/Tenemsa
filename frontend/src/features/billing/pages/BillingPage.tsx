import { useCallback, useEffect, useState } from "react";
import { Alert, Badge, Button, Card, CardHeader, EmptyState, ErrorState, PageHeader, SkeletonList, useConfirm, useToast } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";
import { errorMessage } from "../../../shared/lib/errors";
import {
  cancelSubscription, getBilling, openCheckout, rupees, startCheckout, tokens, verifyPayment,
  type BillingOverview, type PlanView,
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
          subtitle={sub ? (sub.cancelAtPeriodEnd ? `Ends on ${sub.renewsOn ?? "the end of the period"}. It will not renew.` : sub.renewsOn ? `Renews on ${sub.renewsOn}.` : "Active.") : undefined}
          actions={isOwner && sub && !sub.cancelAtPeriodEnd ? <Button variant="danger" size="sm" loading={busy === "cancel"} onClick={cancel}>Cancel subscription</Button> : undefined} />
        <div className="bl-meters">
          <Meter label="AI this month" used={o.usage.aiTokensUsed} max={o.usage.aiTokenBudget} format={tokens} />
          <Meter label="People" used={o.usage.people} max={o.usage.maxPeople} />
        </div>
        <p style={{ fontSize: 13, color: "var(--text-3)", margin: "12px 0 0" }}>
          AI usage is counted in tokens (small pieces of text) across Writer, the HR assistant, meeting summaries and document search. It resets on {new Date(o.usage.aiResetsOn).toLocaleDateString("en-IN", { day: "numeric", month: "long" })}.
        </p>
      </Card>

      <div className="bl-grid">
        {o.plans.map(p => {
          const here = p.code === o.currentPlan;
          return (
            <div key={p.code} className={`bl-plan ${here ? "current" : ""}`}>
              <h3>{p.name}{here && <Badge tone="accent">Current</Badge>}</h3>
              <div className="bl-price">{p.contactOnly ? "Custom" : p.pricePaise === 0 ? "Free" : <>{rupees(p.pricePaise)}<small> / month</small></>}</div>
              {p.description && <p>{p.description}</p>}
              <ul>
                <li>{p.maxUsers == null ? "Unlimited people" : `Up to ${p.maxUsers} people`}</li>
                <li>{p.monthlyAiTokens == null ? "Unlimited AI" : `${tokens(p.monthlyAiTokens)} AI tokens a month`}</li>
                {p.maxConnections != null && <li>{p.maxConnections} connected apps</li>}
              </ul>
              {here ? null : p.contactOnly ? <p>Contact the Tenemsa team for this plan.</p>
                : p.pricePaise === 0 ? (sub && isOwner ? <p>Cancel your subscription to move to Free.</p> : null)
                : isOwner && o.paymentsEnabled ? <Button variant="primary" loading={busy === p.code} disabled={!!sub && !sub.cancelAtPeriodEnd} onClick={() => buy(p)}>{sub && !sub.cancelAtPeriodEnd ? "Cancel first to switch" : `Choose ${p.name}`}</Button>
                : <p>{isOwner ? "Payments are not switched on yet." : "Only an owner can change the plan."}</p>}
            </div>
          );
        })}
      </div>

      <Card>
        <CardHeader title="Payments" />
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

import { useEffect, useState } from "react";
import { api, errText, fmtDate, fmtNum } from "../api/client";
import { Alert, Badge, PageHead, Skeleton } from "../components/ui";

interface Revenue {
  mrrPaise: number; payingCompanies: number; trialCompanies: number; chargedThisMonthPaise: number; topupsThisMonthPaise: number;
  byPlan: { plan: string; name: string; price_paise: number; subscribers: number; on_trial: number | null }[];
  recentSubscriptions: { company: string; plan: string; status: string; trial: boolean; renews: string | null; ending: boolean }[];
  recentPayments: { company: string; type: string; amount_paise: number | null; paid_at: string }[];
}

const rupees = (paise: number) => `₹${(paise / 100).toLocaleString("en-IN", { maximumFractionDigits: 0 })}`;

export default function RevenuePage() {
  const [r, setR] = useState<Revenue | null>(null), [err, setErr] = useState<string | null>(null);
  useEffect(() => { api<Revenue>("/api/platform/billing/revenue").then(setR).catch(e => setErr(errText(e))); }, []);
  return (
    <>
      <PageHead title="Revenue" sub="What companies pay for plans and token top-ups. Figures come from Razorpay's own reports to Tenemsa." />
      {err && <Alert>{err}</Alert>}
      {!r && !err && <Skeleton h={220} />}
      {r && (
        <>
          <div className="pc-two">
            <section className="tn-card tn-card-pad"><h2 className="tn-card-title">Monthly recurring revenue</h2>
              <p style={{ fontSize: 28, fontWeight: 700, margin: "6px 0" }}>{rupees(r.mrrPaise)}</p>
              <p className="tn-hint">{fmtNum(r.payingCompanies)} paying companies · {fmtNum(r.trialCompanies)} on a free trial (not counted)</p>
            </section>
            <section className="tn-card tn-card-pad"><h2 className="tn-card-title">Collected this month</h2>
              <p style={{ fontSize: 28, fontWeight: 700, margin: "6px 0" }}>{rupees(r.chargedThisMonthPaise + r.topupsThisMonthPaise)}</p>
              <p className="tn-hint">{rupees(r.chargedThisMonthPaise)} plans · {rupees(r.topupsThisMonthPaise)} token top-ups</p>
            </section>
          </div>
          <section className="tn-card tn-card-pad" style={{ overflowX: "auto", marginTop: 16 }}>
            <h2 className="tn-card-title">By plan</h2>
            <table className="pc-table"><thead><tr><th>Plan</th><th>Price</th><th>Subscribers</th><th>On trial</th></tr></thead>
              <tbody>{r.byPlan.map(p => <tr key={p.plan}><td><b>{p.name}</b></td><td>{rupees(p.price_paise)}</td><td>{fmtNum(p.subscribers)}</td><td>{fmtNum(p.on_trial)}</td></tr>)}</tbody></table>
          </section>
          <section className="tn-card tn-card-pad" style={{ overflowX: "auto", marginTop: 16 }}>
            <h2 className="tn-card-title">Recent subscriptions</h2>
            <table className="pc-table"><thead><tr><th>Company</th><th>Plan</th><th>Status</th><th>Renews / trial ends</th></tr></thead>
              <tbody>{r.recentSubscriptions.map((s, i) => <tr key={i}><td>{s.company}</td><td>{s.plan}</td>
                <td>{s.status} {s.trial && <Badge>Trial</Badge>} {s.ending && <Badge tone="warning">Ending</Badge>}</td><td>{s.renews ? fmtDate(s.renews) : "—"}</td></tr>)}</tbody></table>
          </section>
          <section className="tn-card tn-card-pad" style={{ overflowX: "auto", marginTop: 16 }}>
            <h2 className="tn-card-title">Recent payments</h2>
            {r.recentPayments.length === 0 ? <p className="tn-hint">No payments yet.</p> :
              <table className="pc-table"><thead><tr><th>When</th><th>Company</th><th>For</th><th>Amount</th></tr></thead>
                <tbody>{r.recentPayments.map((p, i) => <tr key={i}><td>{fmtDate(p.paid_at)}</td><td>{p.company}</td><td>{p.type === "topup.paid" ? "Token top-up" : "Plan"}</td><td>{p.amount_paise == null ? "—" : rupees(p.amount_paise)}</td></tr>)}</tbody></table>}
          </section>
        </>
      )}
    </>
  );
}

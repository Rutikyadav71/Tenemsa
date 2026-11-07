import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, errText, fmtBytes, fmtDay, fmtNum } from "../api/client";
import { Alert, Badge, PageHead, Skeleton, Stat, statusLabel, statusTone } from "../components/ui";

interface Overview {
  companies: number; active: number; frozen: number; deleted: number; demoCompanies: number; newLast7Days: number; newLast30Days: number;
  users: number; frozenUsers: number; activeCompaniesLast7Days: number; aiRequestsLast30Days: number; aiFailedLast30Days: number; storageBytes: number;
  growth: { weekStart: string; companies: number; users: number }[];
  recentCompanies: { id: number; name: string; slug: string; status: string; createdAt: string }[];
}

function Bars({ data }: { data: { label: string; value: number }[] }) {
  const max = Math.max(1, ...data.map(d => d.value));
  return (
    <div className="pc-bars" role="img" aria-label={`New companies per week: ${data.map(d => `${d.label} ${d.value}`).join(", ")}`}>
      {data.map(d => (
        <div key={d.label} className="pc-bar-col" title={`${d.label}: ${d.value}`}>
          <span className="pc-bar-n">{d.value}</span>
          <div className="pc-bar" style={{ height: `${Math.max(4, (d.value / max) * 100)}%` }} />
          <span className="pc-bar-l">{d.label}</span>
        </div>
      ))}
    </div>
  );
}

export default function Dashboard() {
  const [o, setO] = useState<Overview | null>(null), [err, setErr] = useState<string | null>(null);
  useEffect(() => { api<Overview>("/api/platform/overview").then(setO).catch(e => setErr(errText(e))); }, []);
  if (err) return <><PageHead title="Dashboard" /><Alert>{err}</Alert></>;
  if (!o) return <><PageHead title="Dashboard" /><div className="pc-grid"><Skeleton h={92} /><Skeleton h={92} /><Skeleton h={92} /><Skeleton h={92} /></div></>;
  const failPct = o.aiRequestsLast30Days ? Math.round((o.aiFailedLast30Days / o.aiRequestsLast30Days) * 100) : 0;
  return (
    <>
      <PageHead title="Dashboard" sub="Every company on the platform, at a glance." />
      {o.demoCompanies > 0 && <Alert tone="info">{o.demoCompanies} of these companies are demo data (workspace names starting with <code>demo-</code>).</Alert>}
      <div className="pc-grid">
        <Stat label="Companies" value={fmtNum(o.companies)} sub={`${o.newLast7Days} new in 7 days · ${o.newLast30Days} in 30`} />
        <Stat label="Active" value={fmtNum(o.active)} tone="success" sub={`${o.activeCompaniesLast7Days} signed in this week`} />
        <Stat label="Frozen" value={fmtNum(o.frozen)} tone={o.frozen ? "warning" : undefined} sub="Suspended by you" />
        <Stat label="Closed" value={fmtNum(o.deleted)} sub="Can be restored" />
        <Stat label="People" value={fmtNum(o.users)} sub={`${fmtNum(o.frozenUsers)} frozen accounts`} />
        <Stat label="AI requests (30 days)" value={fmtNum(o.aiRequestsLast30Days)} sub={`${failPct}% did not succeed`} tone={failPct > 25 ? "warning" : undefined} />
        <Stat label="Stored files" value={fmtBytes(o.storageBytes)} sub="Documents and chat files" />
      </div>
      <div className="pc-two">
        <section className="tn-card tn-card-pad">
          <h2 className="tn-card-title">New companies per week</h2>
          <p className="tn-card-sub">Last 12 weeks</p>
          <Bars data={o.growth.map(g => ({ label: new Date(g.weekStart).toLocaleDateString("en-IN", { day: "numeric", month: "short" }), value: g.companies }))} />
        </section>
        <section className="tn-card">
          <div className="tn-card-head"><h2 className="tn-card-title">Newest companies</h2><Link to="/companies">View all</Link></div>
          <ul className="pc-list">
            {o.recentCompanies.map(c => (
              <li key={c.id}><Link to={`/companies/${c.id}`}><b>{c.name}</b><span>{c.slug} · {fmtDay(c.createdAt)}</span></Link><Badge tone={statusTone(c.status)} dot>{statusLabel(c.status)}</Badge></li>
            ))}
            {o.recentCompanies.length === 0 && <li className="pc-muted">No companies yet.</li>}
          </ul>
        </section>
      </div>
    </>
  );
}

import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { can } from "../../../shared/lib/roles";
import { getBilling, type BillingOverview } from "../services/billingService";
import "../billing.css";

/** Warns owners and admins when the month's AI allowance is nearly or fully used. Hidden for everyone else. */
export default function AiBudgetBanner() {
  const [o, setO] = useState<BillingOverview | null>(null);
  const loc = useLocation();
  const allowed = can("billing:view");
  useEffect(() => { if (allowed) getBilling().then(setO).catch(() => { /* a banner is optional */ }); }, [allowed, loc.pathname]);
  if (!o || o.usage.aiTokenBudget == null || o.usage.aiTokenBudget <= 0) return null;
  const pct = o.usage.aiTokensUsed / o.usage.aiTokenBudget;
  if (pct < 0.8) return null;
  const out = pct >= 1;
  const base = `/${loc.pathname.split("/")[1]}`;
  return (
    <div className={`pn ${out ? "crit" : "warn"}`} role="alert">
      <div>
        <b>{out ? "Your AI allowance for this month is used up" : "Your AI allowance is almost used up"}</b>
        <p>{out ? "AI features are paused until it resets" : `${Math.round(pct * 100)}% used`} on {new Date(o.usage.aiResetsOn).toLocaleDateString("en-IN", { day: "numeric", month: "long" })}.{" "}
          {can("subscription:manage") ? <Link to={`${base}/billing`}>Upgrade your plan</Link> : "Ask an owner to upgrade the plan."}</p>
      </div>
    </div>
  );
}

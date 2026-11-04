import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Skeleton } from "../../../shared/ui";

interface Props {
  label: string;
  value: ReactNode;
  hint?: string;
  icon: ReactNode;
  tone?: "default" | "warn" | "bad" | "good";
  to?: string;
  loading?: boolean;
}

/** One headline number. Linked cards go to the screen where the number can be acted on. */
export default function KpiCard({ label, value, hint, icon, tone = "default", to, loading }: Props) {
  const body = (
    <>
      <div className="top"><span>{label}</span><span className="ic" aria-hidden="true">{icon}</span></div>
      {loading ? <Skeleton width={70} height={28} /> : <div className="val">{value}</div>}
      {hint && !loading && <div className="hint">{hint}</div>}
    </>
  );
  const cls = `db-kpi ${tone === "default" ? "" : tone}`;
  return to ? <Link to={to} className={cls}>{body}</Link> : <div className={cls}>{body}</div>;
}

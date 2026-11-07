import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, errText, fmtDate, type Page } from "../api/client";
import { Alert, Badge, PageHead, Pager, Select, Skeleton } from "../components/ui";

interface Row { id: number; createdAt: string; admin: string | null; action: string; companyId: number | null; companyName: string | null; details: string | null; ipAddress: string | null }
const ACTIONS = ["LOGIN", "LOGIN_FAILED", "ACCOUNT_LOCKED", "REGISTERED", "REGISTER_REFUSED", "TWO_FACTOR_ENABLED", "COMPANY_FROZEN", "COMPANY_UNFROZEN", "COMPANY_CLOSED", "COMPANY_RESTORED", "COMPANY_AI_SET", "COMPANY_LIMITS_SET", "ANNOUNCEMENT_SENT", "ANNOUNCEMENT_WITHDRAWN", "OWNER_VERIFICATION_SENT"];
const tone = (a: string) => (a.includes("FAILED") || a.includes("LOCKED") || a.includes("REFUSED") || a === "COMPANY_CLOSED" ? "danger" : a === "COMPANY_FROZEN" ? "warning" : a.startsWith("COMPANY") ? "accent" : "neutral") as "danger" | "warning" | "accent" | "neutral";

export default function Audit() {
  const [page, setPage] = useState(0), [action, setAction] = useState("");
  const [data, setData] = useState<Page<Row> | null>(null), [err, setErr] = useState<string | null>(null);
  useEffect(() => { setData(null); setErr(null); api<Page<Row>>(`/api/platform/audit?page=${page}&size=25${action ? `&action=${action}` : ""}`).then(setData).catch(e => setErr(errText(e))); }, [page, action]);
  return (
    <>
      <PageHead title="Audit log" sub="Everything done in this console, newest first." />
      <div className="pc-filters"><Select aria-label="Action" value={action} onChange={e => { setAction(e.target.value); setPage(0); }}><option value="">All actions</option>{ACTIONS.map(a => <option key={a} value={a}>{a.replace(/_/g, " ").toLowerCase()}</option>)}</Select></div>
      {err && <Alert>{err}</Alert>}
      {!data && !err && <Skeleton h={300} />}
      {data && (
        <div className="tn-card pc-table-wrap">
          <table className="pc-table">
            <thead><tr><th>When</th><th>Administrator</th><th>Action</th><th>Company</th><th>Details</th><th>IP</th></tr></thead>
            <tbody>
              {data.content.map(r => (
                <tr key={r.id}>
                  <td>{fmtDate(r.createdAt)}</td><td>{r.admin ?? "—"}</td><td><Badge tone={tone(r.action)}>{r.action.replace(/_/g, " ").toLowerCase()}</Badge></td>
                  <td>{r.companyId ? <Link to={`/companies/${r.companyId}`}>{r.companyName ?? r.companyId}</Link> : "—"}</td><td>{r.details ?? ""}</td><td>{r.ipAddress ?? ""}</td>
                </tr>
              ))}
              {data.content.length === 0 && <tr><td colSpan={6} className="pc-empty">Nothing recorded.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pager page={data.page} pages={data.totalPages} onPage={setPage} />}
    </>
  );
}

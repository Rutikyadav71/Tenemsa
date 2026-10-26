import { useEffect, useState } from "react";
import { fetchAuditActions, fetchAuditLog } from "../services/auditService";
import type { AuditPage } from "../../../shared/types/models";

const PAGE_SIZE = 25;

const label = (a: string) => a.toLowerCase().replace(/_/g, " ");

const badgeClass = (a: string) =>
  a.endsWith("FAILED") || a.endsWith("DELETED") || a.endsWith("REVOKED") || a.endsWith("FROZEN") ? "badge badge-red"
    : a.startsWith("LOGIN") ? "badge badge-muted"
      : "badge badge-blue";

export default function AuditLogPage() {
  const [data, setData] = useState<AuditPage | null>(null);
  const [actions, setActions] = useState<string[]>([]);
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => { fetchAuditActions().then(setActions).catch(() => { }); }, []);

  useEffect(() => {
    setLoading(true); setError("");
    fetchAuditLog(page, PAGE_SIZE, action || undefined)
      .then(setData)
      .catch(() => setError("Could not load the audit log."))
      .finally(() => setLoading(false));
  }, [page, action]);

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Audit Log</div>
          <div className="page-sub">Who did what in your company. Entries cannot be edited or deleted.</div>
        </div>
        <select className="form-control-ems" style={{ maxWidth: 220 }} value={action}
          onChange={e => { setAction(e.target.value); setPage(0); }}>
          <option value="">All actions</option>
          {actions.map(a => <option key={a} value={a}>{label(a)}</option>)}
        </select>
      </div>

      {error && <div className="alert-ems alert-error">{error}</div>}

      <div className="ems-card" style={{ padding: 0 }}>
        <div className="ems-table-wrap" style={{ borderRadius: 0, border: "none" }}>
          <table className="ems-table">
            <thead>
              <tr><th>When</th><th>Who</th><th>Action</th><th>Details</th><th>IP</th></tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={5}>Loading...</td></tr>}
              {!loading && data && data.content.length === 0 && <tr><td colSpan={5}>No entries yet.</td></tr>}
              {!loading && data?.content.map(e => (
                <tr key={e.id}>
                  <td>{new Date(e.createdAt).toLocaleString()}</td>
                  <td className="td-primary">{e.actorName || "System"}</td>
                  <td><span className={badgeClass(e.action)}>{label(e.action)}</span></td>
                  <td>{e.details || "—"}</td>
                  <td>{e.ipAddress || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {data && data.totalPages > 1 && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 12, marginTop: 16, fontSize: 13 }}>
          <button className="btn-ems" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
          <span>Page {data.page + 1} of {data.totalPages} · {data.totalElements} entries</span>
          <button className="btn-ems" disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}

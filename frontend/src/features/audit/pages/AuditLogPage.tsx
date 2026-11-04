import { useCallback, useEffect, useState } from "react";
import { fetchAuditActions, fetchAuditLog } from "../services/auditService";
import type { AuditPage } from "../../../shared/types/models";
import { Badge, Card, EmptyState, ErrorState, PageHeader, Pagination, Select, SkeletonList, type Tone } from "../../../shared/ui";

const PAGE_SIZE = 25;
const label = (a: string) => { const t = a.toLowerCase().replace(/_/g, " "); return t.charAt(0).toUpperCase() + t.slice(1); };
const tone = (a: string): Tone =>
  a.endsWith("FAILED") || a.endsWith("DELETED") || a.endsWith("REVOKED") || a.endsWith("FROZEN") ? "danger" : a.startsWith("LOGIN") ? "neutral" : "info";

export default function AuditLogPage() {
  const [data, setData] = useState<AuditPage | null>(null);
  const [actions, setActions] = useState<string[]>([]);
  const [action, setAction] = useState("");
  const [page, setPage] = useState(0);
  const [failed, setFailed] = useState(false);

  useEffect(() => { fetchAuditActions().then(setActions).catch(() => {}); }, []);

  const load = useCallback(() => {
    setFailed(false); setData(null);
    fetchAuditLog(page, PAGE_SIZE, action || undefined).then(setData).catch(() => setFailed(true));
  }, [page, action]);
  useEffect(load, [load]);

  return (
    <div>
      <PageHeader title="Audit log" subtitle="Who did what in your company. Entries cannot be edited or deleted."
        actions={<Select aria-label="Filter by action" value={action} onChange={e => { setAction(e.target.value); setPage(0); }}>
          <option value="">All actions</option>
          {actions.map(a => <option key={a} value={a}>{label(a)}</option>)}
        </Select>} />
      {failed ? <ErrorState message="We couldn't load the audit log." onRetry={load} />
        : !data ? <SkeletonList rows={5} />
        : data.content.length === 0 ? <EmptyState title="No entries yet" text="Activity in your company will be recorded here." />
        : (
          <>
            <Card pad={false}>
              <div className="tn-table-wrap">
                <table className="tn-table tn-table-cards">
                  <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Details</th><th>IP</th></tr></thead>
                  <tbody>
                    {data.content.map(e => (
                      <tr key={e.id}>
                        <td data-label="When">{new Date(e.createdAt).toLocaleString()}</td>
                        <td data-label="Who"><b>{e.actorName || "System"}</b></td>
                        <td data-label="Action"><Badge tone={tone(e.action)}>{label(e.action)}</Badge></td>
                        <td data-label="Details">{e.details || "–"}</td>
                        <td data-label="IP">{e.ipAddress || "–"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
            <div style={{ marginTop: 12 }}>
              <Pagination page={page} pageCount={data.totalPages} total={data.totalElements} label="entries" onPage={setPage} />
            </div>
          </>
        )}
    </div>
  );
}

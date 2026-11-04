import { useCallback, useEffect, useMemo, useState } from "react";
import { LuCheck, LuInbox, LuX } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import type { Leave, LeaveStatus } from "../../../shared/types/models";
import { Avatar, Badge, Button, Card, EmptyState, ErrorState, PageHeader, Select, SkeletonList, Tabs, useConfirm, useToast } from "../../../shared/ui";
import { LEAVE_LABEL, LEAVE_TONE, leaveDays, prettyDay } from "../components/leaveUi";

type Filter = LeaveStatus | "ALL";
const PERIODS = [{ label: "All time", days: 0 }, { label: "Last 7 days", days: 7 }, { label: "Last 30 days", days: 30 }, { label: "Last 90 days", days: 90 }, { label: "Last 6 months", days: 180 }];

export default function LeaveManagement() {
  const toast = useToast();
  const confirmAction = useConfirm();
  const [leaves, setLeaves] = useState<Leave[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [filter, setFilter] = useState<Filter>("PENDING");
  const [period, setPeriod] = useState(0);
  const [busy, setBusy] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setFailed(false);
    try { setLeaves(((await axiosInstance.get<Leave[]>("/api/leaves")).data || []).sort((a, b) => b.id - a.id)); }
    catch { setFailed(true); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const decide = async (l: Leave, status: "APPROVED" | "REJECTED") => {
    const who = l.employeeName || `employee ${l.empId}`;
    if (!(await confirmAction({ title: `${status === "APPROVED" ? "Approve" : "Reject"} ${who}'s leave?`, message: `${prettyDay(l.startDate)} to ${prettyDay(l.endDate)}. They will be notified.`,
      confirmLabel: status === "APPROVED" ? "Approve" : "Reject", tone: status === "APPROVED" ? "primary" : "danger" }))) return;
    setBusy(l.id);
    try {
      const res = await axiosInstance.put<Leave>(`/api/leaves/${l.id}`, { status });
      setLeaves(prev => prev.map(x => x.id === l.id ? { ...x, status: res.data.status } : x));
      toast.success(status === "APPROVED" ? "Leave approved." : "Leave rejected.");
    } catch (e) { toast.fail(e, "We couldn't save that decision. Please try again."); }
    finally { setBusy(null); }
  };

  const counts = useMemo(() => ({
    ALL: leaves.length,
    PENDING: leaves.filter(l => l.status === "PENDING").length,
    APPROVED: leaves.filter(l => l.status === "APPROVED").length,
    REJECTED: leaves.filter(l => l.status === "REJECTED").length,
  }), [leaves]);

  const shown = useMemo(() => {
    const cutoff = period ? Date.now() - period * 86_400_000 : 0;
    return leaves.filter(l => (filter === "ALL" || l.status === filter) && (!cutoff || new Date(l.startDate).getTime() >= cutoff));
  }, [leaves, filter, period]);

  return (
    <div>
      <PageHeader title="Leave requests" subtitle={`${counts.PENDING} waiting for a decision · ${counts.ALL} in total`}
        actions={<Select aria-label="Period" value={period} onChange={e => setPeriod(Number(e.target.value))} style={{ width: "auto" }}>{PERIODS.map(p => <option key={p.days} value={p.days}>{p.label}</option>)}</Select>} />
      <Tabs label="Filter by status" value={filter} onChange={setFilter}
         tabs={(["PENDING", "APPROVED", "REJECTED", "ALL"] as Filter[]).map(f => ({ id: f, label: f === "ALL" ? "All" : LEAVE_LABEL[f], count: counts[f] }))} />
      <Card pad={false}>
        {loading ? <div style={{ padding: 20 }}><SkeletonList rows={5} /></div>
          : failed ? <ErrorState message="Leave requests couldn't be loaded." onRetry={() => void load()} />
          : shown.length === 0 ? <EmptyState icon={<LuInbox size={24} />} title={filter === "PENDING" ? "Nothing waiting for a decision" : "No requests here"} text="Change the filter or period to see other requests." />
          : <div className="tn-table-wrap"><table className="tn-table tn-table-cards">
              <thead><tr><th>Employee</th><th>Dates</th><th>Days</th><th>Reason</th><th>Status</th><th style={{ width: 190 }}><span className="sr-only">Actions</span></th></tr></thead>
              <tbody>{shown.map(l => (
                <tr key={l.id}>
                  <td><div style={{ display: "flex", gap: 10, alignItems: "center" }}><Avatar name={l.employeeName || String(l.empId)} /><div><div className="strong">{l.employeeName || `Employee ${l.empId}`}</div><div style={{ fontSize: 12, color: "var(--text-3)" }}>ID {l.empId}</div></div></div></td>
                  <td data-label="Dates">{prettyDay(l.startDate)} to {prettyDay(l.endDate)}</td>
                  <td data-label="Days">{leaveDays(l.startDate, l.endDate)}</td>
                  <td data-label="Reason" style={{ maxWidth: 260, overflowWrap: "anywhere" }}>{l.reason || "–"}</td>
                  <td data-label="Status"><Badge tone={LEAVE_TONE[l.status]} dot>{LEAVE_LABEL[l.status]}</Badge></td>
                  <td>{l.status === "PENDING" && <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                    <Button size="sm" variant="primary" icon={<LuCheck size={14} />} loading={busy === l.id} onClick={() => void decide(l, "APPROVED")}>Approve</Button>
                    <Button size="sm" icon={<LuX size={14} />} disabled={busy === l.id} onClick={() => void decide(l, "REJECTED")}>Reject</Button>
                  </div>}</td>
                </tr>))}
              </tbody></table></div>}
      </Card>
    </div>
  );
}

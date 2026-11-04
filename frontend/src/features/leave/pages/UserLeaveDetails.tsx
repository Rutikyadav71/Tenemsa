import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { LuArrowLeft, LuCalendarX2 } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import type { Leave } from "../../../shared/types/models";
import { Badge, Button, Card, EmptyState, ErrorState, PageHeader, SkeletonList } from "../../../shared/ui";
import { LEAVE_LABEL, LEAVE_TONE, leaveDays, prettyDay } from "../components/leaveUi";

export default function UserLeaveDetails() {
  const { empId } = useParams();
  const { search, pathname } = useLocation();
  const navigate = useNavigate();
  const base = `/${pathname.split("/")[1]}`;
  const leaveId = new URLSearchParams(search).get("leaveId");
  const [leaves, setLeaves] = useState<Leave[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!empId) return;
    let live = true;
    axiosInstance.get<Leave[]>(`/api/leaves/employee/${empId}`)
      .then(r => { if (live) setLeaves((r.data || []).filter(l => !leaveId || String(l.id) === leaveId).sort((a, b) => b.startDate.localeCompare(a.startDate))); })
      .catch(() => { if (live) setFailed(true); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [empId, leaveId, attempt]);

  return (
    <div style={{ maxWidth: 860 }}>
      <PageHeader title={leaveId ? "Leave request" : "My leave"} subtitle={leaveId ? "The request you were notified about." : "Every leave request you have sent."}
        actions={<Button icon={<LuArrowLeft size={16} />} onClick={() => navigate(`${base}/notifications`)}>Back to notifications</Button>} />
      {loading ? <SkeletonList rows={3} />
        : failed ? <Card><ErrorState message="Your leave records couldn't be loaded." onRetry={() => { setFailed(false); setLoading(true); setAttempt(a => a + 1); }} /></Card>
        : leaves.length === 0 ? <Card><EmptyState icon={<LuCalendarX2 size={24} />} title="No leave records found" text="This request may have been removed." /></Card>
        : <div style={{ display: "grid", gap: 12 }}>{leaves.map(l => (
            <Card key={l.id}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start", marginBottom: 12 }}>
                <div><div style={{ fontWeight: 600, color: "var(--text-1)" }}>{prettyDay(l.startDate)} to {prettyDay(l.endDate)}</div>
                  <div style={{ fontSize: 12.5, color: "var(--text-3)" }}>{leaveDays(l.startDate, l.endDate)} day{leaveDays(l.startDate, l.endDate) === 1 ? "" : "s"}</div></div>
                <Badge tone={LEAVE_TONE[l.status]} dot>{LEAVE_LABEL[l.status]}</Badge>
              </div>
              {l.reason && <p style={{ margin: 0, fontSize: 13.5, color: "var(--text-2)", overflowWrap: "anywhere" }}>{l.reason}</p>}
            </Card>))}</div>}
    </div>
  );
}

import { useEffect, useState } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import type { Leave, LeaveStatus } from "../../../shared/types/models";

const BackIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M19 12H5M12 19l-7-7 7-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;

const statusBadge = (status: LeaveStatus) => {
  const map: Record<LeaveStatus, string> = { PENDING: "badge-amber", APPROVED: "badge-green", REJECTED: "badge-red" };
  const dot: Record<LeaveStatus, string> = { PENDING: "#f59e0b", APPROVED: "#22c55e", REJECTED: "#ef4444" };
  return <span className={`badge ${map[status] || "badge-muted"}`}><span className="badge-dot" style={{ background: dot[status] || "var(--text-3)" }} />{status}</span>;
};

export default function UserLeaveDetails() {
  const { empId }   = useParams();
  const location    = useLocation();
  const navigate    = useNavigate();
  const leaveId     = new URLSearchParams(location.search).get("leaveId");

  const [leaves,  setLeaves]  = useState<Leave[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!empId) return;
    axiosInstance.get<Leave[]>(`/api/leaves/employee/${empId}`)
      .then(r => {
        let data = r.data || [];
        if (leaveId) data = data.filter(l => String(l.id) === String(leaveId));
        setLeaves(data);
      })
      .catch(e => console.error(e))
      .finally(() => setLoading(false));
  }, [empId, leaveId]);

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Leave Details</div>
          <div className="page-sub">Employee #{empId} · {leaveId ? `Leave #${leaveId}` : "All records"}</div>
        </div>
        <button className="btn-ems btn-secondary-ems" onClick={() => navigate("/user/notifications")}>
          <BackIcon /> Back to Notifications
        </button>
      </div>

      {loading ? (
        <div className="leave-grid">
          {Array(3).fill(0).map((_, i) => (
            <div key={i} className="skeleton-card">
              <div className="skeleton" style={{ width: "50%", height: 14, borderRadius: 4, marginBottom: 10 }} />
              {[100, 80, 90].map((w, j) => <div key={j} className="skeleton" style={{ width: w, height: 12, borderRadius: 4, marginBottom: 8 }} />)}
            </div>
          ))}
        </div>
      ) : leaves.length === 0 ? (
        <div className="ems-card" style={{ textAlign: "center", padding: "48px 20px" }}>
          <div style={{ fontSize: 14, color: "var(--text-3)" }}>No leave records found</div>
          <button className="btn-ems btn-secondary-ems" style={{ marginTop: 16 }} onClick={() => navigate("/user/notifications")}>
            <BackIcon /> Go Back
          </button>
        </div>
      ) : (
        <div className="leave-grid">
          {leaves.map(l => (
            <div key={l.id} className="leave-card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-1)" }}>Leave #{l.id}</div>
                  <div style={{ fontSize: 11, color: "var(--text-3)", marginTop: 2 }}>Employee #{l.empId}</div>
                </div>
                {statusBadge(l.status)}
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 12 }}>
                {[
                  { label: "Start Date", value: l.startDate },
                  { label: "End Date",   value: l.endDate },
                  ...(l.reason ? [{ label: "Reason", value: l.reason }] : []),
                ].map(({ label, value }) => (
                  <div key={label} style={{ display: "flex", gap: 8 }}>
                    <span style={{ fontSize: 11, color: "var(--text-3)", minWidth: 72, fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.3px" }}>{label}</span>
                    <span style={{ fontSize: 13, color: "var(--text-2)" }}>{value}</span>
                  </div>
                ))}
              </div>

              <div style={{ paddingTop: 10, borderTop: "1px solid var(--border)" }}>
                <div style={{ fontSize: 11, color: "var(--text-3)" }}>
                  Duration: {l.startDate && l.endDate
                    ? Math.max(1, Math.round((new Date(l.endDate).getTime() - new Date(l.startDate).getTime()) / 86400000) + 1) + " day(s)"
                    : "—"}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

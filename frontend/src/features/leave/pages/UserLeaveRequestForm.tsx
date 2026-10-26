import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { useNavigate } from "react-router-dom";
import type { Leave, LeaveStatus } from "../../../shared/types/models";

export default function UserLeaveRequestForm() {
  const empId   = localStorage.getItem("empId");
  const navigate = useNavigate();
  const today   = new Date().toISOString().split("T")[0];

  const [form,    setForm]    = useState({ empId: empId || "", startDate: "", endDate: "", reason: "" });
  const [leaves,  setLeaves]  = useState<Leave[]>([]);
  const [message, setMessage] = useState("");
  const [msgType, setMsgType] = useState("success");
  const [loading, setLoading] = useState(false);
  const [fetchingHistory, setFetchingHistory] = useState(true);

  useEffect(() => {
    if (empId) {
      axiosInstance.get(`/api/leaves/employee/${empId}`)
        .then(r => setLeaves(r.data || []))
        .catch(() => {})
        .finally(() => setFetchingHistory(false));
    } else {
      setFetchingHistory(false);
    }
  }, [empId]);

  const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    if (form.startDate > form.endDate) { setMessage("End date must be after start date."); setMsgType("error"); return; }
    setLoading(true);
    try {
      await axiosInstance.post("/api/leaves", form);
      setMessage("Leave submitted! Awaiting admin approval."); setMsgType("success");
      setForm({ ...form, startDate: "", endDate: "", reason: "" });
      const r = await axiosInstance.get<Leave[]>(`/api/leaves/employee/${empId}`);
      setLeaves(r.data || []);
    } catch(err: any) {
      setMessage(err.response?.data?.message || "Failed to submit leave."); setMsgType("error");
    } finally { setLoading(false); }
  };

  const statusBadge = (status: LeaveStatus) => {
    const map: Record<LeaveStatus, string> = { PENDING: "badge-amber", APPROVED: "badge-green", REJECTED: "badge-red" };
    const dot: Record<LeaveStatus, string> = { PENDING: "#f59e0b", APPROVED: "#22c55e", REJECTED: "#ef4444" };
    return <span className={`badge ${map[status] || "badge-muted"}`}><span className="badge-dot" style={{ background: dot[status] || "var(--text-3)" }} />{status}</span>;
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Apply Leave</div>
          <div className="page-sub">Submit a leave request to your manager</div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1.2fr", gap: 16, alignItems: "start" }}>
        {/* Form */}
        <div className="ems-card">
          <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-1)", marginBottom: 16 }}>New Request</div>

          {message && <div className={`alert-ems alert-${msgType}`}>{message}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">Employee ID</label>
              <input name="empId" value={form.empId} className="form-control-ems" readOnly />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
              <div className="form-group">
                <label className="form-label">Start Date</label>
                <input name="startDate" type="date" value={form.startDate} onChange={handleChange}
                  className="form-control-ems" min={today} required />
              </div>
              <div className="form-group">
                <label className="form-label">End Date</label>
                <input name="endDate" type="date" value={form.endDate} onChange={handleChange}
                  className="form-control-ems" min={form.startDate || today} required />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Reason for Leave</label>
              <textarea name="reason" value={form.reason} onChange={handleChange}
                className="form-control-ems" rows={4} placeholder="Describe your reason..."
                style={{ resize: "vertical" }} required />
            </div>

            <button type="submit" className="btn-ems btn-primary-ems" disabled={loading} style={{ width: "100%", justifyContent: "center" }}>
              {loading ? "Submitting..." : "Submit Request"}
            </button>
          </form>
        </div>

        {/* History */}
        <div className="ems-card" style={{ padding: 0 }}>
          <div style={{ padding: "16px 20px", borderBottom: "1px solid var(--border)", fontSize: 14, fontWeight: 600, color: "var(--text-1)" }}>
            Leave History
          </div>
          <div style={{ maxHeight: 420, overflowY: "auto" }}>
            {fetchingHistory ? (
              Array(4).fill(0).map((_, i) => (
                <div key={i} style={{ padding: "14px 20px", borderBottom: "1px solid var(--border)" }}>
                  <div className="skeleton" style={{ width: "60%", height: 13, borderRadius: 4, marginBottom: 6 }} />
                  <div className="skeleton" style={{ width: "40%", height: 11, borderRadius: 4 }} />
                </div>
              ))
            ) : leaves.length === 0 ? (
              <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--text-3)", fontSize: 13 }}>No leave history yet</div>
            ) : (
              leaves.map((l, i) => (
                <div key={l.id || i} style={{
                  padding: "14px 20px",
                  borderBottom: i < leaves.length - 1 ? "1px solid var(--border)" : "none",
                  display: "flex", alignItems: "flex-start", gap: 10
                }}>
                  <div style={{
                    width: 8, height: 8, borderRadius: "50%", marginTop: 5, flexShrink: 0,
                    background: l.status === "APPROVED" ? "var(--green)" : l.status === "REJECTED" ? "var(--red)" : "var(--amber)"
                  }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 13, color: "var(--text-1)", fontWeight: 500 }}>
                      {l.startDate} — {l.endDate}
                    </div>
                    {l.reason && (
                      <div style={{ fontSize: 11.5, color: "var(--text-3)", marginTop: 2 }}>
                        {l.reason.length > 50 ? l.reason.slice(0, 50) + "…" : l.reason}
                      </div>
                    )}
                  </div>
                  {statusBadge(l.status)}
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

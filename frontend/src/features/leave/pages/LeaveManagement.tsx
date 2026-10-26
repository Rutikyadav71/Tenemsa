import { useEffect, useState } from "react";
import axiosInstance from "../services/axiosInstance";
import { getLeavePrediction } from "../services/EmployeeService";
import type { Leave, LeaveStatus } from "../types/models";

const CalIcon   = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const UserIcon  = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/></svg>;
const CheckIcon = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const XIcon     = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>;
const BrainIcon = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.6"/><path d="M12 8v4l3 3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const TrashIcon = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M19 6l-1 14H6L5 6M10 11v6M14 11v6M9 6V4h6v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const FilterIcon= () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;

const statusBadge = (status: LeaveStatus) => {
  const m: Record<LeaveStatus, {cls:string; dot:string}> = { PENDING:{cls:"badge-amber",dot:"#f59e0b"}, APPROVED:{cls:"badge-green",dot:"#22c55e"}, REJECTED:{cls:"badge-red",dot:"#ef4444"} };
  const s = m[status] || m.PENDING;
  return <span className={`badge ${s.cls}`}><span className="badge-dot" style={{background:s.dot}}/>{status}</span>;
};

const CardSkeleton = () => (
  <div className="skeleton-card">
    <div style={{display:"flex",justifyContent:"space-between",marginBottom:14}}>
      <div className="skeleton" style={{width:"40%",height:14,borderRadius:4}}/>
      <div className="skeleton" style={{width:64,height:20,borderRadius:99}}/>
    </div>
    {[100,80,90].map((w,i)=><div key={i} className="skeleton" style={{width:w,height:12,borderRadius:4,marginBottom:8}}/>)}
    <div style={{display:"flex",gap:8,marginTop:16}}>
      <div className="skeleton" style={{flex:1,height:32,borderRadius:8}}/>
      <div className="skeleton" style={{flex:1,height:32,borderRadius:8}}/>
    </div>
  </div>
);

// Days filter options
const DAY_FILTERS = [
  { label:"All Time",   days: 0   },
  { label:"Last 7d",    days: 7   },
  { label:"Last 30d",   days: 30  },
  { label:"Last 90d",   days: 90  },
  { label:"Last 6mo",   days: 180 },
];

export default function LeaveManagement() {
  const [leaves,     setLeaves]     = useState<Leave[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [statusFilter, setStatusFilter] = useState<LeaveStatus | "ALL">("ALL");
  const [dayFilter,  setDayFilter]  = useState(0);  // 0 = all time
  const [showModal,  setShowModal]  = useState(false);
  const [mlLoading,  setMlLoading]  = useState(false);
  const [mlData,     setMlData]     = useState<(Record<string, any> & { error?: string }) | null>(null);

  useEffect(() => { fetchLeaves(); }, []);

  const fetchLeaves = async () => {
    setLoading(true);
    try {
      const res = await axiosInstance.get<Leave[]>("/api/leaves");
      // Sort newest first by id (higher id = newer)
      const sorted = (res.data || []).sort((a, b) => b.id - a.id);
      setLeaves(sorted);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  };

  const updateStatus = async (id: number, status: LeaveStatus) => {
    try {
      const res = await axiosInstance.put<Leave>(`/api/leaves/${id}`, { status });
      setLeaves(prev => prev.map(l => l.id === id ? { ...l, status: res.data.status } : l));
    } catch (e) { console.error(e); }
  };

  const clearFromUI = (id: number) => setLeaves(prev => prev.filter(l => l.id !== id));

  const getPrediction = async (leaveId: number) => {
    setShowModal(true); setMlLoading(true); setMlData(null);
    try {
      const res = await getLeavePrediction(leaveId);
      setMlData(res.data as Record<string, any>);
    } catch { setMlData({ error: "ML service unavailable." }); }
    finally { setMlLoading(false); }
  };

  // Apply filters
  const now = new Date();
  const displayed = leaves
    .filter(l => statusFilter === "ALL" || l.status === statusFilter)
    .filter(l => {
      if (dayFilter === 0) return true;
      const cutoff = new Date(now.getTime() - dayFilter * 86400000);
      // Use id as proxy for creation time if no createdAt, else use startDate
      const refDate = l.startDate ? new Date(l.startDate) : new Date();
      return refDate >= cutoff;
    });

  const counts = {
    total:    leaves.length,
    pending:  leaves.filter(l => l.status === "PENDING").length,
    approved: leaves.filter(l => l.status === "APPROVED").length,
    rejected: leaves.filter(l => l.status === "REJECTED").length,
  };

  return (
    <div className="fade-in">
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="page-title">Leave Management</div>
          <div className="page-sub">All leave requests — {counts.total} total</div>
        </div>
      </div>

      {/* Filters row */}
      <div style={{ display:"flex", gap:8, marginBottom:16, flexWrap:"wrap", alignItems:"center" }}>
        {/* Status filters */}
        <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
          {(["ALL","PENDING","APPROVED","REJECTED"] as (LeaveStatus | "ALL")[]).map(f => (
            <button key={f}
              className={`btn-ems ${statusFilter===f ? "btn-primary-ems" : "btn-secondary-ems"}`}
              style={{ padding:"6px 12px", fontSize:12 }}
              onClick={() => setStatusFilter(f)}>
              {f}
            </button>
          ))}
        </div>

        {/* Divider */}
        <div style={{ width:1, height:24, background:"var(--border)", margin:"0 4px" }}/>

        {/* Day range filters */}
        <div style={{ display:"flex", alignItems:"center", gap:6, flexWrap:"wrap" }}>
          <span style={{ fontSize:11, color:"var(--text-3)", display:"flex", alignItems:"center", gap:4 }}>
            <FilterIcon/> Period:
          </span>
          {DAY_FILTERS.map(f => (
            <button key={f.days}
              className={`btn-ems ${dayFilter===f.days ? "btn-primary-ems" : "btn-secondary-ems"}`}
              style={{ padding:"5px 10px", fontSize:11 }}
              onClick={() => setDayFilter(f.days)}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {/* Summary cards */}
      <div style={{ display:"flex", gap:10, marginBottom:18, flexWrap:"wrap" }}>
        {[
          { label:"Total",    count:counts.total,    color:"var(--text-2)" },
          { label:"Pending",  count:counts.pending,  color:"var(--amber)" },
          { label:"Approved", count:counts.approved, color:"var(--green)" },
          { label:"Rejected", count:counts.rejected, color:"var(--red)" },
        ].map((s, i) => (
          <div key={i} className="ems-card" style={{ padding:"10px 16px", flex:"1", minWidth:90 }}>
            <div style={{ fontSize:10, color:"var(--text-3)", textTransform:"uppercase", letterSpacing:"0.5px", marginBottom:3 }}>{s.label}</div>
            <div style={{ fontSize:20, fontWeight:700, fontFamily:"'Outfit',sans-serif", color:s.color }}>{s.count}</div>
          </div>
        ))}
      </div>

      {/* Shown count */}
      {displayed.length !== leaves.length && (
        <div style={{ fontSize:12, color:"var(--text-3)", marginBottom:12 }}>
          Showing {displayed.length} of {leaves.length} leaves
        </div>
      )}

      {/* Cards grid */}
      <div className="leave-grid">
        {loading ? Array(6).fill(0).map((_,i) => <CardSkeleton key={i}/>) :
          displayed.length === 0 ? (
            <div style={{ gridColumn:"1/-1", textAlign:"center", padding:"48px 0", color:"var(--text-3)", fontSize:14 }}>
              No leave requests found for the selected filters
            </div>
          ) :
          displayed.map(leave => (
            <div key={leave.id} className="leave-card">
              <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-start", marginBottom:12 }}>
                <div>
                  <div style={{ fontSize:13, fontWeight:600, color:"var(--text-1)" }}>
                    {leave.employeeName || `Employee #${leave.empId}`}
                  </div>
                  <div style={{ fontSize:11, color:"var(--text-3)", marginTop:2, display:"flex", alignItems:"center", gap:4 }}>
                    <UserIcon/> ID: {leave.empId} &nbsp;·&nbsp; Leave #{leave.id}
                  </div>
                </div>
                {statusBadge(leave.status)}
              </div>

              <div style={{ display:"flex", flexDirection:"column", gap:6, marginBottom:14 }}>
                <div style={{ display:"flex", alignItems:"center", gap:6, fontSize:12.5, color:"var(--text-2)" }}>
                  <CalIcon/>
                  <span style={{ color:"var(--text-3)" }}>From:</span>
                  <span>{leave.startDate}</span>
                  <span style={{ color:"var(--text-3)" }}>—</span>
                  <span>{leave.endDate}</span>
                </div>
                {leave.reason && (
                  <div style={{ fontSize:12, color:"var(--text-3)", padding:"6px 10px",
                    background:"var(--bg-elevated)", borderRadius:6,
                    borderLeft:"2px solid var(--border-bright)" }}>
                    {leave.reason}
                  </div>
                )}
              </div>

              <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
                {leave.status === "PENDING" && (
                  <>
                    <button className="btn-ems btn-success-ems2"
                      style={{ flex:1, fontSize:12, padding:"7px 10px" }}
                      onClick={() => updateStatus(leave.id, "APPROVED")}>
                      <CheckIcon/> Approve
                    </button>
                    <button className="btn-ems btn-danger-ems"
                      style={{ flex:1, fontSize:12, padding:"7px 10px" }}
                      onClick={() => updateStatus(leave.id, "REJECTED")}>
                      <XIcon/> Reject
                    </button>
                  </>
                )}
                <button className="btn-ems btn-secondary-ems"
                  style={{ fontSize:12, padding:"7px 10px" }}
                  onClick={() => getPrediction(leave.id)}>
                  <BrainIcon/> AI
                </button>
                {leave.status !== "PENDING" && (
                  <button className="btn-icon danger" style={{ marginLeft:"auto" }}
                    onClick={() => clearFromUI(leave.id)}>
                    <TrashIcon/>
                  </button>
                )}
              </div>
            </div>
          ))
        }
      </div>

      {/* ML Modal */}
      {showModal && (
        <div className="ems-modal-backdrop" onClick={() => setShowModal(false)}>
          <div className="ems-modal" onClick={e => e.stopPropagation()}>
            <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:16 }}>
              <div>
                <div className="modal-title">AI Leave Analysis</div>
                <div className="modal-sub">ML-powered prediction</div>
              </div>
              <button className="btn-icon" onClick={() => setShowModal(false)}><XIcon/></button>
            </div>
            {mlLoading ? (
              <>
                <div className="skeleton" style={{ width:"100%", height:60, borderRadius:8, marginBottom:12 }}/>
                <div className="skeleton" style={{ width:"80%", height:14, borderRadius:4, marginBottom:8 }}/>
                <div className="skeleton" style={{ width:"60%", height:14, borderRadius:4 }}/>
              </>
            ) : mlData?.error ? (
              <div className="alert-ems alert-error">{mlData.error}</div>
            ) : mlData ? (
              <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
                {Object.entries(mlData).map(([k, v]) => (
                  <div key={k} style={{ display:"flex", justifyContent:"space-between",
                    padding:"10px 14px", background:"var(--bg-surface)",
                    borderRadius:8, border:"1px solid var(--border)" }}>
                    <span style={{ fontSize:13, color:"var(--text-3)", textTransform:"capitalize" }}>
                      {k.replace(/_/g, " ")}
                    </span>
                    <span style={{ fontSize:13, fontWeight:600, color:"var(--text-1)" }}>{String(v)}</span>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        </div>
      )}
    </div>
  );
}

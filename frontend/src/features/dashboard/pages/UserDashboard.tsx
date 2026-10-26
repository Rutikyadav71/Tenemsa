import { useEffect, useState } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { Link } from "react-router-dom";
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";
import type { TooltipContentProps } from "recharts";
import type { Leave, LeaveStatus, User } from "../../../shared/types/models";

const avatarColors = ["#6366f1","#2dd4bf","#22c55e","#f59e0b","#a78bfa"];

const CustomTooltip = ({ active, payload, label }: TooltipContentProps<number, string>) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: "var(--bg-elevated)", border: "1px solid var(--border-bright)", borderRadius: 8, padding: "10px 14px" }}>
      <div style={{ fontSize: 11, color: "var(--text-3)", marginBottom: 4 }}>{label}</div>
      {payload.map((p, i) => <div key={i} style={{ fontSize: 13, fontWeight: 600, color: p.color }}>{p.name}: {p.value}</div>)}
    </div>
  );
};

const statusBadge = (status: LeaveStatus) => {
  const map: Record<LeaveStatus, string> = { PENDING: "badge-amber", APPROVED: "badge-green", REJECTED: "badge-red" };
  const dotColor: Record<LeaveStatus, string> = { PENDING: "#f59e0b", APPROVED: "#22c55e", REJECTED: "#ef4444" };
  return (
    <span className={`badge ${map[status] || "badge-muted"}`}>
      <span className="badge-dot" style={{ background: dotColor[status] || "var(--text-3)" }} />
      {status}
    </span>
  );
};

const StatSkeleton = () => (
  <div className="skeleton-card">
    <div className="skeleton" style={{ width: 38, height: 38, borderRadius: 9, marginBottom: 14 }} />
    <div className="skeleton" style={{ width: "50%", height: 11, marginBottom: 6 }} />
    <div className="skeleton" style={{ width: "60%", height: 24 }} />
  </div>
);

const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
const buildMonthly = (leaves: Leave[]) => {
  const now = new Date();
  return Array.from({ length: 6 }, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - 5 + i, 1);
    const lbl = months[d.getMonth()];
    const count = leaves.filter(l => {
      const ld = new Date(l.startDate);
      return ld.getMonth() === d.getMonth() && ld.getFullYear() === d.getFullYear();
    }).length;
    return { month: lbl, leaves: count };
  });
};

const DONUT_COLORS = ["#f59e0b", "#22c55e", "#ef4444"];

export default function UserDashboard() {
  const empId = localStorage.getItem("empId");
  const name  = localStorage.getItem("name") || "Employee";
  const hours = new Date().getHours();
  const greeting = hours < 12 ? "Good Morning" : hours < 17 ? "Good Afternoon" : "Good Evening";
  const formattedDate = new Date().toLocaleDateString("en-IN", { weekday: "long", year: "numeric", month: "long", day: "numeric" });

  const [profile,      setProfile]      = useState<User | null>(null);
  const [leaveHistory, setLeaveHistory] = useState<Leave[]>([]);
  const [loading,      setLoading]      = useState(true);

  useEffect(() => {
    Promise.all([
      axiosInstance.get<User | null>(`/api/employees/${empId}`).catch(() => ({ data: null })),
      axiosInstance.get<Leave[]>(`/api/leaves/employee/${empId}`).catch(() => ({ data: [] as Leave[] })),
    ]).then(([p, l]) => {
      setProfile(p.data);
      setLeaveHistory(l.data || []);
    }).finally(() => setLoading(false));
  }, [empId]);

  const pending  = leaveHistory.filter(l => l.status === "PENDING").length;
  const approved = leaveHistory.filter(l => l.status === "APPROVED").length;
  const rejected = leaveHistory.filter(l => l.status === "REJECTED").length;
  const monthlyData = buildMonthly(leaveHistory);
  const donutData   = [{ name: "Pending", value: pending || 0 }, { name: "Approved", value: approved || 0 }, { name: "Rejected", value: rejected || 0 }];

  const stats = [
    { label: "Total Leaves", value: leaveHistory.length, color: "blue",  icon: <svg width="18" height="18" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg> },
    { label: "Pending",      value: pending,              color: "amber", icon: <svg width="18" height="18" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8"/><path d="M12 7v5l3 3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg> },
    { label: "Approved",     value: approved,             color: "green", icon: <svg width="18" height="18" fill="none" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><polyline points="22 4 12 14.01 9 11.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg> },
    { label: "Rejected",     value: rejected,             color: "red",   icon: <svg width="18" height="18" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8"/><path d="M15 9l-6 6M9 9l6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg> },
  ];

  return (
    <div className="fade-in">
      {/* Header */}
      <div className="flex-between mb-24" style={{ flexWrap: "wrap", gap: 12 }}>
        <div>
          <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 4, textTransform: "uppercase", letterSpacing: "0.5px", fontWeight: 500 }}>{formattedDate}</div>
          <h2 style={{ fontSize: 22, fontWeight: 700, fontFamily: "'Outfit',sans-serif", letterSpacing: "-0.3px" }}>
            {greeting}, {name}
          </h2>
          <div style={{ fontSize: 13, color: "var(--text-3)", marginTop: 2 }}>Here's your leave & profile overview.</div>
        </div>
        <Link to="/user/apply-leave">
          <button className="btn-ems btn-primary-ems">
            <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
            Apply Leave
          </button>
        </Link>
      </div>

      {/* Stat cards */}
      <div className="stat-grid">
        {loading ? Array(4).fill(0).map((_, i) => <StatSkeleton key={i} />) :
          stats.map((s, i) => (
            <div className={`stat-card ${s.color}`} key={i}>
              <div className={`stat-icon ${s.color}`}>{s.icon}</div>
              <div className="stat-label">{s.label}</div>
              <div className="stat-value font-outfit">{s.value}</div>
            </div>
          ))
        }
      </div>

      {/* Charts row */}
      <div className="charts-grid">
        {/* Area chart */}
        <div className="chart-card">
          <div className="chart-header">
            <div>
              <div className="chart-title">Leave Activity</div>
              <div className="chart-sub">Monthly leave applications</div>
            </div>
            <span className="chart-pill">Last 6 months</span>
          </div>
          {loading ? (
            <div className="skeleton" style={{ width: "100%", height: 200, borderRadius: 8 }} />
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <AreaChart data={monthlyData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="gradU" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%"  stopColor="#6366f1" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="month" tick={{ fill: "var(--text-3)", fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: "var(--text-3)", fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={CustomTooltip} />
                <Area type="monotone" dataKey="leaves" name="Leaves" stroke="#6366f1" fill="url(#gradU)" strokeWidth={2} dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Donut */}
        <div className="chart-card">
          <div className="chart-header">
            <div>
              <div className="chart-title">Leave Status</div>
              <div className="chart-sub">Distribution by status</div>
            </div>
          </div>
          {loading ? (
            <div className="skeleton" style={{ width: "100%", height: 200, borderRadius: 8 }} />
          ) : leaveHistory.length === 0 ? (
            <div style={{ height: 200, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-3)", fontSize: 13 }}>No leave data yet</div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie data={donutData} cx="50%" cy="50%" innerRadius={55} outerRadius={80} paddingAngle={3} dataKey="value">
                  {donutData.map((_, i) => <Cell key={i} fill={DONUT_COLORS[i]} stroke="none" />)}
                </Pie>
                <Tooltip content={CustomTooltip} />
              </PieChart>
            </ResponsiveContainer>
          )}
          {!loading && (
            <div style={{ display: "flex", justifyContent: "center", gap: 12, marginTop: 4 }}>
              {donutData.map((d, i) => (
                <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11, color: "var(--text-3)" }}>
                  <span style={{ width: 8, height: 8, borderRadius: "50%", background: DONUT_COLORS[i], display: "inline-block" }} />
                  {d.name}: {d.value}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Profile card + Recent leaves */}
      <div className="bottom-grid">
        {/* Profile */}
        <div className="chart-card">
          <div className="chart-title" style={{ marginBottom: 16 }}>Profile Details</div>
          {loading ? (
            <>
              {[120, 160, 100, 140].map((w, i) => (
                <div key={i} className="skeleton" style={{ width: w, height: 13, borderRadius: 4, marginBottom: 10 }} />
              ))}
            </>
          ) : profile ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 0 }}>
              {[
                { label: "Full Name",     value: profile.name },
                { label: "Employee ID",  value: `#${profile.empId}` },
                { label: "Email",        value: profile.email },
                { label: "Department",   value: profile.department || "—" },
                { label: "Salary",       value: `₹${Number(profile.salary).toLocaleString("en-IN")}` },
              ].map(({ label, value }) => (
                <div key={label} style={{ display: "flex", justifyContent: "space-between", padding: "10px 0", borderBottom: "1px solid var(--border)" }}>
                  <span style={{ fontSize: 12, color: "var(--text-3)", fontWeight: 500 }}>{label}</span>
                  <span style={{ fontSize: 13, color: "var(--text-1)", fontWeight: 500 }}>{value}</span>
                </div>
              ))}
            </div>
          ) : (
            <div style={{ color: "var(--text-3)", fontSize: 13 }}>Profile unavailable</div>
          )}
        </div>

        {/* Recent leaves */}
        <div className="chart-card">
          <div className="flex-between" style={{ marginBottom: 14 }}>
            <div className="chart-title">Recent Leaves</div>
            {leaveHistory.length > 0 && (
              <Link to={`/user/leaves/employee/${empId}`} style={{ fontSize: 11, color: "var(--accent-light)" }}>View all</Link>
            )}
          </div>
          {loading ? (
            Array(4).fill(0).map((_, i) => (
              <div key={i} style={{ marginBottom: 10 }}>
                <div className="skeleton" style={{ width: "80%", height: 13, borderRadius: 4, marginBottom: 5 }} />
                <div className="skeleton" style={{ width: "50%", height: 11, borderRadius: 4 }} />
              </div>
            ))
          ) : leaveHistory.length === 0 ? (
            <div style={{ textAlign: "center", padding: "24px 0", color: "var(--text-3)", fontSize: 13 }}>
              No leave history yet
              <div style={{ marginTop: 12 }}>
                <Link to="/user/apply-leave">
                  <button className="btn-ems btn-primary-ems" style={{ fontSize: 12, padding: "7px 14px" }}>Apply Now</button>
                </Link>
              </div>
            </div>
          ) : (
            <div className="activity-list">
              {leaveHistory.slice(0, 5).map((l, i) => (
                <div key={i} className="activity-item">
                  <div className="activity-dot" style={{ background: l.status === "APPROVED" ? "var(--green)" : l.status === "REJECTED" ? "var(--red)" : "var(--amber)" }} />
                  <div>
                    <div style={{ fontSize: 12.5, color: "var(--text-2)", marginBottom: 3 }}>
                      {l.startDate} — {l.endDate}
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      {statusBadge(l.status)}
                      {l.reason && <span style={{ fontSize: 11, color: "var(--text-3)" }}>{l.reason.slice(0, 30)}{l.reason.length > 30 ? "…" : ""}</span>}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

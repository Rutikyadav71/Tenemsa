import { useEffect, useState } from "react";
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { can } from "../../../shared/lib/roles";
import { getMySummary, getOverview } from "../services/analyticsService";
import type { AnalyticsOverview, Count, MySummary } from "../../../shared/types/models";

const COLORS = ["#6366f1", "#2dd4bf", "#22c55e", "#f59e0b", "#ef4444", "#a78bfa", "#fb7185"];
const STATUS_COLOR: Record<string, string> = { TODO: "#a78bfa", IN_PROGRESS: "#6366f1", IN_REVIEW: "#f59e0b", DONE: "#22c55e",
  PENDING: "#f59e0b", APPROVED: "#22c55e", REJECTED: "#ef4444" };

function Tile({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="ems-card" style={{ padding: 16, minWidth: 150, flex: "1 1 150px" }}>
      <div style={{ fontSize: 12, color: "var(--text-3)" }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, color: "var(--text-1)" }}>{value}</div>
      {hint && <div style={{ fontSize: 11, color: "var(--text-3)" }}>{hint}</div>}
    </div>
  );
}

function Bars({ data, colors, height = 180 }: { data: Count[]; colors?: (label: string, i: number) => string; height?: number }) {
  if (data.length === 0 || data.every(d => d.value === 0)) return <div style={{ color: "var(--text-3)", fontSize: 13, padding: "30px 0", textAlign: "center" }}>No data yet.</div>;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 4, right: 0, left: -18, bottom: 0 }}>
        <XAxis dataKey="label" tick={{ fill: "var(--text-3)", fontSize: 11 }} axisLine={false} tickLine={false} />
        <YAxis allowDecimals={false} tick={{ fill: "var(--text-3)", fontSize: 11 }} axisLine={false} tickLine={false} />
        <Tooltip />
        <Bar dataKey="value" radius={[4, 4, 0, 0]}>
          {data.map((d, i) => <Cell key={d.label} fill={colors ? colors(d.label, i) : COLORS[i % COLORS.length]} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

const pct = (v: number | null) => (v === null ? "No data" : `${v}%`);

export default function AnalyticsPage() {
  const showOverview = can("report:view:company", "report:view:team");
  const [mine, setMine] = useState<MySummary | null>(null);
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const jobs: Promise<any>[] = [getMySummary().then(setMine).catch(() => setError("Could not load your summary."))];
    if (showOverview) jobs.push(getOverview().then(setOverview).catch(() => setError("Could not load the overview.")));
    Promise.allSettled(jobs).finally(() => setLoading(false));
  }, []);

  const monthLabel = (l: string) => new Date(l + "-01T00:00:00").toLocaleDateString("en-IN", { month: "short" });

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Insights</div>
          <div className="page-sub">{overview ? (overview.scope === "TEAM" ? "Numbers for the teams you lead, plus your own." : "Company-wide numbers, plus your own.") : "Your own numbers."}</div>
        </div>
      </div>
      {error && <div className="alert-ems alert-error">{error}</div>}
      {loading && <div style={{ color: "var(--text-3)" }}>Loading...</div>}

      {mine && (
        <>
          <div style={{ fontWeight: 600, color: "var(--text-1)", margin: "6px 0 8px" }}>My summary</div>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 20 }}>
            <Tile label="Leave taken this year" value={`${mine.leaveDaysApprovedThisYear} days`} />
            <Tile label="Requests waiting" value={mine.pendingLeaveRequests} />
            <Tile label="Attendance, last 30 days" value={pct(mine.presentRateLast30Days)} />
            <Tile label="Open tasks" value={mine.openIssues} hint={mine.overdueIssues > 0 ? `${mine.overdueIssues} overdue` : "none overdue"} />
            <Tile label="Unread notifications" value={mine.unreadNotifications} />
          </div>
        </>
      )}

      {overview && (
        <>
          <div style={{ fontWeight: 600, color: "var(--text-1)", margin: "6px 0 8px" }}>{overview.scope === "TEAM" ? "My teams" : "Company"}</div>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
            <Tile label="People" value={overview.headcount} />
            <Tile label="Leave waiting for a decision" value={overview.leave.pending} />
            <Tile label="Off today" value={overview.leave.offToday.length} hint={overview.leave.offToday.slice(0, 3).join(", ") || "nobody"} />
            <Tile label={`Attendance, last ${overview.attendance.days} days`} value={pct(overview.attendance.presentRate)}
                  hint={overview.attendance.averageWorkHours === null ? undefined : `${overview.attendance.averageWorkHours} h average day`} />
            {overview.projects && <Tile label="Overdue tasks" value={overview.projects.overdueIssues} />}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 16 }}>
            <div className="chart-card">
              <div className="chart-title">Approved leave days per month</div>
              <Bars data={overview.leave.daysByMonth.map(c => ({ ...c, label: monthLabel(c.label) }))} />
            </div>
            <div className="chart-card">
              <div className="chart-title">People by department</div>
              <Bars data={overview.byDepartment} />
            </div>
            <div className="chart-card">
              <div className="chart-title">Leave requests by status</div>
              <Bars data={overview.leave.statusCounts} colors={l => STATUS_COLOR[l] ?? "#6366f1"} />
            </div>
            {overview.projects && (
              <div className="chart-card">
                <div className="chart-title">Tasks by status</div>
                <Bars data={overview.projects.issuesByStatus.map(c => ({ ...c, label: c.label }))} colors={l => STATUS_COLOR[l] ?? "#6366f1"} />
              </div>
            )}
            {overview.byRole.length > 0 && (
              <div className="chart-card">
                <div className="chart-title">People by role</div>
                <Bars data={overview.byRole} />
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

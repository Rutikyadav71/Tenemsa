import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TooltipContentProps } from "recharts";
import type { NameType, ValueType } from "recharts/types/component/DefaultTooltipContent";
import { Card, CardHeader, EmptyState } from "../../../shared/ui";
import type { AnalyticsOverview, Count } from "../../../shared/types/models";

const COLORS = ["var(--chart-1)", "var(--chart-2)", "var(--chart-3)", "var(--chart-4)", "var(--chart-5)", "var(--chart-6)"];

function Tip({ active, payload, label }: TooltipContentProps<ValueType, NameType>) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: "var(--bg-elevated)", border: "1px solid var(--border-bright)", borderRadius: 8, padding: "8px 12px", fontSize: 12.5 }}>
      {label !== undefined && label !== "" && <div style={{ color: "var(--text-3)", marginBottom: 2 }}>{label}</div>}
      {payload.map((p, i) => <div key={i} style={{ fontWeight: 600, color: "var(--text-1)" }}>{p.name}: {p.value}</div>)}
    </div>
  );
}

const hasData = (rows: Count[]) => rows.some(r => r.value > 0);
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase().replace(/^\w/, c => c.toUpperCase());

function BarCard({ title, subtitle, rows, color = "var(--chart-1)" }: { title: string; subtitle?: string; rows: Count[]; color?: string }) {
  const data = rows.map(r => ({ name: pretty(r.label), value: r.value }));
  return (
    <Card pad={false}>
      <CardHeader title={title} subtitle={subtitle} />
      {hasData(rows) ? (
        <div className="db-chart" role="img" aria-label={`${title}: ${data.map(d => `${d.name} ${d.value}`).join(", ")}`}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
              <CartesianGrid stroke="var(--chart-grid)" vertical={false} />
              <XAxis dataKey="name" tick={{ fill: "var(--text-3)", fontSize: 11 }} tickLine={false} axisLine={false} interval={0} />
              <YAxis allowDecimals={false} tick={{ fill: "var(--text-3)", fontSize: 11 }} tickLine={false} axisLine={false} />
              <Tooltip content={Tip} cursor={{ fill: "var(--hairline)" }} />
              <Bar dataKey="value" name={title} fill={color} radius={[6, 6, 0, 0]} maxBarSize={36} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : <EmptyState title="Nothing to show yet" text="Figures appear here as your team uses Tenemsa." />}
    </Card>
  );
}

function DonutCard({ title, subtitle, rows }: { title: string; subtitle?: string; rows: Count[] }) {
  const data = rows.filter(r => r.value > 0).map(r => ({ name: pretty(r.label), value: r.value }));
  return (
    <Card pad={false}>
      <CardHeader title={title} subtitle={subtitle} />
      {data.length ? (
        <>
          <div className="db-chart" style={{ height: 190 }} role="img" aria-label={`${title}: ${data.map(d => `${d.name} ${d.value}`).join(", ")}`}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={data} dataKey="value" nameKey="name" innerRadius={52} outerRadius={78} paddingAngle={2} stroke="var(--bg-surface)">
                  {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip content={Tip} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="db-legend">{data.map((d, i) => <span key={d.name}><i style={{ background: COLORS[i % COLORS.length] }} />{d.name} · {d.value}</span>)}</div>
        </>
      ) : <EmptyState title="Nothing to show yet" text="Figures appear here as your team uses Tenemsa." />}
    </Card>
  );
}

export default function OverviewCharts({ o }: { o: AnalyticsOverview }) {
  const a = o.attendance;
  const attendance: Count[] = [
    { label: "Present", value: a.present }, { label: "Late", value: a.late }, { label: "Half day", value: a.halfDay },
    { label: "On leave", value: a.onLeave }, { label: "Absent", value: a.absent },
  ];
  return (
    <div className="db-charts">
      <BarCard title="Leave days by month" subtitle="Approved leave" rows={o.leave.daysByMonth} color="var(--chart-2)" />
      <DonutCard title="Attendance, last 30 days" rows={attendance} />
      <BarCard title="People by department" rows={o.byDepartment} />
      {o.projects && <BarCard title="Work items by status" rows={o.projects.issuesByStatus} color="var(--chart-5)" />}
    </div>
  );
}

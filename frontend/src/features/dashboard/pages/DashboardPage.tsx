import { useMemo, type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import axiosInstance from "../../../shared/api/axiosInstance";
import { can } from "../../../shared/lib/roles";
import { Alert, Badge, Button, Card, CardHeader, type Tone } from "../../../shared/ui";
import { getMySummary, getOverview } from "../../analytics/services/analyticsService";
import { listTeamLeaves } from "../../leave/services/teamLeaveService";
import { listMeetings } from "../../meetings/services/meetingService";
import { myIssues } from "../../projects/services/projectService";
import type { Attendance, AnalyticsOverview, Issue, MeetingListItem, Notification, TeamLeave } from "../../../shared/types/models";
import {
  LuBell, LuCalendarCheck, LuCalendarClock, LuChartColumn, LuCircleAlert, LuClock3, LuDownload, LuListTodo, LuMessagesSquare,
  LuSparkles, LuTriangleAlert, LuUsersRound, LuVideo,
} from "react-icons/lu";
import HeroScene from "../components/HeroScene";
import KpiCard from "../components/KpiCard";
import ListCard from "../components/ListCard";
import OverviewCharts from "../components/OverviewCharts";
import { useLoad } from "../hooks/useLoad";
import { downloadCsv, overviewToCsv } from "../exportReport";
import "../dashboard.css";
import "../hero.css";

const ROLE_COPY: Record<string, { label: string; line: string }> = {
  OWNER: { label: "Owner", line: "Here's how your company is doing today." },
  SUPER_ADMIN: { label: "Super admin", line: "Here's how your company is doing today." },
  ADMIN: { label: "Admin", line: "Here's what needs attention across the company." },
  HR: { label: "HR", line: "Here's your people overview for today." },
  MANAGER: { label: "Manager", line: "Here's how your teams are doing today." },
  EMPLOYEE: { label: "Employee", line: "Here's what's on your plate today." },
  INTERN: { label: "Intern", line: "Here's what's on your plate today." },
};

const PRIORITY_TONE: Record<string, Tone> = { URGENT: "danger", HIGH: "warning", MEDIUM: "info", LOW: "neutral" };
const pretty = (s: string) => s.replace(/_/g, " ").toLowerCase().replace(/^\w/, c => c.toUpperCase());

const startOfToday = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; };
const parseDay = (s: string) => new Date(`${s.slice(0, 10)}T00:00:00`);
const fmtDay = (s: string) => parseDay(s).toLocaleDateString(undefined, { day: "numeric", month: "short" });
const pct = (n: number | null) => (n == null ? "–" : `${n}%`);

function dueLabel(due: string | null): { text: string; overdue: boolean } {
  if (!due) return { text: "No due date", overdue: false };
  const diff = Math.round((parseDay(due).getTime() - startOfToday().getTime()) / 86_400_000);
  if (diff < 0) return { text: `${-diff} day${diff === -1 ? "" : "s"} overdue`, overdue: true };
  if (diff === 0) return { text: "Due today", overdue: false };
  if (diff === 1) return { text: "Due tomorrow", overdue: false };
  return { text: `Due ${fmtDay(due)}`, overdue: false };
}

export default function DashboardPage() {
  const base = `/${useLocation().pathname.split("/")[1]}`;
  const roleKey = localStorage.getItem("userRole") || "EMPLOYEE";
  const copy = ROLE_COPY[roleKey] ?? ROLE_COPY.EMPLOYEE;
  const name = (localStorage.getItem("name") || "").trim().split(/\s+/)[0] || "there";
  const empId = localStorage.getItem("empId");
  const company = localStorage.getItem("companyName") || "";
  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  const today = new Date().toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long", year: "numeric" });

  const perms = useMemo(() => ({
    overview: can("report:view:company", "report:view:team"),
    teamLeave: can("leave:approve:team"),
    adminLeave: can("leave:view:any"),
    projects: can("project:view"),
    meetings: can("ai:use"),
    attendance: can("attendance:mark:self") && !!empId,
    apply: can("leave:apply:self"),
    assistant: can("ai:assistant:self"),
    chat: can("chat:use"),
  }), [empId]);

  const me = useLoad(getMySummary);
  const overview = useLoad<AnalyticsOverview>(getOverview, perms.overview);
  const tasks = useLoad<Issue[]>(myIssues, perms.projects);
  const approvals = useLoad<TeamLeave[]>(() => listTeamLeaves("PENDING"), perms.teamLeave);
  const meetings = useLoad<MeetingListItem[]>(listMeetings, perms.meetings);
  const notes = useLoad<Notification[]>(async () => (await axiosInstance.get<Notification[]>("/api/notifications")).data);
  const todayRow = useLoad<Attendance | null>(async () => (await axiosInstance.get<Attendance | null>(`/api/attendance/today/${empId}`)).data, perms.attendance);

  const myOpen = (tasks.data ?? []).filter(t => t.status !== "DONE")
    .sort((a, b) => (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999"));
  const upcomingMeetings = (meetings.data ?? []).slice(0, 4);
  const unread = (notes.data ?? []).filter(n => !n.read).slice(0, 5);
  const m = me.data;
  const o = overview.data;

  const quick: { label: string; to: string; show: boolean; icon: ReactNode }[] = [
    { label: "Apply for leave", to: `${base}/apply-leave`, show: perms.apply, icon: <LuCalendarClock size={16} /> },
    { label: "Attendance", to: `${base}/attendance`, show: perms.attendance || can("attendance:manage:any"), icon: <LuClock3 size={16} /> },
    { label: "Ask Tenemsa AI", to: `${base}/assistant`, show: perms.assistant, icon: <LuSparkles size={16} /> },
    { label: "Open chat", to: `${base}/chat`, show: perms.chat, icon: <LuMessagesSquare size={16} /> },
  ];

  const exportCsv = () => { if (o) downloadCsv(`tenemsa-${o.scope === "COMPANY" ? "company" : "team"}-overview.csv`, overviewToCsv(o)); };

  return (
    <div className="db">
      <section className="db-hero" aria-label="Welcome">
        <div className="db-hero-body">
          <div>
            <h1>{greeting}, {name}</h1>
            <p className="db-hero-sub">{today} <Badge tone="accent">{copy.label}</Badge></p>
            <p className="db-hero-sub" style={{ marginTop: 4 }}>{company ? `${copy.line.replace(/\.$/, "")} at ${company}.` : copy.line}</p>
          </div>
          <div className="db-quick">{quick.filter(q => q.show).map(q => <Link key={q.label} to={q.to} className="tn-btn tn-btn-secondary">{q.icon}{q.label}</Link>)}</div>
        </div>
        <HeroScene />
      </section>

      {perms.overview && (
        <>
          <div className="db-section-title">{o?.scope === "TEAM" ? "Your teams" : "Company"}</div>
          {overview.failed ? (
            <Alert tone="error"><span>The company overview couldn't be loaded. <button type="button" className="db-retry" onClick={overview.reload}>Try again</button></span></Alert>
          ) : (
            <div className="db-kpis">
              <KpiCard label="People" value={o?.headcount} icon={<LuUsersRound size={16} />} loading={overview.loading} to={perms.adminLeave || can("user:view:any") ? `${base}/employees` : undefined} />
              <KpiCard label="Pending leave" value={o?.leave.pending} tone={o && o.leave.pending > 0 ? "warn" : "default"} icon={<LuCalendarClock size={16} />} loading={overview.loading}
                hint={o && o.leave.pending > 0 ? "Waiting for a decision" : "All caught up"} to={perms.adminLeave ? `${base}/leaves` : perms.teamLeave ? `${base}/team-leaves` : undefined} />
              <KpiCard label="Off today" value={o?.leave.offToday.length} icon={<LuCalendarCheck size={16} />} loading={overview.loading}
                hint={o?.leave.offToday.length ? o.leave.offToday.slice(0, 2).join(", ") + (o.leave.offToday.length > 2 ? ` +${o.leave.offToday.length - 2}` : "") : "Everyone is in"} />
              <KpiCard label="Attendance (30d)" value={pct(o?.attendance.presentRate ?? null)} tone="good" icon={<LuClock3 size={16} />} loading={overview.loading}
                hint={o?.attendance.averageWorkHours != null ? `${o.attendance.averageWorkHours} h average day` : undefined} />
              {o?.projects && <KpiCard label="Overdue work" value={o.projects.overdueIssues} tone={o.projects.overdueIssues > 0 ? "bad" : "default"} icon={<LuTriangleAlert size={16} />} to={`${base}/projects`} />}
            </div>
          )}
        </>
      )}

      <div className="db-section-title">My work</div>
      {me.failed ? <Alert tone="error"><span>Your summary couldn't be loaded. <button type="button" className="db-retry" onClick={me.reload}>Try again</button></span></Alert> : (
      <div className="db-kpis">
        <KpiCard label="Open tasks" value={m?.openIssues} icon={<LuListTodo size={16} />} loading={me.loading} to={perms.projects ? `${base}/projects` : undefined} />
        <KpiCard label="Overdue" value={m?.overdueIssues} tone={m && m.overdueIssues > 0 ? "bad" : "default"} icon={<LuCircleAlert size={16} />} loading={me.loading} />
        {perms.apply && <KpiCard label="My leave requests" value={m?.pendingLeaveRequests} icon={<LuCalendarClock size={16} />} loading={me.loading}
          hint={m ? `${m.leaveDaysApprovedThisYear} approved days this year` : undefined} to={empId ? `${base}/leaves/employee` : undefined} />}
        {perms.attendance && <KpiCard label="My attendance" value={pct(m?.presentRateLast30Days ?? null)} tone="good" icon={<LuClock3 size={16} />} loading={me.loading} hint="Last 30 days" to={`${base}/attendance`} />}
        <KpiCard label="Unread alerts" value={m?.unreadNotifications} tone={m && m.unreadNotifications > 0 ? "warn" : "default"} icon={<LuBell size={16} />} loading={me.loading} to={`${base}/notifications`} />
      </div>)}

      <div className="db-cols">
        <div className="db-stack">
          {perms.projects && (
            <ListCard title="My tasks" subtitle="Soonest due first" items={myOpen.slice(0, 6)} loading={tasks.loading} failed={tasks.failed} onRetry={tasks.reload}
              empty={{ title: "No open tasks", text: "Tasks assigned to you will show up here." }} keyOf={t => t.id}
              more={myOpen.length > 6 ? { to: `${base}/projects`, label: `See all ${myOpen.length} tasks` } : { to: `${base}/projects`, label: "Go to projects" }}
              render={t => {
                const due = dueLabel(t.dueDate);
                return (
                  <Link className="db-row" to={`${base}/projects/${t.projectId}`}>
                    <div className="main"><div className="t">{t.title}</div><div className="s">{t.key} · {pretty(t.status)}</div></div>
                    <Badge tone={PRIORITY_TONE[t.priority] ?? "neutral"}>{pretty(t.priority)}</Badge>
                    <span className="s" style={{ color: due.overdue ? "var(--red)" : undefined, whiteSpace: "nowrap" }}>{due.text}</span>
                  </Link>
                );
              }} />
          )}

          {perms.teamLeave && (
            <ListCard title="Leave waiting for you" items={(approvals.data ?? []).slice(0, 5)} loading={approvals.loading} failed={approvals.failed} onRetry={approvals.reload}
              empty={{ title: "Nothing to approve", text: "New leave requests from your team will appear here." }} keyOf={l => l.id}
              more={{ to: `${base}/team-leaves`, label: "Review all requests" }}
              render={l => (
                <Link className="db-row" to={`${base}/team-leaves`}>
                  <div className="main"><div className="t">{l.employeeName}</div><div className="s">{fmtDay(l.startDate)} – {fmtDay(l.endDate)} · {l.days} day{l.days === 1 ? "" : "s"}</div></div>
                  {l.clashesWith.length > 0 && <Badge tone="warning">Overlaps</Badge>}
                </Link>
              )} />
          )}
        </div>

        <div className="db-stack">
          {perms.attendance && (
            <Card pad={false}>
              <CardHeader title="Today" subtitle="Your attendance" />
              <div className="db-pad">
                {todayRow.loading ? <span className="s">Checking…</span>
                  : todayRow.failed ? <Button variant="secondary" size="sm" onClick={todayRow.reload}>Try again</Button>
                  : todayRow.data?.checkIn ? (
                    <div style={{ display: "grid", gap: 6 }}>
                      <Badge tone={todayRow.data.checkOut ? "neutral" : "success"} dot>{todayRow.data.checkOut ? "Checked out" : "Checked in"}</Badge>
                      <div className="s" style={{ fontSize: 13 }}>In {String(todayRow.data.checkIn).slice(0, 5)}{todayRow.data.checkOut ? ` · Out ${String(todayRow.data.checkOut).slice(0, 5)}` : ""}</div>
                    </div>
                  ) : <div style={{ display: "grid", gap: 10 }}><span style={{ fontSize: 13, color: "var(--text-2)" }}>You haven't checked in yet.</span><Link to={`${base}/attendance`} className="tn-btn tn-btn-primary tn-btn-sm" style={{ justifySelf: "start" }}>Check in</Link></div>}
              </div>
            </Card>
          )}

          {perms.meetings && (
            <ListCard title="Recent meetings" items={upcomingMeetings} loading={meetings.loading} failed={meetings.failed} onRetry={meetings.reload}
              empty={{ title: "No meetings yet", text: "Meeting notes you add are summarised here." }} keyOf={x => x.id} more={{ to: `${base}/meetings`, label: "All meetings" }}
              render={x => (
                <Link className="db-row" to={`${base}/meetings/${x.id}`}>
                  <span className="ic" aria-hidden="true" style={{ color: "var(--accent-light)" }}><LuVideo size={16} /></span>
                  <div className="main"><div className="t">{x.title}</div><div className="s">{x.meetingDate || new Date(x.createdAt).toLocaleDateString()} · {x.actionItemCount} action item{x.actionItemCount === 1 ? "" : "s"}</div></div>
                  <Badge tone={x.status === "APPROVED" ? "success" : "warning"}>{x.status === "APPROVED" ? "Approved" : "Draft"}</Badge>
                </Link>
              )} />
          )}

          <ListCard title="Unread notifications" items={unread} loading={notes.loading} failed={notes.failed} onRetry={notes.reload}
            empty={{ title: "You're all caught up", text: "New alerts will appear here." }} keyOf={n => n.id} more={{ to: `${base}/notifications`, label: "Open notifications" }}
            render={n => <Link className="db-row" to={`${base}/notifications`}><div className="main"><div className="t" style={{ whiteSpace: "normal" }}>{n.message}</div>{n.createdAt && <div className="s">{new Date(n.createdAt).toLocaleString()}</div>}</div></Link>} />
        </div>
      </div>

      {perms.overview && o && (
        <>
          <div className="db-section-title" style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span><LuChartColumn size={14} style={{ verticalAlign: -2 }} /> Insights</span>
            <Button size="sm" variant="secondary" icon={<LuDownload size={14} />} onClick={exportCsv}>Download CSV</Button>
          </div>
          <OverviewCharts o={o} />
        </>
      )}
    </div>
  );
}

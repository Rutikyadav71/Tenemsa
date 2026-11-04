import type { AnalyticsOverview } from "../../shared/types/models";

const cell = (v: string | number | null | undefined) => {
  const s = v == null ? "" : String(v);
  // Leading = + - @ can be run as a formula when the file is opened in a spreadsheet.
  const safe = /^[=+\-@]/.test(s) ? `'${s}` : s;
  return /[",\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
};

/** A plain CSV of the company/team overview, opened by Excel, Sheets and Numbers alike. */
export function overviewToCsv(o: AnalyticsOverview): string {
  const rows: (string | number | null)[][] = [["Section", "Item", "Value"]];
  rows.push(["Summary", "Scope", o.scope === "COMPANY" ? "Company" : "My teams"], ["Summary", "People", o.headcount]);
  rows.push(["Leave", "Pending requests", o.leave.pending], ["Leave", "Off today", o.leave.offToday.length]);
  o.leave.daysByMonth.forEach(c => rows.push(["Leave days by month", c.label, c.value]));
  o.leave.statusCounts.forEach(c => rows.push(["Leave by status", c.label, c.value]));
  const a = o.attendance;
  rows.push(["Attendance", "Days covered", a.days], ["Attendance", "Present", a.present], ["Attendance", "Late", a.late], ["Attendance", "Half day", a.halfDay],
    ["Attendance", "Absent", a.absent], ["Attendance", "On leave", a.onLeave], ["Attendance", "Present rate %", a.presentRate], ["Attendance", "Average work hours", a.averageWorkHours]);
  o.byDepartment.forEach(c => rows.push(["People by department", c.label, c.value]));
  o.byRole.forEach(c => rows.push(["People by role", c.label, c.value]));
  if (o.projects) {
    rows.push(["Work", "Overdue items", o.projects.overdueIssues]);
    o.projects.issuesByStatus.forEach(c => rows.push(["Work items by status", c.label, c.value]));
  }
  return rows.map(r => r.map(cell).join(",")).join("\r\n");
}

export function downloadCsv(name: string, csv: string) {
  const url = URL.createObjectURL(new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8" }));
  const a = document.createElement("a");
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

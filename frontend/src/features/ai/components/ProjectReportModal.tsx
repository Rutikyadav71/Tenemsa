import { useState } from "react";
import { aiErrorMessage, projectReport } from "../services/aiService";
import type { ProjectReport, ReportType, Sprint } from "../../../shared/types/models";

interface Props {
  projectId: number;
  sprints: Sprint[];
  onClose: () => void;
}

const list = (title: string, items: string[]) => items.length === 0 ? null : (
  <div style={{ marginTop: 14 }}>
    <div style={{ fontWeight: 600, color: "var(--text-1)", marginBottom: 4 }}>{title}</div>
    <ul style={{ margin: 0, paddingLeft: 18, color: "var(--text-2)", fontSize: 14 }}>
      {items.map((x, i) => <li key={i}>{x}</li>)}
    </ul>
  </div>
);

export default function ProjectReportModal({ projectId, sprints, onClose }: Props) {
  const completed = sprints.filter(s => s.status === "COMPLETED");
  const [type, setType] = useState<ReportType>("STATUS_UPDATE");
  const [days, setDays] = useState(7);
  const [sprintId, setSprintId] = useState("");
  const [report, setReport] = useState<ProjectReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  const run = async () => {
    setError(""); setBusy(true); setReport(null);
    try {
      setReport(await projectReport(projectId, type, type === "STATUS_UPDATE" ? { days } : sprintId ? { sprintId: Number(sprintId) } : {}));
    } catch (err: any) { setError(aiErrorMessage(err, "Could not create the report.")); }
    finally { setBusy(false); }
  };

  const asText = (r: ProjectReport) => [
    r.headline, "", r.summary,
    ...(r.highlights.length ? ["", "Highlights", ...r.highlights.map(x => `- ${x}`)] : []),
    ...(r.risks.length ? ["", "Risks", ...r.risks.map(x => `- ${x}`)] : []),
    ...(r.nextSteps.length ? ["", "Next steps", ...r.nextSteps.map(x => `- ${x}`)] : []),
  ].join("\n");

  const copy = async () => {
    if (!report) return;
    try { await navigator.clipboard.writeText(asText(report)); setCopied(true); setTimeout(() => setCopied(false), 1500); } catch { /* clipboard blocked */ }
  };

  return (
    <div className="ems-modal-backdrop" onClick={onClose}>
      <div className="ems-modal" style={{ width: 680, maxWidth: "95vw", maxHeight: "90vh", overflowY: "auto" }} onClick={e => e.stopPropagation()}>
        <div style={{ fontSize: 17, fontWeight: 700, color: "var(--text-1)", marginBottom: 4 }}>AI project report</div>
        <div style={{ fontSize: 13, color: "var(--text-3)", marginBottom: 14 }}>
          The numbers come from your board; the AI only writes them up. Check the figures under "Based on".
        </div>
        {error && <div className="alert-ems alert-error" onClick={() => setError("")}>{error}</div>}

        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "end" }}>
          <div>
            <label className="form-label">Report</label>
            <select className="form-control-ems" value={type} onChange={e => setType(e.target.value as ReportType)}>
              <option value="STATUS_UPDATE">Status update</option>
              <option value="SPRINT_RETRO" disabled={completed.length === 0}>Sprint retrospective{completed.length === 0 ? " (no completed sprint)" : ""}</option>
            </select>
          </div>
          {type === "STATUS_UPDATE" ? (
            <div>
              <label className="form-label">Recent days</label>
              <select className="form-control-ems" value={days} onChange={e => setDays(Number(e.target.value))}>
                {[1, 3, 7, 14, 30].map(d => <option key={d} value={d}>{d}</option>)}
              </select>
            </div>
          ) : (
            <div>
              <label className="form-label">Sprint</label>
              <select className="form-control-ems" value={sprintId} onChange={e => setSprintId(e.target.value)}>
                <option value="">Latest completed</option>
                {completed.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
          )}
          <button className="btn-ems btn-primary-ems" disabled={busy} onClick={run}>{busy ? "Writing…" : "Create report"}</button>
        </div>

        {report && (
          <div style={{ marginTop: 18 }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text-1)" }}>{report.headline}</div>
            <p style={{ color: "var(--text-2)", whiteSpace: "pre-wrap" }}>{report.summary}</p>
            {list("Highlights", report.highlights)}
            {list("Risks", report.risks)}
            {list("Next steps", report.nextSteps)}
            {list("Based on", report.basedOn)}
            <div style={{ display: "flex", justifyContent: "space-between", marginTop: 16, alignItems: "center" }}>
              <span style={{ fontSize: 12, color: "var(--text-3)" }}>AI can make mistakes. Generated {new Date(report.generatedAt).toLocaleString()}.</span>
              <button className="btn-ems btn-secondary-ems" onClick={copy}>{copied ? "Copied" : "Copy as text"}</button>
            </div>
          </div>
        )}
        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
          <button className="btn-ems btn-secondary-ems" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}

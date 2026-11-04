import { useState } from "react";
import { aiErrorMessage, projectReport } from "../services/aiService";
import { Alert, Button, Field, Modal, Select, useToast } from "../../../shared/ui";
import type { ProjectReport, ReportType, Sprint } from "../../../shared/types/models";

interface Props {
  projectId: number;
  sprints: Sprint[];
  onClose: () => void;
}

const list = (title: string, items: string[]) => items.length === 0 ? null : (
  <div style={{ marginTop: 14 }}>
    <h4 style={{ fontWeight: 600, color: "var(--text-1)", margin: "0 0 4px", fontSize: 14 }}>{title}</h4>
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
  const toast = useToast();

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
    try { await navigator.clipboard.writeText(asText(report)); toast.success("Report copied."); } catch { toast.error("Your browser blocked copying. Select the text and copy it by hand."); }
  };

  return (
    <Modal open onClose={onClose} dismissible={!busy} size="lg" title="Project report"
      description="The numbers come from your board; Tenemsa AI only writes them up. Check the figures under “Based on”."
      footer={<><Button variant="secondary" onClick={onClose}>Close</Button>{report && <Button onClick={copy}>Copy as text</Button>}</>}>
      {error && <Alert tone="error">{error}</Alert>}
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "end", marginTop: error ? 12 : 0 }}>
        <Field label="Report">
          <Select value={type} onChange={e => setType(e.target.value as ReportType)} data-autofocus>
            <option value="STATUS_UPDATE">Status update</option>
            <option value="SPRINT_RETRO" disabled={completed.length === 0}>Sprint retrospective{completed.length === 0 ? " (no completed sprint)" : ""}</option>
          </Select>
        </Field>
        {type === "STATUS_UPDATE" ? (
          <Field label="Recent days"><Select value={days} onChange={e => setDays(Number(e.target.value))}>{[1, 3, 7, 14, 30].map(d => <option key={d} value={d}>{d}</option>)}</Select></Field>
        ) : (
          <Field label="Sprint">
            <Select value={sprintId} onChange={e => setSprintId(e.target.value)}>
              <option value="">Latest completed</option>
              {completed.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
          </Field>
        )}
        <div style={{ paddingBottom: 14 }}><Button variant="primary" loading={busy} onClick={run}>Create report</Button></div>
      </div>
      {report && (
        <div style={{ marginTop: 18 }}>
          <h3 style={{ fontSize: 16, fontWeight: 600, color: "var(--text-1)", margin: 0 }}>{report.headline}</h3>
          <p style={{ color: "var(--text-2)", whiteSpace: "pre-wrap" }}>{report.summary}</p>
          {list("Highlights", report.highlights)}
          {list("Risks", report.risks)}
          {list("Next steps", report.nextSteps)}
          {list("Based on", report.basedOn)}
          <p style={{ fontSize: 12.5, color: "var(--text-3)", marginTop: 16 }}>AI can make mistakes. Generated {new Date(report.generatedAt).toLocaleString()}.</p>
        </div>
      )}
    </Modal>
  );
}

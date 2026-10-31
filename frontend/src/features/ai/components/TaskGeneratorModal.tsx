import { useState } from "react";
import { aiErrorMessage, createAiTasks, generateTasks } from "../services/aiService";
import type { IssuePriority, IssueType, SuggestedTask } from "../../../shared/types/models";

const TYPES: IssueType[] = ["TASK", "BUG", "STORY"];
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

interface Row extends SuggestedTask { keep: boolean; }

interface Props {
  projectId: number;
  onClose: () => void;
  /** Called after issues were created so the board can reload. */
  onCreated: (count: number) => void;
}

export default function TaskGeneratorModal({ projectId, onClose, onCreated }: Props) {
  const [goal, setGoal] = useState("");
  const [maxTasks, setMaxTasks] = useState(8);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const generate = async () => {
    setError(""); setBusy(true);
    try {
      const res = await generateTasks(projectId, goal.trim(), maxTasks);
      setRows(res.tasks.map(t => ({ ...t, keep: !t.possibleDuplicate })));
      if (res.tasks.length === 0) setError("The AI had no suggestions. Try describing the goal in more detail.");
    } catch (err: any) { setError(aiErrorMessage(err, "Could not generate tasks.")); }
    finally { setBusy(false); }
  };

  const create = async () => {
    const chosen = (rows || []).filter(r => r.keep && r.title.trim());
    if (chosen.length === 0) { setError("Select at least one task."); return; }
    setError(""); setBusy(true);
    try {
      const created = await createAiTasks(projectId, chosen.map(r => ({
        title: r.title.trim(), description: r.description || undefined, type: r.type, priority: r.priority,
        storyPoints: r.storyPoints, labels: r.labels,
      })));
      onCreated(created.length);
      onClose();
    } catch (err: any) { setError(aiErrorMessage(err, "Could not create the tasks.")); setBusy(false); }
  };

  const patch = (i: number, p: Partial<Row>) => setRows(rows!.map((r, k) => (k === i ? { ...r, ...p } : r)));

  return (
    <div className="ems-modal-backdrop" onClick={onClose}>
      <div className="ems-modal" style={{ width: 720, maxWidth: "95vw", maxHeight: "90vh", overflowY: "auto" }} onClick={e => e.stopPropagation()}>
        <div style={{ fontSize: 17, fontWeight: 700, color: "var(--text-1)", marginBottom: 4 }}>Suggest tasks with AI</div>
        <div style={{ fontSize: 13, color: "var(--text-3)", marginBottom: 14 }}>
          Describe a goal. The AI proposes tasks; nothing is created until you review the list and confirm.
        </div>
        {error && <div className="alert-ems alert-error" onClick={() => setError("")}>{error}</div>}

        {rows === null ? (
          <>
            <label className="form-label">Goal</label>
            <textarea className="form-control-ems" rows={5} maxLength={4000} value={goal} onChange={e => setGoal(e.target.value)}
                      placeholder="For example: let customers export their invoices as PDF and email them" />
            <div style={{ fontSize: 12, color: "var(--text-3)", margin: "4px 0 12px" }}>{goal.length} / 4000 · sent to the AI provider, so do not include secrets.</div>
            <label className="form-label">Maximum number of tasks</label>
            <input type="number" min={1} max={15} className="form-control-ems" style={{ width: 100 }} value={maxTasks}
                   onChange={e => setMaxTasks(Math.min(15, Math.max(1, Number(e.target.value) || 1)))} />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 16 }}>
              <button className="btn-ems btn-secondary-ems" onClick={onClose}>Cancel</button>
              <button className="btn-ems btn-primary-ems" disabled={busy || goal.trim().length < 15} onClick={generate}>
                {busy ? "Thinking…" : "Suggest tasks"}
              </button>
            </div>
          </>
        ) : (
          <>
            {rows.map((r, i) => (
              <div key={i} className="ems-card" style={{ padding: 12, marginBottom: 10, opacity: r.keep ? 1 : 0.55 }}>
                <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 8 }}>
                  <input type="checkbox" checked={r.keep} onChange={e => patch(i, { keep: e.target.checked })} />
                  <input className="form-control-ems" maxLength={200} value={r.title} onChange={e => patch(i, { title: e.target.value })} />
                </div>
                {r.possibleDuplicate && <div style={{ fontSize: 12, color: "#f59e0b", marginBottom: 6 }}>An issue with this title already exists in the project.</div>}
                <textarea className="form-control-ems" rows={2} value={r.description || ""} onChange={e => patch(i, { description: e.target.value })} style={{ marginBottom: 8 }} />
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <select className="form-control-ems" style={{ width: "auto" }} value={r.type} onChange={e => patch(i, { type: e.target.value as IssueType })}>
                    {TYPES.map(t => <option key={t}>{t}</option>)}
                  </select>
                  <select className="form-control-ems" style={{ width: "auto" }} value={r.priority} onChange={e => patch(i, { priority: e.target.value as IssuePriority })}>
                    {PRIORITIES.map(p => <option key={p}>{p}</option>)}
                  </select>
                  <input type="number" min={0} max={100} className="form-control-ems" style={{ width: 90 }} placeholder="Points"
                         value={r.storyPoints ?? ""} onChange={e => patch(i, { storyPoints: e.target.value === "" ? null : Number(e.target.value) })} />
                  {r.labels.map(l => <span key={l} style={{ fontSize: 12, color: "var(--text-3)", alignSelf: "center" }}>#{l}</span>)}
                </div>
              </div>
            ))}
            <div style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 10 }}>
              Created issues get the label "ai-draft" so they are easy to find and review later.
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
              <button className="btn-ems btn-secondary-ems" disabled={busy} onClick={() => setRows(null)}>Back</button>
              <div style={{ display: "flex", gap: 8 }}>
                <button className="btn-ems btn-secondary-ems" onClick={onClose}>Cancel</button>
                <button className="btn-ems btn-primary-ems" disabled={busy} onClick={create}>
                  {busy ? "Creating…" : `Create ${rows.filter(r => r.keep).length} issue(s)`}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

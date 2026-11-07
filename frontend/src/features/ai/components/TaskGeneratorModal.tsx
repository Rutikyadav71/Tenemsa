import { useState } from "react";
import { aiErrorMessage, createAiTasks, generateTasks } from "../services/aiService";
import { Alert, Badge, Button, Card, Field, Input, Modal, Select, Textarea } from "../../../shared/ui";
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
      if (res.tasks.length === 0) setError("Tenemsa AI had no suggestions. Try describing the goal in more detail.");
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

  const kept = (rows || []).filter(r => r.keep).length;
  const footer = rows === null ? (
    <>
      <Button variant="secondary" onClick={onClose}>Cancel</Button>
      <Button variant="primary" loading={busy} disabled={goal.trim().length < 15} onClick={generate}>Suggest tasks</Button>
    </>
  ) : (
    <>
      <Button variant="secondary" disabled={busy} onClick={() => setRows(null)}>Back</Button>
      <Button variant="secondary" onClick={onClose}>Cancel</Button>
      <Button variant="primary" loading={busy} disabled={kept === 0} onClick={create}>Create {kept} issue{kept === 1 ? "" : "s"}</Button>
    </>
  );

  return (
    <Modal open onClose={onClose} dismissible={!busy} size="lg" title="Suggest tasks with Tenemsa AI"
      description="Describe a goal. Tenemsa AI proposes tasks; nothing is created until you review the list and confirm." footer={footer}>
      <div style={{ display: "grid", gap: 12 }}>
        {error && <Alert tone="error">{error}</Alert>}
        {rows === null ? (
          <>
            <Field label="Goal" required hint={`${goal.length} / 4000 · Sent to Tenemsa AI, so leave out secrets. At least 15 characters.`}>
              <Textarea rows={5} maxLength={4000} value={goal} onChange={e => setGoal(e.target.value)} data-autofocus
                placeholder="For example: let customers export their invoices as PDF and email them" />
            </Field>
            <Field label="Maximum number of tasks">
              <Input type="number" min={1} max={15} style={{ width: 100 }} value={maxTasks} onChange={e => setMaxTasks(Math.min(15, Math.max(1, Number(e.target.value) || 1)))} />
            </Field>
          </>
        ) : (
          <>
            {rows.map((r, i) => (
              <Card key={i} style={{ opacity: r.keep ? 1 : 0.55 }}>
                <div style={{ display: "grid", gap: 8 }}>
                  <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                    <input type="checkbox" aria-label={`Keep task ${i + 1}`} checked={r.keep} onChange={e => patch(i, { keep: e.target.checked })} />
                    <Input aria-label={`Title of task ${i + 1}`} maxLength={200} value={r.title} onChange={e => patch(i, { title: e.target.value })} />
                  </div>
                  {r.possibleDuplicate && <Badge tone="warning">An issue with this title already exists</Badge>}
                  <Textarea aria-label="Description" rows={2} value={r.description || ""} onChange={e => patch(i, { description: e.target.value })} />
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                    <Select aria-label="Type" style={{ width: "auto" }} value={r.type} onChange={e => patch(i, { type: e.target.value as IssueType })}>{TYPES.map(t => <option key={t}>{t}</option>)}</Select>
                    <Select aria-label="Priority" style={{ width: "auto" }} value={r.priority} onChange={e => patch(i, { priority: e.target.value as IssuePriority })}>{PRIORITIES.map(p => <option key={p}>{p}</option>)}</Select>
                    <Input aria-label="Story points" type="number" min={0} max={100} style={{ width: 90 }} placeholder="Points" value={r.storyPoints ?? ""}
                      onChange={e => patch(i, { storyPoints: e.target.value === "" ? null : Number(e.target.value) })} />
                    {[...new Set(r.labels)].map(l => <span key={l} style={{ fontSize: 12, color: "var(--text-3)" }}>#{l}</span>)}
                  </div>
                </div>
              </Card>
            ))}
            <p style={{ fontSize: 12.5, color: "var(--text-3)", margin: 0 }}>Created issues get the label “ai-draft” so they are easy to find and review later.</p>
          </>
        )}
      </div>
    </Modal>
  );
}

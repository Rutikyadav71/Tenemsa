import { useCallback, useEffect, useState } from "react";
import { decideLeave, leaveErrorMessage, listTeamLeaves, type TeamLeaveFilter } from "../services/teamLeaveService";
import { can } from "../../../shared/lib/roles";
import type { TeamLeave } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, EmptyState, ErrorState, Field, Modal, PageHeader, SkeletonList, Tabs, Textarea, useToast, type Tone } from "../../../shared/ui";
import "./teamLeaves.css";

const FILTERS: { id: TeamLeaveFilter; label: string }[] = [
  { id: "PENDING", label: "Waiting for you" },
  { id: "APPROVED", label: "Approved" },
  { id: "REJECTED", label: "Rejected" },
  { id: "ALL", label: "All" },
];
const TONE: Record<string, Tone> = { PENDING: "warning", APPROVED: "success", REJECTED: "danger" };
const day = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
const span = (l: TeamLeave) => `${day(l.startDate)}${l.days > 1 ? ` to ${day(l.endDate)}` : ""} · ${l.days} ${l.days === 1 ? "day" : "days"}`;

export default function TeamLeavesPage() {
  const toast = useToast();
  const [filter, setFilter] = useState<TeamLeaveFilter>("PENDING");
  const [rows, setRows] = useState<TeamLeave[] | null>(null);
  const [error, setError] = useState("");
  const [deciding, setDeciding] = useState<{ leave: TeamLeave; decision: "approve" | "reject" } | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const wholeCompany = can("leave:view:any");

  const load = useCallback((f: TeamLeaveFilter) => {
    setError("");
    return listTeamLeaves(f).then(setRows).catch(e => setError(leaveErrorMessage(e, "We couldn't load the leave requests.")));
  }, []);
  useEffect(() => { setRows(null); void load(filter); }, [filter, load]);

  const open = (leave: TeamLeave, decision: "approve" | "reject") => { setDeciding({ leave, decision }); setNote(""); setFormError(""); };

  const submit = async () => {
    if (!deciding) return;
    if (deciding.decision === "reject" && note.trim().length < 3) { setFormError("Please give a reason, so the employee knows why."); return; }
    setBusy(true); setFormError("");
    try {
      await decideLeave(deciding.leave.id, deciding.decision, note);
      toast.success(deciding.decision === "approve" ? "Leave approved." : "Leave rejected.");
      setDeciding(null);
      await load(filter);
    } catch (e) {
      setFormError(leaveErrorMessage(e, "We couldn't save the decision."));
      void load(filter);
    } finally { setBusy(false); }
  };

  return (
    <div>
      <PageHeader title={wholeCompany ? "Leave decisions" : "Team leave"}
        subtitle={`${wholeCompany ? "Requests from everyone in the company." : "Requests from the people on the teams you lead."} Each request shows who else is off on the same days.`} />
      <Tabs tabs={FILTERS} value={filter} onChange={setFilter} label="Leave status" />
      <div style={{ marginTop: 16 }}>
        {error ? <ErrorState message={error} onRetry={() => void load(filter)} />
          : rows === null ? <SkeletonList rows={3} />
          : rows.length === 0 ? <EmptyState title={filter === "PENDING" ? "Nothing is waiting for you" : "No requests here"}
              text={filter === "PENDING" ? "New requests will appear here and in your notifications." : "Requests with this status will show up here."} />
          : (
            <div style={{ display: "grid", gap: 12 }}>
              {rows.map(r => (
                <Card key={r.id} className="tl-card">
                  <div className="tl-main">
                    <div className="tl-who">
                      <b>{r.employeeName}</b>
                      {r.department && <span> · {r.department}</span>}
                      <Badge tone={TONE[r.status] ?? "neutral"}>{r.status === "PENDING" ? "Pending" : r.status === "APPROVED" ? "Approved" : "Rejected"}</Badge>
                    </div>
                    <div className="tl-dates">{span(r)}</div>
                    {r.reason && <div className="tl-note">“{r.reason}”</div>}
                    {r.clashesWith.length > 0 && <div className="tl-clash">Also off on these days: {r.clashesWith.join(", ")}</div>}
                    {r.status !== "PENDING" && (
                      <div className="tl-note">
                        {r.status === "APPROVED" ? "Approved" : "Rejected"}{r.decidedByName ? ` by ${r.decidedByName}` : ""}
                        {r.decidedAt ? ` on ${new Date(r.decidedAt).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}` : ""}
                        {r.decisionNote ? `: “${r.decisionNote}”` : ""}
                      </div>
                    )}
                  </div>
                  {r.canDecide && (
                    <div className="tl-actions">
                      <Button variant="primary" size="sm" onClick={() => open(r, "approve")}>Approve</Button>
                      <Button variant="secondary" size="sm" onClick={() => open(r, "reject")}>Reject</Button>
                    </div>
                  )}
                </Card>
              ))}
            </div>
          )}
      </div>

      <Modal open={!!deciding} onClose={() => setDeciding(null)} dismissible={!busy}
        title={deciding ? `${deciding.decision === "approve" ? "Approve" : "Reject"} leave for ${deciding.leave.employeeName}` : ""}
        description={deciding ? span(deciding.leave) : undefined}
        footer={<>
          <Button variant="secondary" disabled={busy} onClick={() => setDeciding(null)}>Cancel</Button>
          <Button variant={deciding?.decision === "reject" ? "danger" : "primary"} loading={busy} onClick={submit}>{deciding?.decision === "approve" ? "Approve" : "Reject"}</Button>
        </>}>
        {deciding && (
          <div style={{ display: "grid", gap: 12 }}>
            {deciding.leave.clashesWith.length > 0 && <Alert tone="warning">Also off on these days: {deciding.leave.clashesWith.join(", ")}</Alert>}
            {formError && <Alert tone="error">{formError}</Alert>}
            <Field label={deciding.decision === "reject" ? "Reason" : "Note (optional)"} required={deciding.decision === "reject"}
              hint={`${note.length} / 300 · The employee is notified in the app and by email, with your note.`}>
              <Textarea rows={3} maxLength={300} value={note} onChange={e => setNote(e.target.value)} data-autofocus
                placeholder={deciding.decision === "reject" ? "Tell them why, for example: release week, please pick other dates." : "Anything the employee should know"} />
            </Field>
          </div>
        )}
      </Modal>
    </div>
  );
}

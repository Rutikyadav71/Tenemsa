import { useEffect, useState } from "react";
import { decideLeave, leaveErrorMessage, listTeamLeaves, type TeamLeaveFilter } from "../services/teamLeaveService";
import { can } from "../../../shared/lib/roles";
import type { TeamLeave } from "../../../shared/types/models";

const FILTERS: { key: TeamLeaveFilter; label: string }[] = [
  { key: "PENDING", label: "Waiting for you" },
  { key: "APPROVED", label: "Approved" },
  { key: "REJECTED", label: "Rejected" },
  { key: "ALL", label: "All" },
];

const COLOR: Record<string, string> = { PENDING: "#f59e0b", APPROVED: "#22c55e", REJECTED: "#ef4444" };
const day = (d: string) => new Date(d + "T00:00:00").toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });

export default function TeamLeavesPage() {
  const [filter, setFilter] = useState<TeamLeaveFilter>("PENDING");
  const [rows, setRows] = useState<TeamLeave[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [deciding, setDeciding] = useState<{ leave: TeamLeave; decision: "approve" | "reject" } | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const wholeCompany = can("leave:view:any");

  const load = (f = filter) => {
    setLoading(true); setError("");
    return listTeamLeaves(f).then(setRows).catch(e => setError(leaveErrorMessage(e, "Could not load leave requests."))).finally(() => setLoading(false));
  };
  useEffect(() => { load(filter); }, [filter]);

  const open = (leave: TeamLeave, decision: "approve" | "reject") => { setDeciding({ leave, decision }); setNote(""); setFormError(""); };
  const close = () => { if (!busy) setDeciding(null); };

  const submit = async () => {
    if (!deciding) return;
    if (deciding.decision === "reject" && note.trim().length < 3) { setFormError("Please give a reason, so the employee knows why."); return; }
    setBusy(true); setFormError("");
    try {
      await decideLeave(deciding.leave.id, deciding.decision, note);
      setDeciding(null);
      await load();
    } catch (e: any) {
      setFormError(leaveErrorMessage(e, "Could not save the decision."));
      load();
    } finally { setBusy(false); }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">{wholeCompany ? "Leave decisions" : "Team leave"}</div>
          <div className="page-sub">
            {wholeCompany ? "Requests from everyone in the company." : "Requests from the people on the teams you lead."}
            {" "}Each request shows who else is off on the same days.
          </div>
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
        {FILTERS.map(f => (
          <button key={f.key} className={`btn-ems ${filter === f.key ? "btn-primary-ems" : "btn-secondary-ems"}`} style={{ fontSize: 12 }}
                  onClick={() => setFilter(f.key)}>{f.label}</button>
        ))}
      </div>

      {error && <div className="alert-ems alert-error">{error}</div>}

      <div className="ems-card" style={{ padding: 0 }}>
        {loading && <div style={{ padding: 16, color: "var(--text-3)" }}>Loading...</div>}
        {!loading && rows.length === 0 && (
          <div style={{ padding: 28, color: "var(--text-3)", textAlign: "center" }}>
            {filter === "PENDING" ? "Nothing is waiting for you. New requests will appear here and in your notifications." : "No requests here."}
          </div>
        )}
        {!loading && rows.map(r => (
          <div key={r.id} style={{ padding: "14px 16px", borderBottom: "1px solid var(--border)" }}>
            <div style={{ display: "flex", gap: 12, alignItems: "flex-start", flexWrap: "wrap" }}>
              <div style={{ flex: 1, minWidth: 240 }}>
                <div style={{ color: "var(--text-1)", fontWeight: 600 }}>
                  {r.employeeName}
                  {r.department && <span style={{ color: "var(--text-3)", fontWeight: 400, fontSize: 12 }}> · {r.department}</span>}
                </div>
                <div style={{ fontSize: 13, color: "var(--text-2)", marginTop: 2 }}>
                  {day(r.startDate)}{r.days > 1 ? ` to ${day(r.endDate)}` : ""} · {r.days} {r.days === 1 ? "day" : "days"}
                </div>
                {r.reason && <div style={{ fontSize: 13, color: "var(--text-3)", marginTop: 4 }}>"{r.reason}"</div>}
                {r.clashesWith.length > 0 && (
                  <div style={{ fontSize: 12, color: "#f59e0b", marginTop: 6 }}>
                    Also off on these days: {r.clashesWith.join(", ")}
                  </div>
                )}
                {r.status !== "PENDING" && (
                  <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 6 }}>
                    {r.status === "APPROVED" ? "Approved" : "Rejected"}{r.decidedByName ? ` by ${r.decidedByName}` : ""}
                    {r.decidedAt ? ` on ${new Date(r.decidedAt).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}` : ""}
                    {r.decisionNote ? `: "${r.decisionNote}"` : ""}
                  </div>
                )}
              </div>
              <span style={{ fontSize: 11, fontWeight: 700, color: COLOR[r.status] }}>{r.status}</span>
              {r.canDecide && (
                <div style={{ display: "flex", gap: 8 }}>
                  <button className="btn-ems btn-primary-ems" style={{ fontSize: 12 }} onClick={() => open(r, "approve")}>Approve</button>
                  <button className="btn-ems btn-secondary-ems" style={{ fontSize: 12 }} onClick={() => open(r, "reject")}>Reject</button>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      {deciding && (
        <div className="ems-modal-backdrop" onClick={close}>
          <div className="ems-modal" style={{ width: 460, maxWidth: "95vw" }} onClick={e => e.stopPropagation()}>
            <div className="modal-title">
              {deciding.decision === "approve" ? "Approve" : "Reject"} leave for {deciding.leave.employeeName}
            </div>
            <div style={{ fontSize: 13, color: "var(--text-2)", marginBottom: 10 }}>
              {day(deciding.leave.startDate)}{deciding.leave.days > 1 ? ` to ${day(deciding.leave.endDate)}` : ""} · {deciding.leave.days} {deciding.leave.days === 1 ? "day" : "days"}
            </div>
            {deciding.leave.clashesWith.length > 0 && (
              <div className="alert-ems alert-error">Also off on these days: {deciding.leave.clashesWith.join(", ")}</div>
            )}
            {formError && <div className="alert-ems alert-error">{formError}</div>}
            <div className="form-group">
              <label className="form-label">{deciding.decision === "reject" ? "Reason (required)" : "Note (optional)"}</label>
              <textarea className="form-control-ems" rows={3} maxLength={300} value={note} onChange={e => setNote(e.target.value)}
                        placeholder={deciding.decision === "reject" ? "Tell them why, for example: release week, please pick other dates." : "Anything the employee should know"} />
              <div style={{ fontSize: 11, color: "var(--text-3)", textAlign: "right" }}>{note.length} / 300</div>
            </div>
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
              <button className="btn-ems btn-secondary-ems" disabled={busy} onClick={close}>Cancel</button>
              <button className="btn-ems btn-primary-ems" disabled={busy} onClick={submit}>
                {busy ? "Saving..." : deciding.decision === "approve" ? "Approve" : "Reject"}
              </button>
            </div>
            <div style={{ fontSize: 11, color: "var(--text-3)", marginTop: 8 }}>The employee is notified in the app and by email, with your note.</div>
          </div>
        </div>
      )}
    </div>
  );
}

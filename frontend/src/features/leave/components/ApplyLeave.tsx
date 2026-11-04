import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { LuCalendarX2 } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import type { Leave, LeaveStatus } from "../../../shared/types/models";
import { Alert, Badge, Button, Card, CardHeader, EmptyState, ErrorState, Field, Input, PageHeader, SkeletonList, Textarea, useToast, type Tone } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";

const TONE: Record<LeaveStatus, Tone> = { PENDING: "warning", APPROVED: "success", REJECTED: "danger" };
const LABEL: Record<LeaveStatus, string> = { PENDING: "Pending", APPROVED: "Approved", REJECTED: "Rejected" };
const day = (s: string) => new Date(`${s.slice(0, 10)}T00:00:00`);
const dayCount = (a: string, b: string) => Math.round((day(b).getTime() - day(a).getTime()) / 86_400_000) + 1;
const pretty = (s: string) => day(s).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

/** Apply for leave and see your own history. HR and the Owner may also file for another person's ID. */
export default function ApplyLeave() {
  const toast = useToast();
  const myEmpId = localStorage.getItem("empId") || "";
  const canApplyForOthers = ["OWNER", "HR"].includes(localStorage.getItem("userRole") || "");
  const today = new Date().toISOString().slice(0, 10);

  const [form, setForm] = useState({ empId: myEmpId, startDate: "", endDate: "", reason: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [history, setHistory] = useState<Leave[]>([]);
  const [loading, setLoading] = useState(!!myEmpId);
  const [failed, setFailed] = useState(false);

  const loadHistory = useCallback(async () => {
    if (!myEmpId) return;
    setFailed(false);
    try { setHistory((await axiosInstance.get<Leave[]>(`/api/leaves/employee/${myEmpId}`)).data || []); }
    catch { setFailed(true); }
    finally { setLoading(false); }
  }, [myEmpId]);
  useEffect(() => { void loadHistory(); }, [loadHistory]);

  const days = form.startDate && form.endDate && form.endDate >= form.startDate ? dayCount(form.startDate, form.endDate) : 0;
  const overlap = useMemo(() => !days ? null : history.find(l => l.status !== "REJECTED" && l.startDate <= form.endDate && l.endDate >= form.startDate) ?? null, [history, days, form.startDate, form.endDate]);
  const sorted = useMemo(() => [...history].sort((a, b) => b.startDate.localeCompare(a.startDate)), [history]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError("");
    const next = {
      empId: /^\d+$/.test(form.empId.trim()) ? "" : "Enter an employee ID.",
      startDate: form.startDate ? "" : "Choose a start date.",
      endDate: form.endDate ? "" : "Choose an end date.",
      reason: v.minLength(form.reason, 3, "The reason"),
    };
    if (form.startDate && form.endDate) { const r = v.dateRange(form.startDate, form.endDate); if (r) next.endDate = r; }
    setErrors(next);
    if (v.hasErrors(next)) return;
    setSaving(true);
    try {
      await axiosInstance.post("/api/leaves", { ...form, reason: form.reason.trim() });
      toast.success("Leave request sent. You'll be notified when it's decided.");
      setForm(f => ({ ...f, startDate: "", endDate: "", reason: "" }));
      void loadHistory();
    } catch (err) { setFormError(errorMessage(err, "We couldn't send your leave request. Please try again.")); }
    finally { setSaving(false); }
  };

  return (
    <div>
      <PageHeader title="Apply for leave" subtitle="Send a request to the person who approves your leave." />
      <div style={{ display: "grid", gridTemplateColumns: "minmax(0,1fr) minmax(0,1.2fr)", gap: 16, alignItems: "start" }} className="lv-grid">
        <Card pad={false}>
          <CardHeader title="New request" />
          <form onSubmit={submit} noValidate style={{ display: "grid", gap: 16, padding: 20 }}>
            {formError && <Alert tone="error">{formError}</Alert>}
            {canApplyForOthers && (
              <Field label="Employee ID" required error={errors.empId} hint="Leave it as yours, or enter someone else's ID to file for them.">
                <Input value={form.empId} inputMode="numeric" onChange={e => setForm({ ...form, empId: e.target.value })} />
              </Field>
            )}
            <div className="tn-form-grid">
              <Field label="From" required error={errors.startDate}>
                <Input type="date" min={today} value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value, endDate: form.endDate && form.endDate < e.target.value ? e.target.value : form.endDate })} />
              </Field>
              <Field label="To" required error={errors.endDate}>
                <Input type="date" min={form.startDate || today} value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} />
              </Field>
            </div>
            {days > 0 && <div style={{ fontSize: 13, color: "var(--text-2)" }}><b>{days}</b> calendar day{days === 1 ? "" : "s"} requested.</div>}
            {overlap && <Alert tone="warning">This overlaps a {LABEL[overlap.status].toLowerCase()} request ({pretty(overlap.startDate)} to {pretty(overlap.endDate)}). You can still send it.</Alert>}
            <Field label="Reason" required error={errors.reason}>
              <Textarea rows={4} value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} placeholder="A short note for the person approving" maxLength={500} />
            </Field>
            <Button type="submit" variant="primary" block loading={saving}>{saving ? "Sending…" : "Send request"}</Button>
          </form>
        </Card>

        <Card pad={false}>
          <CardHeader title="Your leave history" subtitle={history.length ? `${history.filter(l => l.status === "PENDING").length} waiting for a decision` : undefined} />
          {!myEmpId ? <EmptyState title="No history to show" text="Your account has no employee ID, so there is no personal leave history." />
            : loading ? <div style={{ padding: 20 }}><SkeletonList rows={3} /></div>
            : failed ? <ErrorState message="Your leave history couldn't be loaded." onRetry={() => { setLoading(true); void loadHistory(); }} />
            : sorted.length === 0 ? <EmptyState icon={<LuCalendarX2 size={24} />} title="No leave yet" text="Requests you send will appear here with their status." />
            : <ul className="tn-list" style={{ maxHeight: 460, overflowY: "auto" }}>
                {sorted.map(l => (
                  <li key={l.id} style={{ padding: "12px 20px", display: "flex", gap: 12, alignItems: "flex-start" }}>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, color: "var(--text-1)", fontSize: 13.5 }}>{pretty(l.startDate)} to {pretty(l.endDate)} <span style={{ fontWeight: 400, color: "var(--text-3)" }}>· {dayCount(l.startDate, l.endDate)} day{dayCount(l.startDate, l.endDate) === 1 ? "" : "s"}</span></div>
                      {l.reason && <div style={{ fontSize: 12.5, color: "var(--text-3)", marginTop: 2, overflowWrap: "anywhere" }}>{l.reason.length > 80 ? `${l.reason.slice(0, 80)}…` : l.reason}</div>}
                    </div>
                    <Badge tone={TONE[l.status]} dot>{LABEL[l.status]}</Badge>
                  </li>
                ))}
              </ul>}
        </Card>
      </div>
      <style>{`@media (max-width:900px){.lv-grid{grid-template-columns:1fr !important}}`}</style>
    </div>
  );
}

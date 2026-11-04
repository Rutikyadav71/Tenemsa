import { useEffect, useState, useCallback } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { getEmployees } from "../../employees/services/EmployeeService";
import type { Attendance, AttendanceStatus, User } from "../../../shared/types/models";
import { Badge, Button, Card, CardHeader, EmptyState, Field, Input, Modal, PageHeader, Select, Skeleton, useToast, type Tone } from "../../../shared/ui";
import { LuChevronLeft, LuChevronRight, LuLogIn, LuLogOut, LuPencil } from "react-icons/lu";
import "./attendance.css";

// WEEKEND/HOLIDAY are synthetic, frontend-only calendar states — never
// stored on the backend, just computed client-side for display.
type DisplayStatus = AttendanceStatus | "WEEKEND" | "HOLIDAY";

interface AttendanceSummary {
  empId: number; month: number; year: number; present: number; late: number;
  absent: number; halfDay: number; onLeave: number; totalWorkHours: number; attendanceRate: number;
}

interface MarkForm { status: AttendanceStatus; checkIn: string; checkOut: string; notes: string; }

const TONE: Record<DisplayStatus, Tone> = { PRESENT: "success", ABSENT: "danger", HALF_DAY: "warning", LATE: "warning", ON_LEAVE: "info", HOLIDAY: "accent", WEEKEND: "neutral" };
const LABEL: Record<DisplayStatus, string> = { PRESENT: "Present", ABSENT: "Absent", HALF_DAY: "Half day", LATE: "Late", ON_LEAVE: "On leave", HOLIDAY: "Holiday", WEEKEND: "Weekend" };
const MARKABLE: AttendanceStatus[] = ["PRESENT", "LATE", "HALF_DAY", "ABSENT", "ON_LEAVE"];
const MONTHS = ["January","February","March","April","May","June","July","August","September","October","November","December"];
const DAYS   = ["Sun","Mon","Tue","Wed","Thu","Fri","Sat"];

const StatusBadge = ({ status }: { status: DisplayStatus }) => <Badge tone={TONE[status] ?? "neutral"} dot>{LABEL[status] ?? status}</Badge>;

interface AttendancePageProps { isAdmin: boolean; }

export default function AttendancePage({ isAdmin }: AttendancePageProps) {
  const toast = useToast();
  const empId     = Number(localStorage.getItem("empId")) || 0;
  const today     = new Date();
  const [viewDate, setViewDate] = useState(new Date(today.getFullYear(), today.getMonth(), 1));
  const [records,  setRecords]  = useState<Attendance[]>([]); // calendar records
  const [, setAllRecs]  = useState<Attendance[]>([]); // all employees for admin day view
  const [summary,  setSummary]  = useState<AttendanceSummary | null>(null);
  const [todayRec, setTodayRec] = useState<Attendance | null>(null);
  const [selDay,   setSelDay]   = useState<string | null>(null);
  const [loading,  setLoading]  = useState(false);
  const [saving,   setSaving]   = useState(false);
  const [marking,  setMarking]  = useState(false);
  const [employees,setEmployees]= useState<User[]>([]);
  // Admin mark modal
  const [markModal, setMarkModal] = useState<{empId: number; date: string} | null>(null);
  const [markForm,  setMarkForm]  = useState<MarkForm>({ status:"PRESENT", checkIn:"09:00", checkOut:"18:00", notes:"" });
  // Clock
  const [clock, setClock] = useState(new Date());

  useEffect(() => { const iv = setInterval(() => setClock(new Date()), 1000); return () => clearInterval(iv); }, []);

  const fetchRecords = useCallback(async () => {
    setLoading(true);
    const y = viewDate.getFullYear(), m = viewDate.getMonth() + 1;
    const start = `${y}-${String(m).padStart(2,"0")}-01`;
    const end   = `${y}-${String(m).padStart(2,"0")}-${String(new Date(y,m,0).getDate()).padStart(2,"0")}`;
    try {
      if (isAdmin) {
        const [rangeRes, empRes] = await Promise.all([
          axiosInstance.get<Attendance[]>(`/api/attendance/range?start=${start}&end=${end}`),
          getEmployees(),
        ]);
        setRecords(rangeRes.data || []);
        setEmployees(empRes.data || []);
      } else {
        const [recRes, sumRes, todayRes] = await Promise.all([
          axiosInstance.get<Attendance[]>(`/api/attendance/employee/${empId}?start=${start}&end=${end}`),
          axiosInstance.get<AttendanceSummary>(`/api/attendance/summary/${empId}?month=${m}&year=${y}`),
          axiosInstance.get<Attendance | null>(`/api/attendance/today/${empId}`),
        ]);
        setRecords(recRes.data || []);
        setSummary(sumRes.data);
        setTodayRec(todayRes.data);
      }
    } catch { toast.error("We couldn't load attendance. Please try again."); }
    finally { setLoading(false); }
  }, [viewDate, empId, isAdmin]);

  useEffect(() => { fetchRecords(); }, [fetchRecords]);

  const handleCheckIn = async () => {
    setMarking(true);
    try {
      const res = await axiosInstance.post<Attendance>(`/api/attendance/checkin/${empId}`);
      setTodayRec(res.data); fetchRecords();
    } catch (e: any) { toast.fail(e, "We couldn't check you in. Please try again."); }
    finally { setMarking(false); }
  };

  const handleCheckOut = async () => {
    setMarking(true);
    try {
      const res = await axiosInstance.post<Attendance>(`/api/attendance/checkout/${empId}`);
      setTodayRec(res.data); fetchRecords();
    } catch (e: any) { toast.fail(e, "We couldn't check you out. Please try again."); }
    finally { setMarking(false); }
  };

  const handleAdminMark = async () => {
    if (!markModal) return;
    setSaving(true);
    try {
      await axiosInstance.post("/api/attendance/mark", {
        empId: markModal.empId,
        date:  markModal.date,
        status: markForm.status,
        checkIn:  markForm.checkIn || null,
        checkOut: markForm.checkOut || null,
        notes: markForm.notes,
      });
      setMarkModal(null);
      toast.success("Attendance saved.");
      fetchRecords();
    } catch (e: any) { toast.fail(e, "We couldn't save that attendance record."); }
    finally { setSaving(false); }
  };

  // ── Build calendar ────────────────────────────────────────────────────────
  const y = viewDate.getFullYear(), mo = viewDate.getMonth();
  const firstDay = new Date(y, mo, 1).getDay();
  const daysInMonth = new Date(y, mo + 1, 0).getDate();

  const recordMap: Record<string, Attendance> = {};
  records.forEach(r => {
    const key = isAdmin ? `${r.empId}-${r.date}` : r.date;
    recordMap[key] = r;
  });

  const prevMonth = () => setViewDate(new Date(y, mo - 1, 1));
  const nextMonth = () => setViewDate(new Date(y, mo + 1, 1));

  const getDayStatus = (day: number) => {
    const dateStr = `${y}-${String(mo+1).padStart(2,"0")}-${String(day).padStart(2,"0")}`;
    const d = new Date(y, mo, day);
    const isWeekend = d.getDay() === 0 || d.getDay() === 6;
    if (isAdmin) {
      // show count of employees present
      const dayRecs = records.filter(r => r.date === dateStr);
      return { dayRecs, dateStr, isWeekend };
    }
    const rec = recordMap[dateStr];
    if (isWeekend && !rec) return { status:"WEEKEND", dateStr };
    if (!rec) return { status: null, dateStr };
    return { status: rec.status, dateStr, rec };
  };

  const isFuture = (day: number) => new Date(y, mo, day) > today;
  const isToday  = (day: number) => new Date(y, mo, day).toDateString() === today.toDateString();

  // Get selected day records (admin)
  const selectedDayRecs = selDay ? records.filter(r => r.date === selDay) : [];
  const empMap: Record<number, User> = {};
  employees.forEach(e => { if (e.empId != null) empMap[e.empId] = e; });

  const dayLabel = (info: ReturnType<typeof getDayStatus>, day: number) => {
    const base = new Date(y, mo, day).toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" });
    return !isAdmin && "status" in info && info.status ? `${base}: ${LABEL[info.status as DisplayStatus]}` : base;
  };

  return (
    <div>
      <PageHeader title="Attendance" subtitle={isAdmin ? "Track and manage team attendance." : "Your attendance calendar."}
        actions={!isAdmin ? (
          <div className="at-clock">
            <div><b>{clock.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", second: "2-digit" })}</b>
              <small>{today.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "short" })}</small></div>
            {(!todayRec || !todayRec.checkIn)
              ? <Button variant="primary" icon={<LuLogIn size={16} />} loading={marking} onClick={handleCheckIn}>Check in</Button>
              : !todayRec.checkOut
                ? <Button variant="danger" icon={<LuLogOut size={16} />} loading={marking} onClick={handleCheckOut}>Check out</Button>
                : <Badge tone="success">Done for today · {todayRec.workHours}h logged</Badge>}
          </div>
        ) : undefined} />

      <div className="at-grid">
        <Card pad={false}>
          <div className="at-nav">
            <Button size="sm" aria-label="Previous month" icon={<LuChevronLeft size={16} />} onClick={prevMonth} />
            <h2 aria-live="polite">{MONTHS[mo]} {y}</h2>
            <Button size="sm" aria-label="Next month" icon={<LuChevronRight size={16} />} onClick={nextMonth} />
          </div>
          <div className="at-cal">
            <div className="at-days" aria-hidden="true">{DAYS.map(d => <span key={d}>{d}</span>)}</div>
            {loading && records.length === 0 ? <Skeleton height={260} /> : (
              <div className="at-cells">
                {Array(firstDay).fill(null).map((_, i) => <span key={`e${i}`} />)}
                {Array.from({ length: daysInMonth }, (_, i) => i + 1).map(day => {
                  const info = getDayStatus(day);
                  const future = isFuture(day);
                  const todayDay = isToday(day);
                  const st = !isAdmin && "status" in info && info.status ? (info.status as DisplayStatus) : null;
                  const present = isAdmin ? records.filter(r => r.date === info.dateStr && (r.status === "PRESENT" || r.status === "LATE")).length : 0;
                  return (
                    <button key={day} disabled={future} aria-label={dayLabel(info, day)} aria-pressed={selDay === info.dateStr}
                      className={`at-cell${st ? ` s-${st}` : ""}${todayDay ? " today" : ""}${selDay === info.dateStr ? " sel" : ""}`}
                      onClick={() => { setSelDay(info.dateStr); if (isAdmin) setAllRecs(selectedDayRecs); }}>
                      <b>{day}</b>
                      {st && st !== "WEEKEND" && <small>{LABEL[st].slice(0, 4)}</small>}
                      {isAdmin && present > 0 && <small>{present}/{employees.length}</small>}
                    </button>
                  );
                })}
              </div>
            )}
            <ul className="at-legend" aria-label="Legend">
              {(Object.keys(LABEL) as DisplayStatus[]).filter(k => k !== "WEEKEND").map(k => <li key={k}><span className={`at-dot s-${k}`} />{LABEL[k]}</li>)}
            </ul>
          </div>
        </Card>

        <div className="at-side">
          {!isAdmin && (
            <Card>
              <CardHeader title="Today" />
              {todayRec ? (
                <div className="at-kv">
                  <div><span>Status</span><StatusBadge status={todayRec.status} /></div>
                  {todayRec.checkIn && <div><span>Checked in</span><b>{todayRec.checkIn}</b></div>}
                  {todayRec.checkOut && <div><span>Checked out</span><b>{todayRec.checkOut}</b></div>}
                  {todayRec.workHours != null && <div><span>Hours logged</span><b>{todayRec.workHours}h</b></div>}
                </div>
              ) : <p className="at-muted">Not checked in yet.</p>}
            </Card>
          )}

          {!isAdmin && summary && (
            <Card>
              <CardHeader title={`${MONTHS[mo]} summary`} />
              <div className="at-stats">
                {[["Present", summary.present], ["Absent", summary.absent], ["Half day", summary.halfDay], ["Late", summary.late], ["On leave", summary.onLeave], ["Work hours", `${summary.totalWorkHours}h`]].map(([l, v]) => (
                  <div key={l as string}><small>{l}</small><b>{v}</b></div>
                ))}
              </div>
              <div className="at-rate">
                <small>Attendance rate</small>
                <div><span role="progressbar" aria-valuenow={summary.attendanceRate} aria-valuemin={0} aria-valuemax={100} aria-label="Attendance rate"><i style={{ width: `${summary.attendanceRate}%` }} /></span><b>{summary.attendanceRate}%</b></div>
              </div>
            </Card>
          )}

          {isAdmin && selDay && (
            <Card>
              <CardHeader title={new Date(selDay + "T00:00").toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long" })} />
              <ul className="at-people">
                {employees.map(emp => {
                  const rec = records.find(r => r.empId === emp.empId && r.date === selDay);
                  return (
                    <li key={emp.empId}>
                      <span className="at-who"><b>{emp.name}</b>{rec && <small>{rec.checkIn || "--"} → {rec.checkOut || "--"}</small>}</span>
                      <StatusBadge status={rec ? rec.status : "ABSENT"} />
                      <Button size="sm" aria-label={`Edit attendance for ${emp.name}`} icon={<LuPencil size={14} />}
                        onClick={() => { setMarkModal({ empId: emp.empId!, date: selDay }); setMarkForm(rec ? { status: rec.status, checkIn: rec.checkIn || "09:00", checkOut: rec.checkOut || "18:00", notes: rec.notes || "" } : { status: "PRESENT", checkIn: "09:00", checkOut: "18:00", notes: "" }); }} />
                    </li>
                  );
                })}
              </ul>
              {employees.length === 0 && <EmptyState title="No employees found" text="Employees will be listed here." />}
            </Card>
          )}

          {isAdmin && !selDay && (
            <Card>
              <CardHeader title="Quick view" subtitle="Choose a date on the calendar to view or mark attendance for that day." />
              <div className="at-stats">
                <div><small>Present today</small><b>{records.filter(r => r.date === today.toISOString().slice(0, 10) && (r.status === "PRESENT" || r.status === "LATE")).length}/{employees.length}</b></div>
                <div><small>This month</small><b>{records.length} entries</b></div>
              </div>
            </Card>
          )}
        </div>
      </div>

      <Modal open={!!markModal} onClose={() => setMarkModal(null)} dismissible={!saving} size="sm" title="Mark attendance"
        description={markModal ? `${empMap[markModal.empId]?.name || `Employee #${markModal.empId}`} · ${markModal.date}` : undefined}
        footer={<><Button variant="secondary" disabled={saving} onClick={() => setMarkModal(null)}>Cancel</Button><Button variant="primary" loading={saving} onClick={handleAdminMark}>Save attendance</Button></>}>
        <div style={{ display: "grid", gap: 12 }}>
          <Field label="Status">
            <Select value={markForm.status} onChange={e => setMarkForm({ ...markForm, status: e.target.value as AttendanceStatus })} data-autofocus>
              {MARKABLE.map(s => <option key={s} value={s}>{LABEL[s]}</option>)}
            </Select>
          </Field>
          <div className="tn-form-grid">
            <Field label="Check in"><Input type="time" value={markForm.checkIn} onChange={e => setMarkForm({ ...markForm, checkIn: e.target.value })} /></Field>
            <Field label="Check out"><Input type="time" value={markForm.checkOut} onChange={e => setMarkForm({ ...markForm, checkOut: e.target.value })} /></Field>
          </div>
          <Field label="Notes"><Input value={markForm.notes} placeholder="Optional notes" onChange={e => setMarkForm({ ...markForm, notes: e.target.value })} /></Field>
        </div>
      </Modal>
    </div>
  );
}

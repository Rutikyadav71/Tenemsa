import { useEffect, useState, useCallback } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { getEmployees } from "../../employees/services/EmployeeService";
import type { Attendance, AttendanceStatus, User } from "../../../shared/types/models";

// WEEKEND/HOLIDAY are synthetic, frontend-only calendar states — never
// stored on the backend, just computed client-side for display.
type DisplayStatus = AttendanceStatus | "WEEKEND" | "HOLIDAY";

interface AttendanceSummary {
  empId: number; month: number; year: number; present: number; late: number;
  absent: number; halfDay: number; onLeave: number; totalWorkHours: number; attendanceRate: number;
}

interface MarkForm { status: AttendanceStatus; checkIn: string; checkOut: string; notes: string; }

// ── Icons ──────────────────────────────────────────────────────────────────
const CheckInIcon  = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><polyline points="9 12 11 14 15 10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const CheckOutIcon = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="1.8"/><polyline points="12 6 12 12 16 14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const CalIcon      = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const ChevLeft     = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const ChevRight    = () => <svg width="16" height="16" fill="none" viewBox="0 0 24 24"><path d="M9 18l6-6-6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;

const STATUS_COLORS: Record<DisplayStatus, {bg:string; text:string; dot:string; label:string}> = {
  PRESENT:  { bg:"rgba(34,197,94,0.12)",  text:"#22c55e", dot:"#22c55e",  label:"Present"  },
  ABSENT:   { bg:"rgba(239,68,68,0.12)",  text:"#ef4444", dot:"#ef4444",  label:"Absent"   },
  HALF_DAY: { bg:"rgba(245,158,11,0.14)", text:"#f59e0b", dot:"#f59e0b",  label:"Half Day" },
  LATE:     { bg:"rgba(251,146,60,0.14)", text:"#fb923c", dot:"#fb923c",  label:"Late"     },
  ON_LEAVE: { bg:"rgba(99,102,241,0.14)", text:"#818cf8", dot:"#818cf8",  label:"On Leave" },
  HOLIDAY:  { bg:"rgba(45,212,191,0.14)", text:"#2dd4bf", dot:"#2dd4bf",  label:"Holiday"  },
  WEEKEND:  { bg:"rgba(100,116,139,0.1)", text:"#64748b", dot:"#94a3b8",  label:"Weekend"  },
};

const MONTHS = ["January","February","March","April","May","June","July","August","September","October","November","December"];
const DAYS   = ["Sun","Mon","Tue","Wed","Thu","Fri","Sat"];

function StatusBadge({ status }: { status: DisplayStatus }) {
  const s = STATUS_COLORS[status] || STATUS_COLORS.ABSENT;
  return (
    <span style={{ display:"inline-flex", alignItems:"center", gap:4, padding:"3px 9px",
      borderRadius:99, fontSize:11, fontWeight:600, background:s.bg, color:s.text,
      border:`1px solid ${s.dot}44` }}>
      <span style={{ width:5, height:5, borderRadius:"50%", background:s.dot }} />
      {s.label}
    </span>
  );
}

interface AttendancePageProps { isAdmin: boolean; }

export default function AttendancePage({ isAdmin }: AttendancePageProps) {
  const empId     = Number(localStorage.getItem("empId")) || 0;
  const today     = new Date();
  const [viewDate, setViewDate] = useState(new Date(today.getFullYear(), today.getMonth(), 1));
  const [records,  setRecords]  = useState<Attendance[]>([]); // calendar records
  const [, setAllRecs]  = useState<Attendance[]>([]); // all employees for admin day view
  const [summary,  setSummary]  = useState<AttendanceSummary | null>(null);
  const [todayRec, setTodayRec] = useState<Attendance | null>(null);
  const [selDay,   setSelDay]   = useState<string | null>(null);
  const [loading,  setLoading]  = useState(false);
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
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, [viewDate, empId, isAdmin]);

  useEffect(() => { fetchRecords(); }, [fetchRecords]);

  const handleCheckIn = async () => {
    setMarking(true);
    try {
      const res = await axiosInstance.post<Attendance>(`/api/attendance/checkin/${empId}`);
      setTodayRec(res.data); fetchRecords();
    } catch (e: any) { alert(e.response?.data || "Check-in failed"); }
    finally { setMarking(false); }
  };

  const handleCheckOut = async () => {
    setMarking(true);
    try {
      const res = await axiosInstance.post<Attendance>(`/api/attendance/checkout/${empId}`);
      setTodayRec(res.data); fetchRecords();
    } catch (e: any) { alert(e.response?.data || "Check-out failed"); }
    finally { setMarking(false); }
  };

  const handleAdminMark = async () => {
    if (!markModal) return;
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
      fetchRecords();
    } catch (e: any) { alert(e.response?.data?.message || "Failed to mark"); }
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

  return (
    <div className="fade-in">
      {/* Header */}
      <div className="page-header">
        <div>
          <div className="page-title">Attendance</div>
          <div className="page-sub">
            {isAdmin ? "Track and manage team attendance" : "Your attendance calendar"}
          </div>
        </div>
        {!isAdmin && (
          <div style={{ display:"flex", alignItems:"center", gap:10 }}>
            <div style={{ textAlign:"right" }}>
              <div style={{ fontSize:20, fontWeight:700, fontFamily:"'Outfit',sans-serif", color:"var(--text-1)" }}>
                {clock.toLocaleTimeString("en-IN", {hour:"2-digit", minute:"2-digit", second:"2-digit"})}
              </div>
              <div style={{ fontSize:11, color:"var(--text-3)" }}>
                {today.toLocaleDateString("en-IN",{weekday:"long",day:"numeric",month:"short"})}
              </div>
            </div>
            <div style={{ display:"flex", gap:8 }}>
              {(!todayRec || !todayRec.checkIn) ? (
                <button className="btn-ems btn-success-ems2" onClick={handleCheckIn} disabled={marking}>
                  <CheckInIcon /> Check In
                </button>
              ) : !todayRec.checkOut ? (
                <button className="btn-ems btn-danger-ems" onClick={handleCheckOut} disabled={marking}>
                  <CheckOutIcon /> Check Out
                </button>
              ) : (
                <div style={{ padding:"8px 14px", background:"var(--green-glow)", borderRadius:"var(--radius-sm)",
                  fontSize:12, color:"var(--green)", border:"1px solid rgba(34,197,94,0.3)" }}>
                  Done for today — {todayRec.workHours}h logged
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <div style={{ display:"grid", gridTemplateColumns: isAdmin ? "1fr 380px" : "1fr 300px", gap:16, alignItems:"start" }}>
        {/* ── Calendar ─────────────────────────────────────────────────────── */}
        <div className="ems-card" style={{ padding:0 }}>
          {/* Month nav */}
          <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between",
            padding:"16px 20px", borderBottom:"1px solid var(--border)" }}>
            <button className="btn-icon" onClick={prevMonth}><ChevLeft /></button>
            <div style={{ fontFamily:"'Outfit',sans-serif", fontSize:16, fontWeight:700, color:"var(--text-1)" }}>
              {MONTHS[mo]} {y}
            </div>
            <button className="btn-icon" onClick={nextMonth}><ChevRight /></button>
          </div>

          <div style={{ padding:16 }}>
            {/* Day labels */}
            <div style={{ display:"grid", gridTemplateColumns:"repeat(7,1fr)", gap:4, marginBottom:8 }}>
              {DAYS.map(d => (
                <div key={d} style={{ textAlign:"center", fontSize:11, fontWeight:600,
                  color:"var(--text-3)", padding:"4px 0", textTransform:"uppercase" }}>{d}</div>
              ))}
            </div>

            {/* Calendar cells */}
            <div style={{ display:"grid", gridTemplateColumns:"repeat(7,1fr)", gap:4 }}>
              {Array(firstDay).fill(null).map((_, i) => <div key={`e${i}`} />)}
              {Array.from({ length: daysInMonth }, (_, i) => i + 1).map(day => {
                const info = getDayStatus(day);
                const future = isFuture(day);
                const todayDay = isToday(day);
                const s = !isAdmin && info.status ? STATUS_COLORS[info.status] : null;

                return (
                  <div key={day}
                    onClick={() => { if (!future) { setSelDay(info.dateStr); if(isAdmin) setAllRecs(selectedDayRecs); }}}
                    style={{
                      borderRadius:10, padding:"6px 4px", textAlign:"center", cursor: future ? "default" : "pointer",
                      transition:"all 0.15s", position:"relative", minHeight:52,
                      background: todayDay ? "var(--accent-glow)" : selDay === info.dateStr ? "var(--bg-elevated)" : s ? s.bg : "transparent",
                      border: todayDay ? "1px solid var(--accent)" : selDay === info.dateStr ? "1px solid var(--border-bright)" : "1px solid transparent",
                      opacity: future ? 0.35 : 1,
                    }}
                    onMouseEnter={e => !future && (e.currentTarget.style.background = s ? s.bg : "var(--bg-elevated)")}
                    onMouseLeave={e => !future && (e.currentTarget.style.background = todayDay ? "var(--accent-glow)" : selDay === info.dateStr ? "var(--bg-elevated)" : s ? s.bg : "transparent")}
                  >
                    <div style={{ fontSize:13, fontWeight: todayDay ? 700 : 500,
                      color: todayDay ? "var(--accent-light)" : s ? s.text : "var(--text-2)" }}>
                      {day}
                    </div>
                    {/* Status indicator */}
                    {!isAdmin && info.status && info.status !== "WEEKEND" && (
                      <div style={{ fontSize:9, color: s?.text, marginTop:2, fontWeight:600 }}>
                        {s?.label?.slice(0,4)}
                      </div>
                    )}
                    {isAdmin && (() => {
                      const dayRecs = records.filter(r => r.date === info.dateStr);
                      const present = dayRecs.filter(r => r.status === "PRESENT" || r.status === "LATE").length;
                      return present > 0 ? (
                        <div style={{ fontSize:9, color:"var(--green)", marginTop:2, fontWeight:600 }}>
                          {present}/{employees.length}
                        </div>
                      ) : null;
                    })()}
                    {/* Today dot */}
                    {todayDay && (
                      <div style={{ width:4, height:4, borderRadius:"50%", background:"var(--accent)",
                        margin:"2px auto 0" }} />
                    )}
                  </div>
                );
              })}
            </div>

            {/* Legend */}
            <div style={{ display:"flex", flexWrap:"wrap", gap:10, marginTop:16, paddingTop:12, borderTop:"1px solid var(--border)" }}>
              {Object.entries(STATUS_COLORS).filter(([k])=>k!=="WEEKEND").map(([k, v]) => (
                <div key={k} style={{ display:"flex", alignItems:"center", gap:4, fontSize:10, color:"var(--text-3)" }}>
                  <span style={{ width:7, height:7, borderRadius:"50%", background:v.dot }} />
                  {v.label}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── Right panel ──────────────────────────────────────────────────── */}
        <div style={{ display:"flex", flexDirection:"column", gap:14 }}>
          {/* Today's status (employee) */}
          {!isAdmin && (
            <div className="ems-card">
              <div style={{ fontSize:13, fontWeight:600, color:"var(--text-1)", marginBottom:12 }}>Today's Status</div>
              {todayRec ? (
                <div style={{ display:"flex", flexDirection:"column", gap:8 }}>
                  <div style={{ display:"flex", justifyContent:"space-between" }}>
                    <span style={{ fontSize:12, color:"var(--text-3)" }}>Status</span>
                    <StatusBadge status={todayRec.status} />
                  </div>
                  {todayRec.checkIn && (
                    <div style={{ display:"flex", justifyContent:"space-between" }}>
                      <span style={{ fontSize:12, color:"var(--text-3)" }}>Check In</span>
                      <span style={{ fontSize:13, color:"var(--green)", fontWeight:600 }}>{todayRec.checkIn}</span>
                    </div>
                  )}
                  {todayRec.checkOut && (
                    <div style={{ display:"flex", justifyContent:"space-between" }}>
                      <span style={{ fontSize:12, color:"var(--text-3)" }}>Check Out</span>
                      <span style={{ fontSize:13, color:"var(--text-1)", fontWeight:600 }}>{todayRec.checkOut}</span>
                    </div>
                  )}
                  {todayRec.workHours != null && (
                    <div style={{ display:"flex", justifyContent:"space-between" }}>
                      <span style={{ fontSize:12, color:"var(--text-3)" }}>Hours Logged</span>
                      <span style={{ fontSize:13, color:"var(--accent-light)", fontWeight:700 }}>{todayRec.workHours}h</span>
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ fontSize:12, color:"var(--text-3)", textAlign:"center", padding:"10px 0" }}>
                  Not checked in yet
                </div>
              )}
            </div>
          )}

          {/* Monthly summary (employee) */}
          {!isAdmin && summary && (
            <div className="ems-card">
              <div style={{ fontSize:13, fontWeight:600, color:"var(--text-1)", marginBottom:12 }}>
                {MONTHS[mo]} Summary
              </div>
              <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:8 }}>
                {[
                  { label:"Present",   val:summary.present,   color:"var(--green)" },
                  { label:"Absent",    val:summary.absent,    color:"var(--red)" },
                  { label:"Half Day",  val:summary.halfDay,   color:"var(--amber)" },
                  { label:"Late",      val:summary.late,      color:"#fb923c" },
                  { label:"On Leave",  val:summary.onLeave,   color:"var(--accent-light)" },
                  { label:"Work Hrs",  val:`${summary.totalWorkHours}h`, color:"var(--teal)" },
                ].map(s => (
                  <div key={s.label} style={{ padding:"10px 12px", background:"var(--bg-elevated)",
                    borderRadius:8, border:"1px solid var(--border)" }}>
                    <div style={{ fontSize:10, color:"var(--text-3)", textTransform:"uppercase", letterSpacing:"0.5px" }}>{s.label}</div>
                    <div style={{ fontSize:18, fontWeight:700, fontFamily:"'Outfit',sans-serif", color:s.color, marginTop:2 }}>{s.val}</div>
                  </div>
                ))}
              </div>
              <div style={{ marginTop:12, padding:"10px 12px", background:"var(--accent-glow2)",
                borderRadius:8, border:"1px solid rgba(99,102,241,0.2)" }}>
                <div style={{ fontSize:11, color:"var(--text-3)" }}>Attendance Rate</div>
                <div style={{ display:"flex", alignItems:"center", gap:8, marginTop:4 }}>
                  <div style={{ flex:1, height:6, background:"var(--bg-elevated)", borderRadius:99, overflow:"hidden" }}>
                    <div style={{ height:"100%", width:`${summary.attendanceRate}%`,
                      background:"linear-gradient(90deg,var(--accent),var(--teal))", borderRadius:99 }} />
                  </div>
                  <span style={{ fontSize:13, fontWeight:700, color:"var(--accent-light)" }}>{summary.attendanceRate}%</span>
                </div>
              </div>
            </div>
          )}

          {/* Selected day details (admin) */}
          {isAdmin && selDay && (
            <div className="ems-card">
              <div style={{ fontSize:13, fontWeight:600, color:"var(--text-1)", marginBottom:12 }}>
                {new Date(selDay+"T00:00").toLocaleDateString("en-IN",{weekday:"long",day:"numeric",month:"long"})}
              </div>
              <div style={{ display:"flex", flexDirection:"column", gap:6, maxHeight:300, overflowY:"auto" }}>
                {employees.map(emp => {
                  const rec = records.find(r => r.empId === emp.empId && r.date === selDay);
                  return (
                    <div key={emp.empId} style={{ display:"flex", alignItems:"center", gap:10, padding:"8px 10px",
                      background:"var(--bg-elevated)", borderRadius:8, border:"1px solid var(--border)" }}>
                      <div style={{ width:28, height:28, borderRadius:"50%", background:"var(--accent-glow)",
                        display:"flex", alignItems:"center", justifyContent:"center",
                        fontSize:11, fontWeight:700, fontFamily:"'Outfit',sans-serif", color:"var(--accent-light)", flexShrink:0 }}>
                        {emp.name?.slice(0,2).toUpperCase()}
                      </div>
                      <div style={{ flex:1, minWidth:0 }}>
                        <div style={{ fontSize:12, fontWeight:500, color:"var(--text-1)", whiteSpace:"nowrap", overflow:"hidden", textOverflow:"ellipsis" }}>{emp.name}</div>
                        {rec && <div style={{ fontSize:10, color:"var(--text-3)" }}>{rec.checkIn||"--"} → {rec.checkOut||"--"}</div>}
                      </div>
                      <div style={{ display:"flex", alignItems:"center", gap:6 }}>
                        {rec ? <StatusBadge status={rec.status}/> : <StatusBadge status="ABSENT"/>}
                        <button style={{ width:22, height:22, borderRadius:5, background:"var(--bg-hover)",
                          border:"1px solid var(--border)", color:"var(--text-3)", cursor:"pointer", fontSize:11,
                          display:"flex", alignItems:"center", justifyContent:"center" }}
                          onClick={() => { setMarkModal({empId:emp.empId!, date:selDay!}); setMarkForm(rec ? {status:rec.status,checkIn:rec.checkIn||"09:00",checkOut:rec.checkOut||"18:00",notes:rec.notes||""} : {status:"PRESENT",checkIn:"09:00",checkOut:"18:00",notes:""}); }}>
                          <svg width="11" height="11" fill="none" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>
                        </button>
                      </div>
                    </div>
                  );
                })}
                {employees.length === 0 && <div style={{ fontSize:12, color:"var(--text-3)", textAlign:"center" }}>No employees found</div>}
              </div>
            </div>
          )}

          {/* Admin: today quick mark */}
          {isAdmin && !selDay && (
            <div className="ems-card">
              <div style={{ fontSize:13, fontWeight:600, color:"var(--text-1)", marginBottom:8 }}>Quick Actions</div>
              <div style={{ fontSize:12, color:"var(--text-3)", marginBottom:12 }}>Click any date on the calendar to view or mark attendance for that day.</div>
              <div style={{ display:"flex", flexDirection:"column", gap:6 }}>
                {[
                  { label:"Today's Attendance", sub: `${records.filter(r=>r.date===today.toISOString().slice(0,10)&&(r.status==="PRESENT"||r.status==="LATE")).length}/${employees.length} present`, color:"var(--green)" },
                  { label:"This Month Records", sub:`${records.length} entries logged`, color:"var(--accent-light)" },
                ].map(s => (
                  <div key={s.label} style={{ padding:"10px 12px", background:"var(--bg-elevated)", borderRadius:8, border:"1px solid var(--border)" }}>
                    <div style={{ fontSize:12, color:"var(--text-3)" }}>{s.label}</div>
                    <div style={{ fontSize:15, fontWeight:700, color:s.color, marginTop:2 }}>{s.sub}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ── Admin mark attendance modal ────────────────────────────────────── */}
      {markModal && (
        <div className="ems-modal-backdrop" onClick={() => setMarkModal(null)}>
          <div className="ems-modal" style={{ width:400 }} onClick={e => e.stopPropagation()}>
            <div className="modal-title">Mark Attendance</div>
            <div className="modal-sub">
              {empMap[markModal.empId]?.name || `Emp #${markModal.empId}`} — {markModal.date}
            </div>
            <div className="form-group">
              <label className="form-label">Status</label>
              <select className="form-control-ems" value={markForm.status}
                onChange={e => setMarkForm({...markForm, status:e.target.value as AttendanceStatus})}>
                {Object.keys(STATUS_COLORS).map(s => (
                  <option key={s} value={s}>{STATUS_COLORS[s].label}</option>
                ))}
              </select>
            </div>
            <div style={{ display:"grid", gridTemplateColumns:"1fr 1fr", gap:"0 12px" }}>
              <div className="form-group">
                <label className="form-label">Check In</label>
                <input type="time" className="form-control-ems" value={markForm.checkIn}
                  onChange={e => setMarkForm({...markForm, checkIn:e.target.value})}/>
              </div>
              <div className="form-group">
                <label className="form-label">Check Out</label>
                <input type="time" className="form-control-ems" value={markForm.checkOut}
                  onChange={e => setMarkForm({...markForm, checkOut:e.target.value})}/>
              </div>
            </div>
            <div className="form-group">
              <label className="form-label">Notes</label>
              <input type="text" className="form-control-ems" value={markForm.notes}
                placeholder="Optional notes..." onChange={e => setMarkForm({...markForm, notes:e.target.value})}/>
            </div>
            <div style={{ display:"flex", gap:10, marginTop:8 }}>
              <button className="btn-ems btn-secondary-ems" onClick={() => setMarkModal(null)}>Cancel</button>
              <button className="btn-ems btn-primary-ems" style={{ flex:1, justifyContent:"center" }} onClick={handleAdminMark}>
                Save Attendance
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

import { useEffect, useState } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { getEmployees } from "../../employees/services/EmployeeService";
import { Link } from "react-router-dom";
import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend, BarChart, Bar
} from "recharts";
import jsPDF from "jspdf";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";
import type { User, Leave, Notification } from "../../../shared/types/models";
import type { TooltipContentProps } from "recharts";

const COLORS = ["#6366f1","#2dd4bf","#22c55e","#f59e0b","#ef4444","#a78bfa","#fb7185"];

const StatSkeleton = () => (
  <div className="skeleton-card">
    <div className="skeleton" style={{width:38,height:38,borderRadius:9,marginBottom:14}} />
    <div className="skeleton" style={{width:"50%",height:11,marginBottom:6}} />
    <div className="skeleton" style={{width:"70%",height:26}} />
  </div>
);

const ChartSkeleton = () => (
  <div className="skeleton-card" style={{height:260}}>
    <div className="skeleton" style={{width:"40%",height:14,marginBottom:6}} />
    <div className="skeleton" style={{width:"25%",height:11,marginBottom:20}} />
    <div className="skeleton" style={{width:"100%",height:180,borderRadius:8}} />
  </div>
);

const CustomTooltip = ({ active, payload, label }: TooltipContentProps<number, string>) => {
  if (active && payload && payload.length) {
    return (
      <div style={{background:"var(--bg-elevated)",border:"1px solid var(--border-bright)",borderRadius:8,padding:"10px 14px"}}>
        <div style={{fontSize:11,color:"var(--text-3)",marginBottom:4}}>{label}</div>
        {payload.map((p, i) => (
          <div key={i} style={{fontSize:13,fontWeight:600,color:p.color}}>{p.name}: {p.value}</div>
        ))}
      </div>
    );
  }
  return null;
};

const generateMonthlyData = (count: number) => {
  const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
  const now = new Date();
  return Array.from({length: 6}, (_, i) => {
    const d = new Date(now.getFullYear(), now.getMonth() - 5 + i, 1);
    return {
      month: months[d.getMonth()],
      employees: Math.max(1, count - Math.floor(Math.random() * 3)),
      leaves: Math.floor(Math.random() * 5),
    };
  });
};

export default function AdminDashboard() {
  const [employees, setEmployees] = useState<User[]>([]);
  const [leaves, setLeaves] = useState<Leave[]>([]);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);

  const name = localStorage.getItem("adminName") || "Admin";
  const hours = new Date().getHours();
  const greeting = hours < 12 ? "Good Morning" : hours < 17 ? "Good Afternoon" : "Good Evening";
  const formattedDate = new Date().toLocaleDateString("en-IN", {weekday:"long",year:"numeric",month:"long",day:"numeric"});

  useEffect(() => {
    fetchAll();
    const iv = setInterval(fetchAll, 30000);
    return () => clearInterval(iv);
  }, []);

  const fetchAll = async () => {
    try {
      const [e, l, n] = await Promise.all([
        getEmployees(),
        axiosInstance.get<Leave[]>("/api/leaves"),
        axiosInstance.get<Notification[]>("/api/notifications"),
      ]);
      setEmployees(e.data); setLeaves(l.data); setNotifications(n.data);
    } catch(err) { console.error(err); }
    finally { setLoading(false); }
  };

  const pending  = leaves.filter(l => l.status === "PENDING").length;
  const approved = leaves.filter(l => l.status === "APPROVED").length;
  const rejected = leaves.filter(l => l.status === "REJECTED").length;

  const deptData = Object.values(
    employees.reduce<Record<string, { name: string; value: number }>>((acc, emp) => {
      const d = emp.department || "Other";
      acc[d] = acc[d] || {name: d, value: 0};
      acc[d].value++;
      return acc;
    }, {})
  );

  const leaveStatusData = [
    {name:"Pending", value: pending},
    {name:"Approved", value: approved},
    {name:"Rejected", value: rejected},
  ];

  const monthlyData = generateMonthlyData(employees.length);

  const exportPDF = () => {
    const doc = new jsPDF();
    doc.setFontSize(16); doc.text("Tenemsa Dashboard Report", 14, 18);
    doc.setFontSize(11);
    doc.text(`Total Employees: ${employees.length}`, 14, 34);
    doc.text(`Pending Leaves: ${pending}`, 14, 44);
    doc.text(`Approved Leaves: ${approved}`, 14, 54);
    doc.text(`Rejected Leaves: ${rejected}`, 14, 64);
    doc.text(`Generated: ${formattedDate}`, 14, 80);
    doc.save("Tenemsa_Dashboard_Report.pdf");
  };

  const exportExcel = () => {
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(employees), "Employees");
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(leaves), "Leaves");
    saveAs(new Blob([XLSX.write(wb, {bookType:"xlsx",type:"array"})]), "Tenemsa_Report.xlsx");
  };

  const stats = [
    {label:"Total Employees", value: employees.length, color:"blue", icon:<svg width="18" height="18" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>},
    {label:"Pending Leaves",  value: pending,  color:"amber", icon:<svg width="18" height="18" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8"/><path d="M12 7v5l3 3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>},
    {label:"Approved Leaves", value: approved, color:"green", icon:<svg width="18" height="18" fill="none" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><polyline points="22 4 12 14.01 9 11.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>},
    {label:"Rejected Leaves", value: rejected, color:"red",   icon:<svg width="18" height="18" fill="none" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.8"/><path d="M15 9l-6 6M9 9l6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>},
  ];

  const quickLinks = [
    {to:"/admin/employees", label:"View Employees", icon:<svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>, color:"blue"},
    {to:"/admin/add",       label:"Add Employee", icon:<svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/><line x1="19" y1="8" x2="19" y2="14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><line x1="22" y1="11" x2="16" y2="11" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>, color:"teal"},
    {to:"/admin/leaves",    label:"Manage Leaves", icon:<svg width="20" height="20" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01M16 18h.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>, color:"amber"},
    {to:"/admin/apply-leave", label:"Apply Leave", icon:<svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M14 2v6h6M12 18v-6M9 15h6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>, color:"green"},
  ];

  return (
    <div className="fade-in">
      {/* Header */}
      <div className="flex-between mb-24" style={{flexWrap:"wrap",gap:12}}>
        <div>
          <div style={{fontSize:12,color:"var(--text-3)",marginBottom:4,textTransform:"uppercase",letterSpacing:"0.5px",fontWeight:500}}>{formattedDate}</div>
          <h2 style={{fontSize:22,fontWeight:700,fontFamily:"'Outfit',sans-serif",color:"var(--text-1)",letterSpacing:"-0.3px"}}>{greeting}, {name}</h2>
          <div style={{fontSize:13,color:"var(--text-3)",marginTop:2}}>Here's your Tenemsa overview at a glance.</div>
        </div>
        <div style={{display:"flex",gap:8}}>
          <button className="btn-ems btn-secondary-ems" onClick={exportPDF}>
            <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
            Export PDF
          </button>
          <button className="btn-ems btn-primary-ems" onClick={exportExcel}>
            <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
            Export Excel
          </button>
        </div>
      </div>

      {/* Stat Cards */}
      <div className="stat-grid">
        {loading ? Array(4).fill(0).map((_,i) => <StatSkeleton key={i}/>) :
          stats.map((s, i) => (
            <div className={`stat-card ${s.color}`} key={i}>
              <div className={`stat-icon ${s.color}`}>{s.icon}</div>
              <div className="stat-label">{s.label}</div>
              <div className="stat-value font-outfit">{s.value}</div>
            </div>
          ))
        }
      </div>

      {/* Charts */}
      <div className="charts-grid">
        {loading ? <ChartSkeleton /> : (
          <div className="chart-card">
            <div className="chart-header">
              <div>
                <div className="chart-title">Leave Trend</div>
                <div className="chart-sub">Monthly leave overview</div>
              </div>
              <span className="chart-pill">Last 6 months</span>
            </div>
            <ResponsiveContainer width="100%" height={210}>
              <AreaChart data={monthlyData} margin={{top:0,right:0,left:-20,bottom:0}}>
                <defs>
                  <linearGradient id="gradL" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                  </linearGradient>
                  <linearGradient id="gradE" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#2dd4bf" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#2dd4bf" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <XAxis dataKey="month" tick={{fill:"var(--text-3)",fontSize:11}} axisLine={false} tickLine={false}/>
                <YAxis tick={{fill:"var(--text-3)",fontSize:11}} axisLine={false} tickLine={false}/>
                <Tooltip content={CustomTooltip} />
                <Area type="monotone" dataKey="employees" name="Employees" stroke="#2dd4bf" fill="url(#gradE)" strokeWidth={2} dot={false}/>
                <Area type="monotone" dataKey="leaves" name="Leaves" stroke="#6366f1" fill="url(#gradL)" strokeWidth={2} dot={false}/>
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}

        {loading ? <ChartSkeleton /> : (
          <div className="chart-card">
            <div className="chart-header">
              <div>
                <div className="chart-title">Department Split</div>
                <div className="chart-sub">By employee count</div>
              </div>
              <span className="chart-pill">{deptData.length} dept{deptData.length !== 1 ? "s" : ""}</span>
            </div>
            {deptData.length === 0 ? (
              <div style={{height:210,display:"flex",alignItems:"center",justifyContent:"center",color:"var(--text-3)",fontSize:13}}>No employee data yet</div>
            ) : (
              <ResponsiveContainer width="100%" height={210}>
                <PieChart>
                  <Pie data={deptData} cx="50%" cy="45%" innerRadius={60} outerRadius={85} paddingAngle={3} dataKey="value">
                    {deptData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} stroke="none"/>)}
                  </Pie>
                  <Tooltip content={CustomTooltip} />
                  <Legend wrapperStyle={{fontSize:11,color:"var(--text-2)"}}/>
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        )}
      </div>

      {/* Leave Status Bar + Activity */}
      <div className="bottom-grid">
        {loading ? <ChartSkeleton /> : (
          <div className="chart-card">
            <div className="chart-header">
              <div>
                <div className="chart-title">Leave Status Overview</div>
                <div className="chart-sub">All time breakdown</div>
              </div>
            </div>
            <ResponsiveContainer width="100%" height={160}>
              <BarChart data={leaveStatusData} margin={{top:0,right:0,left:-20,bottom:0}}>
                <XAxis dataKey="name" tick={{fill:"var(--text-3)",fontSize:11}} axisLine={false} tickLine={false}/>
                <YAxis tick={{fill:"var(--text-3)",fontSize:11}} axisLine={false} tickLine={false}/>
                <Tooltip content={CustomTooltip} />
                <Bar dataKey="value" radius={[4,4,0,0]}>
                  {leaveStatusData.map((_, i) => <Cell key={i} fill={["#f59e0b","#22c55e","#ef4444"][i]}/>)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}

        {loading ? <ChartSkeleton /> : (
          <div className="chart-card">
            <div className="chart-header">
              <div className="chart-title">Recent Activity</div>
            </div>
            <div className="activity-list">
              {notifications.length === 0 ? (
                <div style={{color:"var(--text-3)",fontSize:13,padding:"20px 0",textAlign:"center"}}>No recent activity</div>
              ) : notifications.slice(0, 5).map((n, i) => (
                <div key={i} className="activity-item">
                  <div className="activity-dot" style={{background: n.read ? "var(--text-3)" : "var(--accent)"}}/>
                  <div>
                    <div className={`activity-msg${!n.read ? " bold" : ""}`}>{n.message}</div>
                    {n.createdAt && <div className="activity-time">{new Date(n.createdAt).toLocaleString("en-IN",{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"})}</div>}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Quick Links */}
      <div style={{marginBottom:8}}>
        <div style={{fontSize:13,fontWeight:600,color:"var(--text-2)",marginBottom:12,textTransform:"uppercase",letterSpacing:"0.5px"}}>Quick Actions</div>
        <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(160px,1fr))",gap:12}}>
          {quickLinks.map((q, i) => (
            <Link to={q.to} key={i} style={{textDecoration:"none"}}>
              <div className="ems-card" style={{textAlign:"center",cursor:"pointer",transition:"all 0.2s"}}>
                <div className={`stat-icon ${q.color}`} style={{margin:"0 auto 10px"}}>{q.icon}</div>
                <div style={{fontSize:13,fontWeight:500,color:"var(--text-1)"}}>{q.label}</div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

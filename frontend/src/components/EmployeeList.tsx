import { useEffect, useRef, useState } from "react";
import { getEmployees, deleteEmployee, searchEmployees, deleteMultipleEmployees } from "../services/EmployeeService";
import { useNavigate, useLocation } from "react-router-dom";
import type { User } from "../types/models";

const avatarColors = ["#6366f1","#2dd4bf","#22c55e","#f59e0b","#ef4444","#a78bfa","#fb7185","#38bdf8"];
const getColor = (name?: string | null) => avatarColors[(name?.charCodeAt(0) || 0) % avatarColors.length];
const getInitials = (name?: string | null) => name ? name.split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2) : "?";

const SearchIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="1.8"/><path d="M21 21l-4.35-4.35" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const EditIcon  = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const TrashIcon = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M19 6l-1 14H6L5 6M10 11v6M14 11v6M9 6V4h6v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const PlusIcon  = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;

const RowSkeleton = () => (
  <tr>
    {[32,90,80,70,70,60,70].map((w,i) => (
      <td key={i} style={{padding:"13px 16px"}}>
        <div className="skeleton" style={{width:w,height:14,borderRadius:4}}/>
      </td>
    ))}
  </tr>
);

function EmployeeList() {
  const [employees, setEmployees] = useState<User[]>([]);
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();
  const searchInputRef = useRef<HTMLInputElement>(null);

  const deptFilter = new URLSearchParams(location.search).get("department");

  useEffect(() => { loadEmployees(); }, []);

  const loadEmployees = async () => {
    setLoading(true);
    try {
      const res = await getEmployees();
      setEmployees(res.data);
      setSelectedIds([]); setSelectAll(false);
    } catch(e) { console.error(e); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    const delay = setTimeout(() => {
      if (!searchTerm.trim()) { loadEmployees(); return; }
      setLoading(true);
      searchEmployees(searchTerm)
        .then(res => setEmployees(res.data))
        .catch(e => console.error(e))
        .finally(() => setLoading(false));
    }, 300);
    return () => clearTimeout(delay);
  }, [searchTerm]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.ctrlKey && e.key.toLowerCase()==="f") { e.preventDefault(); searchInputRef.current?.focus(); }};
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  const handleDelete = async (empId: number) => {
    if (!confirm("Remove this employee?")) return;
    await deleteEmployee(empId).catch(e => console.error(e));
    loadEmployees();
  };

  const toggleSelect = (id: number) => setSelectedIds(prev => prev.includes(id) ? prev.filter(i=>i!==id) : [...prev, id]);

  const toggleAll = () => {
    if (selectAll) { setSelectedIds([]); setSelectAll(false); }
    else { setSelectedIds(displayed.map(e=>e.empId).filter((id): id is number => id !== null)); setSelectAll(true); }
  };

  const deleteSelected = async () => {
    if (!selectedIds.length || !confirm(`Remove ${selectedIds.length} employees?`)) return;
    await deleteMultipleEmployees(selectedIds).catch(e => console.error(e));
    loadEmployees();
  };

  const displayed = deptFilter ? employees.filter(e => e.department === deptFilter) : employees;

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Employees</div>
          <div className="page-sub">{displayed.length} total member{displayed.length !== 1 ? "s" : ""}</div>
        </div>
        <div style={{display:"flex",gap:8,flexWrap:"wrap"}}>
          {selectedIds.length > 0 && (
            <button className="btn-ems btn-danger-ems" onClick={deleteSelected}>
              <TrashIcon /> Delete ({selectedIds.length})
            </button>
          )}
          <button className="btn-ems btn-primary-ems" onClick={() => navigate("/admin/add")}>
            <PlusIcon /> Add Employee
          </button>
        </div>
      </div>

      <div className="ems-card" style={{padding:0}}>
        {/* Toolbar */}
        <div style={{padding:"14px 16px",borderBottom:"1px solid var(--border)",display:"flex",gap:10,flexWrap:"wrap",alignItems:"center"}}>
          <div className="search-bar" style={{flex:"1",minWidth:200}}>
            <SearchIcon />
            <input ref={searchInputRef} placeholder="Search employees... (Ctrl+F)" value={searchTerm} onChange={e => setSearchTerm(e.target.value)}/>
          </div>
          {deptFilter && (
            <div style={{display:"flex",alignItems:"center",gap:6}}>
              <span className="badge badge-blue">Dept: {deptFilter}</span>
              <button className="btn-icon" onClick={() => navigate("/admin/employees")}>
                <svg width="12" height="12" fill="none" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>
              </button>
            </div>
          )}
        </div>

        <div className="ems-table-wrap" style={{borderRadius:0,border:"none"}}>
          <table className="ems-table">
            <thead>
              <tr>
                <th style={{width:40}}>
                  <input type="checkbox" checked={selectAll} onChange={toggleAll}
                    style={{accentColor:"var(--accent)",width:14,height:14,cursor:"pointer"}}/>
                </th>
                <th>Employee</th>
                <th>ID</th>
                <th>Department</th>
                <th>Email</th>
                <th>Salary</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? Array(5).fill(0).map((_,i) => <RowSkeleton key={i}/>) :
                displayed.length === 0 ? (
                  <tr><td colSpan={7} style={{textAlign:"center",padding:"40px 16px",color:"var(--text-3)",fontSize:13}}>No employees found</td></tr>
                ) :
                displayed.map(emp => (
                  <tr key={emp.empId} style={selectedIds.includes(emp.empId!) ? {background:"var(--accent-glow2)"} : {}}>
                    <td>
                      <input type="checkbox" checked={selectedIds.includes(emp.empId!)} onChange={() => toggleSelect(emp.empId!)}
                        style={{accentColor:"var(--accent)",width:14,height:14,cursor:"pointer"}}/>
                    </td>
                    <td>
                      <div style={{display:"flex",alignItems:"center",gap:9}}>
                        <div className="emp-avatar" style={{background:`${getColor(emp.name)}22`,color:getColor(emp.name)}}>
                          {getInitials(emp.name)}
                        </div>
                        <span className="td-primary">{emp.name}</span>
                      </div>
                    </td>
                    <td><span className="badge badge-muted">#{emp.empId}</span></td>
                    <td><span style={{color:"var(--text-2)",fontSize:13}}>{emp.department || "—"}</span></td>
                    <td><span style={{color:"var(--text-3)",fontSize:12.5}}>{emp.email}</span></td>
                    <td><span style={{color:"var(--teal)",fontWeight:600,fontSize:13}}>₹{Number(emp.salary).toLocaleString("en-IN")}</span></td>
                    <td>
                      <div style={{display:"flex",gap:6}}>
                        <button className="btn-icon" title="Edit" onClick={() => navigate(`/admin/update/${emp.empId}`)}
                          style={{color:"var(--accent-light)"}} onMouseOver={e=>e.currentTarget.style.background="var(--accent-glow)"} onMouseOut={e=>e.currentTarget.style.background="transparent"}>
                          <EditIcon/>
                        </button>
                        <button className="btn-icon danger" title="Delete" onClick={() => handleDelete(emp.empId!)}>
                          <TrashIcon/>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default EmployeeList;

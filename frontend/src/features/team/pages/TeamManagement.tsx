import { useEffect, useState } from "react";
import axiosInstance from "../services/axiosInstance";
import { getEmployees } from "../services/EmployeeService";
import type { Team, TeamStatus, User } from "../types/models";

interface TeamFormState {
  name: string; description: string; projectName: string; deadline: string;
  status: TeamStatus; teamLeadId: number | ""; memberIds: number[];
}

const PlusIcon  = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const EditIcon  = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const TrashIcon = () => <svg width="13" height="13" fill="none" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M19 6l-1 14H6L5 6M10 11v6M14 11v6M9 6V4h6v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const CloseIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>;
const TeamIcon  = () => <svg width="22" height="22" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.6"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round"/></svg>;

const STATUS_MAP: Record<TeamStatus, {cls:string; label:string}> = {
  ACTIVE:   {cls:"badge-green", label:"Active"},
  ON_HOLD:  {cls:"badge-amber", label:"On Hold"},
  COMPLETED:{cls:"badge-blue",  label:"Completed"},
  CANCELLED:{cls:"badge-red",   label:"Cancelled"},
};
const AV_COLORS = ["#6366f1","#2dd4bf","#22c55e","#f59e0b","#ef4444","#a78bfa"];
const getColor  = (n?: string | null) => AV_COLORS[(n?.charCodeAt(0)||0) % AV_COLORS.length];

interface EmpAvatarProps {
  empId: number; name?: string | null; profileImageUrl?: string | null; size?: number;
}

/* Simple member avatar — works with just an empId (no need for full employee list) */
function EmpAvatar({ empId, name, profileImageUrl, size = 30 }: EmpAvatarProps) {
  const c = getColor(name || String(empId));
  const lbl = name ? name.slice(0,2).toUpperCase() : `#${empId}`;
  if (profileImageUrl) {
    return <img src={profileImageUrl} alt={name||"member"} title={name||`#${empId}`}
      style={{width:size,height:size,borderRadius:"50%",objectFit:"cover",border:"2px solid var(--bg-surface)",flexShrink:0}}/>;
  }
  return (
    <div title={name || `Emp #${empId}`}
      style={{width:size,height:size,borderRadius:"50%",flexShrink:0,background:`${c}22`,color:c,
        display:"flex",alignItems:"center",justifyContent:"center",fontSize:size*0.34,
        fontWeight:700,fontFamily:"'Outfit',sans-serif",border:"2px solid var(--bg-surface)"}}>
      {lbl}
    </div>
  );
}

interface TeamCardProps {
  team: Team; employees: User[]; onEdit: (t: Team) => void; onDelete: (t: Team) => void; isAdmin: boolean;
}

function TeamCard({ team, employees, onEdit, onDelete, isAdmin }: TeamCardProps) {
  // employees may be empty for USER role — fall back to empId display
  const getEmp = (id: number) => employees.find(e => Number(e.empId) === Number(id));
  const lead   = team.teamLeadId ? getEmp(team.teamLeadId) : null;
  const s      = STATUS_MAP[team.status] || STATUS_MAP.ACTIVE;
  const overdue= team.deadline && new Date(team.deadline) < new Date() && team.status==="ACTIVE";
  const memberIds = team.memberIds || [];

  return (
    <div className="ems-card" style={{display:"flex",flexDirection:"column",gap:12,
      borderColor:overdue?"rgba(239,68,68,0.3)":undefined}}>
      <div style={{display:"flex",justifyContent:"space-between",alignItems:"flex-start"}}>
        <div>
          <div style={{fontSize:15,fontWeight:700,color:"var(--text-1)",fontFamily:"'Outfit',sans-serif"}}>{team.name}</div>
          {team.projectName && <div style={{fontSize:11,color:"var(--accent-light)",marginTop:2}}>{team.projectName}</div>}
        </div>
        <div style={{display:"flex",alignItems:"center",gap:6}}>
          <span className={`badge ${s.cls}`}>{s.label}</span>
          {isAdmin && (
            <>
              <button className="btn-icon" onClick={()=>onEdit(team)}><EditIcon/></button>
              <button className="btn-icon danger" onClick={()=>onDelete(team)}><TrashIcon/></button>
            </>
          )}
        </div>
      </div>

      {team.description && (
        <div style={{fontSize:12.5,color:"var(--text-3)",lineHeight:1.6}}>{team.description}</div>
      )}

      <div style={{display:"flex",gap:14,flexWrap:"wrap"}}>
        {team.deadline && (
          <div style={{fontSize:11,color:overdue?"var(--red)":"var(--text-3)",display:"flex",alignItems:"center",gap:4}}>
            <svg width="11" height="11" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
            {new Date(team.deadline+"T00:00").toLocaleDateString("en-IN",{day:"numeric",month:"short",year:"numeric"})}
            {overdue && " (Overdue)"}
          </div>
        )}
        <div style={{fontSize:11,color:"var(--text-3)",display:"flex",alignItems:"center",gap:4}}>
          <svg width="11" height="11" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8"/></svg>
          {memberIds.length} member{memberIds.length!==1?"s":""}
        </div>
      </div>

      {/* Team lead */}
      {team.teamLeadId && (
        <div style={{display:"flex",alignItems:"center",gap:8,padding:"8px 10px",
          background:"var(--accent-glow2)",borderRadius:8,border:"1px solid rgba(99,102,241,0.2)"}}>
          <EmpAvatar empId={team.teamLeadId} name={lead?.name} profileImageUrl={lead?.profileImageUrl} size={26}/>
          <div>
            <div style={{fontSize:10,color:"var(--text-3)"}}>Team Lead</div>
            <div style={{fontSize:12,fontWeight:500,color:"var(--accent-light)"}}>{lead?.name||`Emp #${team.teamLeadId}`}</div>
          </div>
        </div>
      )}

      {/* Members */}
      {memberIds.length > 0 && (
        <div>
          <div style={{fontSize:10,color:"var(--text-3)",textTransform:"uppercase",letterSpacing:"0.5px",marginBottom:7}}>Members</div>
          <div style={{display:"flex",flexWrap:"wrap",gap:4}}>
            {memberIds.slice(0,10).map(mid => {
              const emp = getEmp(mid);
              return <EmpAvatar key={mid} empId={mid} name={emp?.name} profileImageUrl={emp?.profileImageUrl} size={30}/>;
            })}
            {memberIds.length > 10 && (
              <div style={{width:30,height:30,borderRadius:"50%",background:"var(--bg-elevated)",
                display:"flex",alignItems:"center",justifyContent:"center",
                fontSize:9,color:"var(--text-3)",border:"2px solid var(--bg-surface)"}}>
                +{memberIds.length-10}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

interface TeamFormModalProps {
  team: Team | null; employees: User[]; onClose: () => void; onSave: (form: TeamFormState) => void;
}

function TeamFormModal({ team, employees, onClose, onSave }: TeamFormModalProps) {
  const [form, setForm] = useState<TeamFormState>(team ? {
    name:team.name||"", description:team.description||"", projectName:team.projectName||"",
    deadline:team.deadline||"", status:team.status||"ACTIVE",
    teamLeadId:team.teamLeadId?Number(team.teamLeadId):"",
    memberIds:(team.memberIds||[]).map(Number),
  } : { name:"", description:"", projectName:"", deadline:"", status:"ACTIVE", teamLeadId:"", memberIds:[] });

  const toggle = (id: number) => {
    const nid = Number(id);
    setForm(f => ({
      ...f,
      memberIds: f.memberIds.includes(nid) ? f.memberIds.filter(i=>i!==nid) : [...f.memberIds, nid]
    }));
  };

  return (
    <div className="ems-modal-backdrop" onClick={onClose}>
      <div className="ems-modal" style={{width:540,maxHeight:"88vh"}} onClick={e=>e.stopPropagation()}>
        <div style={{display:"flex",justifyContent:"space-between",alignItems:"center",marginBottom:16}}>
          <div>
            <div className="modal-title">{team?"Edit Team":"Create Team"}</div>
            <div className="modal-sub" style={{marginBottom:0}}>{team?"Update team details":"Set up a new project team"}</div>
          </div>
          <button className="btn-icon" onClick={onClose}><CloseIcon/></button>
        </div>

        <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0 14px"}}>
          <div className="form-group" style={{gridColumn:"1/-1"}}>
            <label className="form-label">Team Name *</label>
            <input className="form-control-ems" value={form.name}
              onChange={e=>setForm({...form,name:e.target.value})} placeholder="e.g. Frontend Squad"/>
          </div>
          <div className="form-group">
            <label className="form-label">Project Name</label>
            <input className="form-control-ems" value={form.projectName}
              onChange={e=>setForm({...form,projectName:e.target.value})} placeholder="e.g. EMS v2.0"/>
          </div>
          <div className="form-group">
            <label className="form-label">Deadline</label>
            <input type="date" className="form-control-ems" value={form.deadline}
              onChange={e=>setForm({...form,deadline:e.target.value})}/>
          </div>
          <div className="form-group" style={{gridColumn:"1/-1"}}>
            <label className="form-label">Description</label>
            <textarea className="form-control-ems" rows={2} value={form.description}
              onChange={e=>setForm({...form,description:e.target.value})}
              placeholder="What is this team working on?" style={{resize:"vertical"}}/>
          </div>
          <div className="form-group">
            <label className="form-label">Status</label>
            <select className="form-control-ems" value={form.status}
              onChange={e=>setForm({...form,status:e.target.value as TeamStatus})}>
              {Object.entries(STATUS_MAP).map(([k,v])=><option key={k} value={k}>{v.label}</option>)}
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Team Lead</label>
            <select className="form-control-ems" value={form.teamLeadId}
              onChange={e=>setForm({...form,teamLeadId:Number(e.target.value)||""})}>
              <option value="">Select lead...</option>
              {employees.map(e=><option key={e.empId} value={e.empId ?? ""}>{e.name}</option>)}
            </select>
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">Members ({form.memberIds.length} selected)</label>
          <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:5,maxHeight:190,
            overflowY:"auto",padding:6,background:"var(--bg-elevated)",
            borderRadius:8,border:"1px solid var(--border-bright)"}}>
            {employees.map(emp => {
              const sel = form.memberIds.includes(Number(emp.empId));
              return (
                <div key={emp.empId} onClick={()=>toggle(emp.empId!)}
                  style={{display:"flex",alignItems:"center",gap:8,padding:"7px 10px",
                    cursor:"pointer",borderRadius:6,transition:"background 0.15s",
                    background:sel?"var(--accent-glow)":"transparent",
                    border:sel?"1px solid rgba(99,102,241,0.3)":"1px solid transparent"}}>
                  <EmpAvatar empId={emp.empId!} name={emp.name} profileImageUrl={emp.profileImageUrl} size={22}/>
                  <div>
                    <div style={{fontSize:12,fontWeight:sel?600:400,color:sel?"var(--accent-light)":"var(--text-2)"}}>{emp.name}</div>
                    <div style={{fontSize:10,color:"var(--text-3)"}}>{emp.department}</div>
                  </div>
                  {sel && <div style={{marginLeft:"auto",width:14,height:14,borderRadius:"50%",
                    background:"var(--accent)",display:"flex",alignItems:"center",justifyContent:"center"}}>
                    <svg width="8" height="8" fill="none" viewBox="0 0 24 24">
                      <polyline points="20 6 9 17 4 12" stroke="white" strokeWidth="3" strokeLinecap="round"/>
                    </svg>
                  </div>}
                </div>
              );
            })}
          </div>
        </div>

        <div style={{display:"flex",gap:10,marginTop:8}}>
          <button className="btn-ems btn-secondary-ems" onClick={onClose}>Cancel</button>
          <button className="btn-ems btn-primary-ems" style={{flex:1,justifyContent:"center"}}
            onClick={()=>{if(!form.name.trim())return alert("Team name required");
              onSave({...form,memberIds:form.memberIds.map(Number)});}}>
            {team?"Save Changes":"Create Team"}
          </button>
        </div>
      </div>
    </div>
  );
}

interface TeamManagementProps { isAdmin: boolean; empId?: number; }

export default function TeamManagement({ isAdmin, empId: propEmpId }: TeamManagementProps) {
  const [teams,      setTeams]      = useState<Team[]>([]);
  const [employees,  setEmployees]  = useState<User[]>([]); // only populated for admin
  const [loading,    setLoading]    = useState(true);
  const [modal,      setModal]      = useState<Team | "create" | null>(null);
  const [delConfirm, setDelConfirm] = useState<Team | null>(null);
  const [filter,     setFilter]     = useState<TeamStatus | "ALL">("ALL");

  const myEmpId = Number(propEmpId || localStorage.getItem("empId") || 0);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        if (isAdmin) {
          // Admin: get ALL teams + employee list for member display
          const [tRes, eRes] = await Promise.all([
            axiosInstance.get<Team[]>("/api/teams"),
            getEmployees(),
          ]);
          setTeams(tRes.data || []);
          setEmployees(eRes.data || []);
        } else {
          // Employee: use dedicated endpoint — GET /api/teams/employee/{empId}
          // This calls the backend stream filter which is reliable
          const tRes = await axiosInstance.get<Team[]>(`/api/teams/employee/${myEmpId}`);
          setTeams(tRes.data || []);
          // Don't fetch /api/employees — employee doesn't have access (403)
          // TeamCard will fall back to showing empId instead of name
        }
      } catch (e) {
        console.error("Teams fetch error:", e);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [isAdmin, myEmpId]);

  const handleSave = async (formData: TeamFormState) => {
    try {
      if (modal === "create") {
        const res = await axiosInstance.post<Team>("/api/teams", formData);
        setTeams(p => [...p, res.data]);
      } else if (modal) {
        const res = await axiosInstance.put<Team>(`/api/teams/${modal.id}`, formData);
        setTeams(p => p.map(t => t.id === modal.id ? res.data : t));
      }
      setModal(null);
    } catch (e: any) { alert(e.response?.data?.message || "Failed to save team"); }
  };

  const handleDelete = async () => {
    if (!delConfirm) return;
    await axiosInstance.delete(`/api/teams/${delConfirm.id}`).catch(() => {});
    setTeams(p => p.filter(t => t.id !== delConfirm.id));
    setDelConfirm(null);
  };

  const displayTeams = isAdmin
    ? (filter === "ALL" ? teams : teams.filter(t => t.status === filter))
    : teams; // for employee, teams are already filtered by backend

  const countBy = (arr: Team[], status: TeamStatus | "ALL") => status === "ALL" ? arr.length : arr.filter(t => t.status === status).length;

  const CardSkel = () => (
    <div className="skeleton-card">
      <div className="skeleton" style={{width:"55%",height:15,borderRadius:4,marginBottom:8}}/>
      <div className="skeleton" style={{width:"35%",height:11,borderRadius:4,marginBottom:16}}/>
      <div className="skeleton" style={{width:"80%",height:11,borderRadius:4,marginBottom:6}}/>
      <div className="skeleton" style={{width:"60%",height:11,borderRadius:4}}/>
    </div>
  );

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">{isAdmin ? "Team Management" : "My Teams"}</div>
          <div className="page-sub">
            {isAdmin
              ? `${teams.length} team${teams.length!==1?"s":""} across all projects`
              : displayTeams.length > 0
                ? `You are in ${displayTeams.length} team${displayTeams.length!==1?"s":""}`
                : "Your assigned teams"}
          </div>
        </div>
        {isAdmin && (
          <button className="btn-ems btn-primary-ems" onClick={()=>setModal("create")}>
            <PlusIcon/> Create Team
          </button>
        )}
      </div>

      {/* Summary cards */}
      <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(130px,1fr))",gap:10,marginBottom:20}}>
        {[
          {key:"ALL",       label:"Total",     color:"var(--text-2)"},
          {key:"ACTIVE",    label:"Active",    color:"var(--green)"},
          {key:"ON_HOLD",   label:"On Hold",   color:"var(--amber)"},
          {key:"COMPLETED", label:"Completed", color:"var(--accent-light)"},
        ].map(s => (
          <div key={s.key} className="ems-card"
            style={{cursor:isAdmin?"pointer":"default",padding:"12px 16px",
              borderColor:filter===s.key&&isAdmin?"var(--accent)":undefined,
              background:filter===s.key&&isAdmin?"var(--accent-glow2)":undefined}}
            onClick={() => isAdmin && setFilter(s.key as TeamStatus | "ALL")}>
            <div style={{fontSize:10,color:"var(--text-3)",textTransform:"uppercase",letterSpacing:"0.5px"}}>{s.label}</div>
            <div style={{fontSize:22,fontWeight:700,fontFamily:"'Outfit',sans-serif",color:s.color,marginTop:2}}>
              {countBy(displayTeams, s.key as TeamStatus | "ALL")}
            </div>
          </div>
        ))}
      </div>

      {/* Grid */}
      <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fill,minmax(310px,1fr))",gap:14}}>
        {loading ? Array(4).fill(0).map((_,i)=><CardSkel key={i}/>) :
          displayTeams.length === 0 ? (
            <div style={{gridColumn:"1/-1",textAlign:"center",padding:"60px 0"}}>
              <div style={{display:"flex",justifyContent:"center",marginBottom:14,opacity:0.15}}><TeamIcon/></div>
              <div style={{fontSize:14,color:"var(--text-3)"}}>
                {isAdmin ? "No teams yet. Create one to get started." : "You are not assigned to any team yet."}
              </div>
              {!isAdmin && (
                <div style={{fontSize:12,color:"var(--text-3)",marginTop:6}}>
                  Ask your administrator to add you to a team.
                </div>
              )}
            </div>
          ) :
          displayTeams.map(team => (
            <TeamCard key={team.id} team={team} employees={employees} isAdmin={isAdmin}
              onEdit={t=>setModal(t)} onDelete={t=>setDelConfirm(t)}/>
          ))
        }
      </div>

      {/* Create/Edit modal */}
      {modal && (
        <TeamFormModal
          team={modal==="create"?null:modal}
          employees={employees}
          onClose={()=>setModal(null)}
          onSave={handleSave}/>
      )}

      {/* Delete confirm */}
      {delConfirm && (
        <div className="ems-modal-backdrop" onClick={()=>setDelConfirm(null)}>
          <div className="ems-modal" style={{width:380}} onClick={e=>e.stopPropagation()}>
            <div className="modal-title" style={{color:"var(--red)"}}>Delete Team</div>
            <div className="modal-sub">Delete <strong style={{color:"var(--text-1)"}}>{delConfirm.name}</strong>? This cannot be undone.</div>
            <div style={{display:"flex",gap:10,marginTop:16}}>
              <button className="btn-ems btn-secondary-ems" style={{flex:1,justifyContent:"center"}} onClick={()=>setDelConfirm(null)}>Cancel</button>
              <button className="btn-ems btn-danger-ems" style={{flex:1,justifyContent:"center"}} onClick={handleDelete}>
                <TrashIcon/> Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

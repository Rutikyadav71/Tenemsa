import { useEffect, useState } from "react";
import axiosInstance from "../services/axiosInstance";
import type { User } from "../types/models";

type ConfirmAction = { type: "freeze" | "unfreeze" | "delete"; admin: User };

const TrashIcon    = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><polyline points="3 6 5 6 21 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M19 6l-1 14H6L5 6M10 11v6M14 11v6M9 6V4h6v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const FreezeIcon   = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M12 2v20M2 12h20M4.93 4.93l14.14 14.14M19.07 4.93L4.93 19.07" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const UnfreezeIcon = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>;
const PlusIcon     = () => <svg width="14" height="14" fill="none" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>;
const AlertIcon    = () => <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/><line x1="12" y1="9" x2="12" y2="13" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><line x1="12" y1="17" x2="12.01" y2="17" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>;
const EyeIcon = ({open}: {open: boolean}) => open
  ? <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M1 1l22 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
  : <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="1.8"/><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8"/></svg>;

export default function AdminManagement() {
  const [admins,    setAdmins]    = useState<User[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [confirm,   setConfirm]   = useState<ConfirmAction | null>(null);
  const [showAdd,   setShowAdd]   = useState(false);
  const [message,   setMessage]   = useState({ text:"", type:"" });
  const [addForm,   setAddForm]   = useState({ name:"", email:"", password:"" });
  const [showPwd,   setShowPwd]   = useState(false);
  const [adding,    setAdding]    = useState(false);

  const myId     = Number(localStorage.getItem("id"));
  const isMain   = localStorage.getItem("primaryOwner") === "true";

  useEffect(() => { fetchAdmins(); }, []);

  const fetchAdmins = async () => {
    try {
      const res = await axiosInstance.get<User[]>("/api/admin/all");
      setAdmins(res.data || []);
    } catch {} finally { setLoading(false); }
  };

  const handleAdd = async () => {
    if (!addForm.name || !addForm.email || addForm.password.length < 6) {
      setMessage({ text:"Please fill all fields. Password min 6 characters.", type:"error" }); return;
    }
    setAdding(true);
    try {
      await axiosInstance.post("/api/admin/register-by-admin", addForm);
      setMessage({ text:`Admin ${addForm.name} added successfully!`, type:"success" });
      setShowAdd(false); setAddForm({ name:"", email:"", password:"" });
      fetchAdmins();
    } catch (e: any) {
      setMessage({ text: e.response?.data || "Failed to add admin.", type:"error" });
    } finally { setAdding(false); }
  };

  const executeAction = async () => {
    if (!confirm) return;
    const { type, admin } = confirm; setConfirm(null);
    try {
      if (type==="freeze")   await axiosInstance.put(`/api/admin/${admin.id}/freeze`);
      if (type==="unfreeze") await axiosInstance.put(`/api/admin/${admin.id}/unfreeze`);
      if (type==="delete")   await axiosInstance.delete(`/api/admin/${admin.id}`);
      setMessage({ text:`${admin.name} ${type}d successfully.`, type:"success" });
      fetchAdmins();
    } catch (e: any) {
      setMessage({ text: e.response?.data || "Action failed.", type:"error" });
    }
  };

  const canAct = isMain; // Only main admin can freeze/delete

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Admin Control</div>
          <div className="page-sub">{admins.length}/4 administrators registered</div>
        </div>
        {canAct && admins.length < 4 && (
          <button className="btn-ems btn-primary-ems" onClick={() => setShowAdd(true)}>
            <PlusIcon/> Add Admin
          </button>
        )}
      </div>

      {/* Info banner */}
      {!canAct && (
        <div className="alert-ems alert-info" style={{ marginBottom:16 }}>
          Only the Main Administrator can add, freeze, or delete other admin accounts.
        </div>
      )}
      {canAct && (
        <div style={{ padding:"10px 14px", background:"var(--accent-glow2)", border:"1px solid rgba(99,102,241,0.2)",
          borderRadius:"var(--radius-sm)", fontSize:12, color:"var(--text-2)", marginBottom:16, display:"flex", gap:8 }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" style={{flexShrink:0,marginTop:1}}>
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="var(--accent-light)" strokeWidth="1.8"/>
          </svg>
          You are the Main Administrator. You can add up to 4 admins, and freeze or delete sub-admins.
        </div>
      )}

      {message.text && (
        <div className={`alert-ems alert-${message.type}`} style={{ marginBottom:16 }}>
          {message.text}
        </div>
      )}

      {/* Admin list */}
      <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
        {loading ? Array(2).fill(0).map((_,i) => (
          <div key={i} className="skeleton-card" style={{ display:"flex", gap:14, alignItems:"center" }}>
            <div className="skeleton" style={{ width:52, height:52, borderRadius:"50%" }}/>
            <div style={{ flex:1 }}>
              <div className="skeleton" style={{ width:"30%", height:14, borderRadius:4, marginBottom:6 }}/>
              <div className="skeleton" style={{ width:"50%", height:12, borderRadius:4 }}/>
            </div>
          </div>
        )) : admins.map(admin => {
          const isMe = admin.id === myId;
          const isFrozen = admin.frozen;
          const isMainAdmin = admin.primaryOwner;

          return (
            <div key={admin.id} className="ems-card" style={{
              display:"flex", alignItems:"center", gap:16, flexWrap:"wrap",
              borderColor: isFrozen ? "rgba(239,68,68,0.3)" : isMainAdmin ? "rgba(99,102,241,0.3)" : undefined,
              background:  isFrozen ? "rgba(239,68,68,0.03)" : isMainAdmin ? "rgba(99,102,241,0.03)" : undefined,
            }}>
              <div style={{ width:52, height:52, borderRadius:"50%", flexShrink:0,
                background: isMainAdmin ? "var(--accent-glow)" : "var(--bg-elevated)",
                border:`2px solid ${isMainAdmin ? "var(--accent)" : "var(--border-bright)"}`,
                display:"flex", alignItems:"center", justifyContent:"center",
                fontFamily:"'Outfit',sans-serif", fontSize:18, fontWeight:700,
                color: isMainAdmin ? "var(--accent-light)" : "var(--text-2)" }}>
                {(admin.name||"A").split(" ").map(n=>n[0]).join("").toUpperCase().slice(0,2)}
              </div>

              <div style={{ flex:1 }}>
                <div style={{ display:"flex", alignItems:"center", gap:8, marginBottom:4, flexWrap:"wrap" }}>
                  <span style={{ fontSize:15, fontWeight:700, color:"var(--text-1)" }}>{admin.name}</span>
                  {isMainAdmin && <span className="badge badge-blue">Main Admin</span>}
                  {isMe && !isMainAdmin && <span className="badge badge-muted">You</span>}
                  {isFrozen && <span className="badge badge-red">Frozen</span>}
                  {!isFrozen && !isMainAdmin && <span className="badge badge-green">Active</span>}
                </div>
                <div style={{ fontSize:12.5, color:"var(--text-3)" }}>{admin.email}</div>
                <div style={{ fontSize:11, color:"var(--text-3)", marginTop:2 }}>
                  Admin ID: #{admin.id} · Registered: {admin.primaryOwner ? "First Admin" : "Added by Main Admin"}
                </div>
              </div>

              {/* Actions — only main admin can act on non-main, non-self */}
              {canAct && !isMainAdmin && !isMe && (
                <div style={{ display:"flex", gap:8, flexShrink:0 }}>
                  {isFrozen ? (
                    <button className="btn-ems btn-success-ems2" style={{ fontSize:12 }}
                      onClick={() => setConfirm({ type:"unfreeze", admin })}>
                      <UnfreezeIcon/> Unfreeze
                    </button>
                  ) : (
                    <button className="btn-ems btn-secondary-ems" style={{ fontSize:12 }}
                      onClick={() => setConfirm({ type:"freeze", admin })}>
                      <FreezeIcon/> Freeze
                    </button>
                  )}
                  <button className="btn-ems btn-danger-ems" style={{ fontSize:12 }}
                    onClick={() => setConfirm({ type:"delete", admin })}>
                    <TrashIcon/> Delete
                  </button>
                </div>
              )}
              {(isMe || isMainAdmin && isMe) && (
                <div style={{ padding:"6px 12px", fontSize:12, color:"var(--text-3)",
                  background:"var(--bg-elevated)", borderRadius:6, border:"1px solid var(--border)", flexShrink:0 }}>
                  Your account
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* How it works */}
      <div className="ems-card" style={{ marginTop:20 }}>
        <div style={{ fontSize:13, fontWeight:600, color:"var(--text-1)", marginBottom:10 }}>How Admin Access Works</div>
        {[
          ["Main Admin", "The first admin registered at setup. Can add up to 3 more sub-admins (4 total). Cannot be frozen or deleted."],
          ["Sub-Admin", "Added by the Main Admin from this panel. Has full EMS access. Can be frozen/unfrozen/deleted by Main Admin."],
          ["Freeze", "Temporarily blocks login. All data is preserved. The frozen admin is immediately logged out on next request."],
          ["Delete", "Permanently removes the admin account. The Main Admin account cannot be deleted."],
        ].map(([t,s]) => (
          <div key={t} style={{ display:"flex", gap:10, padding:"9px 0", borderBottom:"1px solid var(--border)" }}>
            <div style={{ fontSize:12, fontWeight:600, color:"var(--text-2)", minWidth:80, flexShrink:0 }}>{t}</div>
            <div style={{ fontSize:12, color:"var(--text-3)", lineHeight:1.6 }}>{s}</div>
          </div>
        ))}
      </div>

      {/* Add admin modal */}
      {showAdd && (
        <div className="ems-modal-backdrop" onClick={() => setShowAdd(false)}>
          <div className="ems-modal" style={{ width:420 }} onClick={e => e.stopPropagation()}>
            <div className="modal-title">Add New Admin</div>
            <div className="modal-sub">This admin will have full EMS access ({admins.length}/4 used)</div>
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input className="form-control-ems" value={addForm.name}
                onChange={e => setAddForm({...addForm,name:e.target.value})} placeholder="Admin full name"/>
            </div>
            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input type="email" className="form-control-ems" value={addForm.email}
                onChange={e => setAddForm({...addForm,email:e.target.value})} placeholder="admin@company.com"/>
            </div>
            <div className="form-group">
              <label className="form-label">Temporary Password</label>
              <div className="input-wrap">
                <input type={showPwd?"text":"password"} className="form-control-ems" value={addForm.password}
                  onChange={e => setAddForm({...addForm,password:e.target.value})}
                  placeholder="Min. 6 characters" style={{ paddingRight:40 }}/>
                <span className="input-icon-right" onClick={() => setShowPwd(v=>!v)}><EyeIcon open={showPwd}/></span>
              </div>
            </div>
            <div style={{ display:"flex", gap:10, marginTop:8 }}>
              <button className="btn-ems btn-secondary-ems" onClick={() => setShowAdd(false)}>Cancel</button>
              <button className="btn-ems btn-primary-ems" style={{ flex:1, justifyContent:"center" }}
                onClick={handleAdd} disabled={adding}>
                {adding ? "Adding..." : "Add Admin"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Confirm modal */}
      {confirm && (
        <div className="ems-modal-backdrop" onClick={() => setConfirm(null)}>
          <div className="ems-modal" style={{ width:420 }} onClick={e => e.stopPropagation()}>
            <div style={{ display:"flex", gap:12, alignItems:"flex-start", marginBottom:14 }}>
              <div style={{ color:confirm.type==="delete"?"var(--red)":"var(--amber)", marginTop:2 }}><AlertIcon/></div>
              <div>
                <div className="modal-title" style={{ color:confirm.type==="delete"?"var(--red)":undefined }}>
                  {confirm.type==="freeze"?"Freeze Admin" : confirm.type==="unfreeze"?"Unfreeze Admin" : "Delete Admin"}
                </div>
                <div className="modal-sub" style={{ marginBottom:0 }}>
                  {confirm.type==="freeze" && `Freeze ${confirm.admin.name}? They will be immediately blocked from logging in. All data is preserved.`}
                  {confirm.type==="unfreeze" && `Restore full access to ${confirm.admin.name}? They can log in normally after this.`}
                  {confirm.type==="delete" && `Permanently delete ${confirm.admin.name}'s account? This action cannot be reversed.`}
                </div>
              </div>
            </div>
            {confirm.type==="freeze" && <div className="alert-ems alert-info" style={{marginBottom:12}}>Data is not deleted. Account can be unfrozen later.</div>}
            {confirm.type==="delete" && <div className="alert-ems alert-error" style={{marginBottom:12}}>This will permanently remove the account from the system.</div>}
            <div style={{ display:"flex", gap:10, justifyContent:"flex-end" }}>
              <button className="btn-ems btn-secondary-ems" onClick={() => setConfirm(null)}>Cancel</button>
              <button className={`btn-ems ${confirm.type==="delete"?"btn-danger-ems":confirm.type==="freeze"?"btn-secondary-ems":"btn-success-ems2"}`}
                onClick={executeAction}>
                Confirm {confirm.type.charAt(0).toUpperCase() + confirm.type.slice(1)}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

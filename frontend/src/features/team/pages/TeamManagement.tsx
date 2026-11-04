import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { LuCalendarDays, LuPencil, LuPlus, LuSearch, LuTrash2, LuUsers } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import { getEmployees } from "../../employees/services/EmployeeService";
import type { Team, TeamStatus, User } from "../../../shared/types/models";
import { Avatar, Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, PageHeader, Select, Skeleton, Tabs, Textarea, useConfirm, useToast, type Tone } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";

interface TeamFormState {
  name: string; description: string; projectName: string; deadline: string;
  status: TeamStatus; teamLeadId: number | ""; memberIds: number[];
}

const STATUS: Record<TeamStatus, { tone: Tone; label: string }> = {
  ACTIVE: { tone: "success", label: "Active" }, ON_HOLD: { tone: "warning", label: "On hold" },
  COMPLETED: { tone: "info", label: "Completed" }, CANCELLED: { tone: "danger", label: "Cancelled" },
};
const fmtDate = (d: string) => new Date(d + "T00:00").toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });

function TeamCard({ team, employees, canManage, onEdit, onDelete }: { team: Team; employees: User[]; canManage: boolean; onEdit: () => void; onDelete: () => void }) {
  // Non-admins can't list employees, so members fall back to their numbers.
  const emp = (id: number) => employees.find(e => Number(e.empId) === Number(id));
  const lead = team.teamLeadId ? emp(team.teamLeadId) : null;
  const s = STATUS[team.status] ?? STATUS.ACTIVE;
  const overdue = !!team.deadline && new Date(team.deadline) < new Date() && team.status === "ACTIVE";
  const members = team.memberIds || [];
  return (
    <Card className={`tn-team${overdue ? " is-overdue" : ""}`}>
      <div className="tn-team-head">
        <div style={{ minWidth: 0 }}>
          <h3 className="tn-team-name">{team.name}</h3>
          {team.projectName && <div className="tn-team-project">{team.projectName}</div>}
        </div>
        <div className="tn-team-tools">
          <Badge tone={s.tone}>{s.label}</Badge>
          {canManage && <>
            <Button variant="ghost" size="sm" aria-label={`Edit ${team.name}`} icon={<LuPencil size={15} />} onClick={onEdit} />
            <Button variant="ghost" size="sm" aria-label={`Delete ${team.name}`} icon={<LuTrash2 size={15} />} onClick={onDelete} />
          </>}
        </div>
      </div>
      {team.description && <p className="tn-team-desc">{team.description}</p>}
      <div className="tn-team-facts">
        {team.deadline && <span className={overdue ? "is-late" : ""}><LuCalendarDays size={14} aria-hidden="true" /> {fmtDate(team.deadline)}{overdue ? " · overdue" : ""}</span>}
        <span><LuUsers size={14} aria-hidden="true" /> {members.length} member{members.length === 1 ? "" : "s"}</span>
      </div>
      {team.teamLeadId && (
        <div className="tn-team-lead">
          <Avatar name={lead?.name ?? `#${team.teamLeadId}`} src={lead?.profileImageUrl} size={28} />
          <div><div className="tn-team-label">Team lead</div><div className="strong" style={{ fontSize: 13 }}>{lead?.name ?? `Employee #${team.teamLeadId}`}</div></div>
        </div>
      )}
      {members.length > 0 && (
        <div>
          <div className="tn-team-label">Members</div>
          <div className="tn-team-members">
            {members.slice(0, 8).map(id => { const m = emp(id); return <span key={id} title={m?.name ?? `Employee #${id}`}><Avatar name={m?.name ?? `#${id}`} src={m?.profileImageUrl} size={30} /></span>; })}
            {members.length > 8 && <span className="tn-team-more">+{members.length - 8}</span>}
          </div>
        </div>
      )}
    </Card>
  );
}

function TeamFormModal({ team, employees, saving, onClose, onSave }: { team: Team | null; employees: User[]; saving: boolean; onClose: () => void; onSave: (f: TeamFormState) => void }) {
  const [form, setForm] = useState<TeamFormState>(team ? {
    name: team.name || "", description: team.description || "", projectName: team.projectName || "", deadline: team.deadline || "",
    status: team.status || "ACTIVE", teamLeadId: team.teamLeadId ? Number(team.teamLeadId) : "", memberIds: (team.memberIds || []).map(Number),
  } : { name: "", description: "", projectName: "", deadline: "", status: "ACTIVE", teamLeadId: "", memberIds: [] });
  const [nameError, setNameError] = useState("");
  const [find, setFind] = useState("");
  const set = <K extends keyof TeamFormState>(k: K, v: TeamFormState[K]) => setForm(f => ({ ...f, [k]: v }));
  const toggle = (id: number) => set("memberIds", form.memberIds.includes(id) ? form.memberIds.filter(i => i !== id) : [...form.memberIds, id]);
  const shown = employees.filter(e => e.empId != null && `${e.name} ${e.department ?? ""}`.toLowerCase().includes(find.trim().toLowerCase()));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) { setNameError("Team name is required."); return; }
    onSave({ ...form, name: form.name.trim(), memberIds: form.memberIds.map(Number) });
  };

  return (
    <Modal open title={team ? "Edit team" : "Create team"} description={team ? "Update the team's details and people." : "Set up a new project team."} onClose={onClose} size="lg" dismissible={!saving}
      footer={<><Button onClick={onClose} disabled={saving}>Cancel</Button><Button variant="primary" type="submit" form="team-form" loading={saving}>{team ? "Save changes" : "Create team"}</Button></>}>
      <form id="team-form" onSubmit={submit} noValidate>
        <Field label="Team name" required error={nameError}><Input data-autofocus value={form.name} placeholder="e.g. Frontend squad" onChange={e => { set("name", e.target.value); setNameError(""); }} /></Field>
        <div className="tn-form-grid">
          <Field label="Project"><Input value={form.projectName} placeholder="e.g. Website relaunch" onChange={e => set("projectName", e.target.value)} /></Field>
          <Field label="Deadline"><Input type="date" value={form.deadline} onChange={e => set("deadline", e.target.value)} /></Field>
          <Field label="Status"><Select value={form.status} onChange={e => set("status", e.target.value as TeamStatus)}>{Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</Select></Field>
          <Field label="Team lead"><Select value={form.teamLeadId} onChange={e => set("teamLeadId", Number(e.target.value) || "")}>
            <option value="">No lead yet</option>{employees.map(e => <option key={e.empId} value={e.empId ?? ""}>{e.name}</option>)}</Select></Field>
        </div>
        <Field label="Description"><Textarea rows={2} value={form.description} placeholder="What is this team working on?" onChange={e => set("description", e.target.value)} /></Field>

        <div className="tn-label" id="team-members-label">Members ({form.memberIds.length} selected)</div>
        <div className="tn-search" style={{ margin: "6px 0 8px" }}><LuSearch size={16} aria-hidden="true" /><Input type="search" aria-label="Find people" placeholder="Find people" value={find} onChange={e => setFind(e.target.value)} /></div>
        <ul className="tn-pick" aria-labelledby="team-members-label">
          {shown.length === 0 && <li className="tn-pick-empty">No one matches.</li>}
          {shown.map(emp => {
            const id = Number(emp.empId); const on = form.memberIds.includes(id);
            return (
              <li key={id}><label className={`tn-pick-row${on ? " is-on" : ""}`}>
                <input type="checkbox" checked={on} onChange={() => toggle(id)} />
                <Avatar name={emp.name} src={emp.profileImageUrl} size={28} />
                <span><span className="strong" style={{ fontSize: 13 }}>{emp.name}</span><span className="tn-pick-sub">{emp.department || "No department"}</span></span>
              </label></li>
            );
          })}
        </ul>
      </form>
    </Modal>
  );
}

export default function TeamManagement({ isAdmin, empId: propEmpId }: { isAdmin: boolean; empId?: number }) {
  const toast = useToast();
  const confirmAction = useConfirm();
  const [teams, setTeams] = useState<Team[]>([]);
  const [employees, setEmployees] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [modal, setModal] = useState<Team | "create" | null>(null);
  const [saving, setSaving] = useState(false);
  const [filter, setFilter] = useState<TeamStatus | "ALL">("ALL");
  const myEmpId = Number(propEmpId || localStorage.getItem("empId") || 0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      if (isAdmin) {
        const [t, e] = await Promise.all([axiosInstance.get<Team[]>("/api/teams"), getEmployees()]);
        setTeams(t.data || []); setEmployees(e.data || []);
      } else {
        // People without the employee-list permission use the dedicated "my teams" endpoint.
        setTeams((await axiosInstance.get<Team[]>(`/api/teams/employee/${myEmpId}`)).data || []);
      }
      setFailed(false);
    } catch { setFailed(true); }
    finally { setLoading(false); }
  }, [isAdmin, myEmpId]);
  useEffect(() => { void load(); }, [load]);

  const handleSave = async (form: TeamFormState) => {
    setSaving(true);
    try {
      if (modal === "create") { const res = await axiosInstance.post<Team>("/api/teams", form); setTeams(p => [...p, res.data]); toast.success("Team created."); }
      else if (modal) { const res = await axiosInstance.put<Team>(`/api/teams/${modal.id}`, form); setTeams(p => p.map(t => t.id === modal.id ? res.data : t)); toast.success("Team updated."); }
      setModal(null);
    } catch (e) { toast.fail(e, "We couldn't save the team."); }
    finally { setSaving(false); }
  };

  const handleDelete = async (t: Team) => {
    if (!(await confirmAction({ title: `Delete ${t.name}?`, message: "The team is removed for everyone. This can't be undone.", confirmLabel: "Delete team", tone: "danger" }))) return;
    try { await axiosInstance.delete(`/api/teams/${t.id}`); setTeams(p => p.filter(x => x.id !== t.id)); toast.success("Team deleted."); }
    catch (e) { toast.error(errorMessage(e, "We couldn't delete the team.")); }
  };

  const count = (s: TeamStatus | "ALL") => s === "ALL" ? teams.length : teams.filter(t => t.status === s).length;
  const tabs = useMemo(() => (["ALL", "ACTIVE", "ON_HOLD", "COMPLETED", "CANCELLED"] as const).map(id => ({ id, label: id === "ALL" ? "All" : STATUS[id].label, count: count(id) })), [teams]); // eslint-disable-line react-hooks/exhaustive-deps
  const shown = filter === "ALL" ? teams : teams.filter(t => t.status === filter);

  return (
    <div>
      <PageHeader title={isAdmin ? "Teams" : "My teams"}
        subtitle={loading ? "Loading…" : isAdmin ? `${teams.length} team${teams.length === 1 ? "" : "s"} across all projects` : teams.length ? `You're in ${teams.length} team${teams.length === 1 ? "" : "s"}` : "Your assigned teams"}
        actions={isAdmin ? <Button variant="primary" icon={<LuPlus size={16} />} onClick={() => setModal("create")}>Create team</Button> : undefined} />

      {!loading && !failed && teams.length > 0 && <div style={{ marginBottom: 16 }}><Tabs tabs={tabs} value={filter} onChange={setFilter} label="Filter teams by status" /></div>}

      {loading ? (
        <div className="tn-team-grid">{[0, 1, 2].map(i => <Card key={i}><Skeleton width="55%" height={16} /><div style={{ height: 10 }} /><Skeleton width="35%" height={12} /><div style={{ height: 16 }} /><Skeleton height={12} /><div style={{ height: 6 }} /><Skeleton width="70%" height={12} /></Card>)}</div>
      ) : failed ? <Card><ErrorState message="Your teams couldn't be loaded." onRetry={() => void load()} /></Card>
      : shown.length === 0 ? <Card><EmptyState icon={<LuUsers size={24} />} title={teams.length ? "No teams with this status" : isAdmin ? "No teams yet" : "You're not in a team yet"}
          text={teams.length ? "Try another status." : isAdmin ? "Create a team to group people around a project." : "Ask your manager or an admin to add you to a team."}
          action={isAdmin && !teams.length ? <Button variant="primary" onClick={() => setModal("create")}>Create team</Button> : undefined} /></Card>
      : <div className="tn-team-grid">{shown.map(t => <TeamCard key={t.id} team={t} employees={employees} canManage={isAdmin} onEdit={() => setModal(t)} onDelete={() => void handleDelete(t)} />)}</div>}

      {modal && <TeamFormModal team={modal === "create" ? null : modal} employees={employees} saving={saving} onClose={() => setModal(null)} onSave={handleSave} />}
    </div>
  );
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { LuPencil, LuFilterX, LuPlus, LuSearch, LuTrash2, LuUserRoundX } from "react-icons/lu";
import { getEmployees, deleteEmployee, searchEmployees, deleteMultipleEmployees } from "../services/EmployeeService";
import type { User } from "../../../shared/types/models";
import { Avatar, Badge, Button, Card, EmptyState, ErrorState, Input, PageHeader, Pagination, Select, SkeletonList, useConfirm, useToast } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";

const ROLE_LABEL: Record<string, string> = { OWNER: "Owner", SUPER_ADMIN: "Super admin", ADMIN: "Admin", HR: "HR", MANAGER: "Manager", EMPLOYEE: "Employee", INTERN: "Intern" };

export default function EmployeeList() {
  const confirmAction = useConfirm();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const base = `/${location.pathname.split("/")[1]}`;
  const canEdit = can("user:update");
  const canCreate = can("user:create");

  const [rows, setRows] = useState<User[]>([]);
  const [term, setTerm] = useState("");
  const [dept, setDept] = useState(new URLSearchParams(location.search).get("department") || "");
  const [role, setRole] = useState("");
  const [status, setStatus] = useState<"" | "active" | "frozen">("");
  const [sort, setSort] = useState<"name" | "id" | "salary-desc" | "salary-asc">("name");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async (keyword: string) => {
    setLoading(true); setFailed(false);
    try {
      const res = keyword.trim() ? await searchEmployees(keyword.trim()) : await getEmployees();
      setRows(res.data); setSelected([]);
    } catch { setFailed(true); }
    finally { setLoading(false); }
  }, []);

  // Wait for a short pause in typing before asking the server.
  useEffect(() => {
    const t = setTimeout(() => { void load(term); }, term ? 300 : 0);
    return () => clearTimeout(t);
  }, [term, load]);

  const departments = useMemo(() => Array.from(new Set(rows.map(r => r.department).filter((d): d is string => !!d))).sort(), [rows]);
  const roles = useMemo(() => Array.from(new Set(rows.map(r => r.role))), [rows]);
  const filtering = !!(dept || role || status);
  const clearFilters = () => { setDept(""); setRole(""); setStatus(""); };

  const shown = useMemo(() => {
    const list = rows.filter(e => (!dept || e.department === dept) && (!role || e.role === role)
      && (!status || (status === "frozen" ? e.frozen : !e.frozen)));
    const cmp: Record<typeof sort, (a: User, b: User) => number> = {
      name: (a, b) => a.name.localeCompare(b.name),
      id: (a, b) => (a.empId ?? 0) - (b.empId ?? 0),
      "salary-desc": (a, b) => (b.salary ?? -1) - (a.salary ?? -1),
      "salary-asc": (a, b) => (a.salary ?? Infinity) - (b.salary ?? Infinity),
    };
    return [...list].sort(cmp[sort]);
  }, [rows, dept, role, status, sort]);
  // pagination runs over the filtered + sorted list, so search, filters and sorting always cover every employee
  const pageCount = Math.max(1, Math.ceil(shown.length / pageSize));
  const current = Math.min(page, pageCount - 1);
  const visible = useMemo(() => shown.slice(current * pageSize, (current + 1) * pageSize), [shown, current, pageSize]);
  useEffect(() => { setPage(0); }, [term, dept, role, status, sort, pageSize]);
  const ids = useMemo(() => visible.map(e => e.empId).filter((i): i is number => i != null && !visible.find(v => v.empId === i)?.primaryOwner), [visible]);
  const allChecked = ids.length > 0 && ids.every(i => selected.includes(i));

  const remove = async (e: User) => {
    if (e.empId == null) return;
    if (!(await confirmAction({ title: `Remove ${e.name}?`, message: "They will lose access to the workspace. This can't be undone.", confirmLabel: "Remove", tone: "danger" }))) return;
    try { await deleteEmployee(e.empId); toast.success(`${e.name} was removed.`); }
    catch (err) { toast.fail(err, "We couldn't remove this person."); }
    void load(term);
  };

  const removeSelected = async () => {
    if (!(await confirmAction({ title: `Remove ${selected.length} ${selected.length === 1 ? "person" : "people"}?`, message: "They will lose access to the workspace. This can't be undone.", confirmLabel: "Remove all", tone: "danger" }))) return;
    try { await deleteMultipleEmployees(selected); toast.success("The selected people were removed."); }
    catch (err) { toast.fail(err, "We couldn't remove the selected people."); }
    void load(term);
  };

  return (
    <div>
      <PageHeader title="Employees" subtitle={loading ? "Loading…" : `${shown.length} ${shown.length === 1 ? "person" : "people"}${filtering || term ? ` match${shown.length === 1 ? "es" : ""} (of ${rows.length})` : ""}`}
        actions={<>
          {selected.length > 0 && canEdit && <Button variant="danger" icon={<LuTrash2 size={15} />} onClick={removeSelected}>Remove ({selected.length})</Button>}
          {canCreate && <Button variant="primary" icon={<LuPlus size={16} />} onClick={() => navigate(`${base}/add`)}>Add employee</Button>}
        </>} />

      <Card pad={false}>
        <div className="tn-toolbar">
          <div className="tn-search">
            <LuSearch size={16} aria-hidden="true" />
            <Input type="search" aria-label="Search employees" placeholder="Search by name, email or department" value={term} onChange={e => setTerm(e.target.value)} />
          </div>
        </div>
        <div className="tn-toolbar" style={{ paddingTop: 0 }} role="group" aria-label="Filters">
          <Select aria-label="Department" value={dept} onChange={e => setDept(e.target.value)} style={{ width: "auto", minWidth: 150 }}>
            <option value="">All departments</option>{departments.map(d => <option key={d} value={d}>{d}</option>)}
          </Select>
          <Select aria-label="Role" value={role} onChange={e => setRole(e.target.value)} style={{ width: "auto", minWidth: 130 }}>
            <option value="">All roles</option>{roles.map(r => <option key={r} value={r}>{ROLE_LABEL[r] ?? r}</option>)}
          </Select>
          <Select aria-label="Status" value={status} onChange={e => setStatus(e.target.value as typeof status)} style={{ width: "auto", minWidth: 120 }}>
            <option value="">Any status</option><option value="active">Active</option><option value="frozen">Frozen</option>
          </Select>
          <Select aria-label="Sort by" value={sort} onChange={e => setSort(e.target.value as typeof sort)} style={{ width: "auto", minWidth: 150, marginLeft: "auto" }}>
            <option value="name">Name A to Z</option><option value="id">Employee ID</option>
            {canEdit && <><option value="salary-desc">Salary, high to low</option><option value="salary-asc">Salary, low to high</option></>}
          </Select>
          {filtering && <Button variant="ghost" size="sm" icon={<LuFilterX size={15} />} onClick={clearFilters}>Clear filters</Button>}
        </div>

        {loading ? <div style={{ padding: 20 }}><SkeletonList rows={5} /></div>
          : failed ? <ErrorState message="The employee list couldn't be loaded." onRetry={() => void load(term)} />
          : shown.length === 0 ? <EmptyState icon={<LuUserRoundX size={24} />} title={term || filtering ? "No one matches these filters" : "No employees yet"}
              text={term || filtering ? "Try a different search, or clear the filters." : "Add your first team member to get started."}
              action={!term && !filtering && canCreate ? <Button variant="primary" onClick={() => navigate(`${base}/add`)}>Add employee</Button> : undefined} />
          : (
            <div className="tn-table-wrap">
              <table className="tn-table tn-table-cards">
                <thead>
                  <tr>
                    {canEdit && <th style={{ width: 40 }}><input type="checkbox" aria-label="Select all" checked={allChecked} onChange={() => setSelected(allChecked ? [] : ids)} /></th>}
                    <th>Employee</th><th>Role</th><th>Department</th><th>Email</th>{canEdit && <th className="num">Salary</th>}{canEdit && <th style={{ width: 96 }}><span className="sr-only">Actions</span></th>}
                  </tr>
                </thead>
                <tbody>
                  {visible.map(e => {
                    const on = e.empId != null && selected.includes(e.empId);
                    return (
                      <tr key={e.id} className={on ? "is-selected" : ""}>
                        {canEdit && <td>{e.empId != null && !e.primaryOwner && <input type="checkbox" aria-label={`Select ${e.name}`} checked={on}
                          onChange={() => setSelected(s => on ? s.filter(i => i !== e.empId) : [...s, e.empId as number])} />}</td>}
                        <td><div style={{ display: "flex", alignItems: "center", gap: 10 }}><Avatar name={e.name} src={e.profileImageUrl} />
                          <div><div className="strong">{e.name}</div>{e.empId != null && <div style={{ fontSize: 12, color: "var(--text-3)" }}>ID {e.empId}</div>}</div></div></td>
                        <td data-label="Role"><Badge tone={e.frozen ? "danger" : "neutral"}>{e.frozen ? "Frozen" : ROLE_LABEL[e.role] ?? e.role}</Badge></td>
                        <td data-label="Department">{e.department || "–"}</td>
                        <td data-label="Email" style={{ wordBreak: "break-all" }}>{e.email}</td>
                        {canEdit && <td className="num" data-label="Salary">{e.salary != null ? `₹${Number(e.salary).toLocaleString("en-IN")}` : "–"}</td>}
                        {canEdit && <td><div style={{ display: "flex", gap: 4, justifyContent: "flex-end" }}>
                          {e.empId != null && <Button variant="ghost" size="sm" aria-label={`Edit ${e.name}`} icon={<LuPencil size={15} />} onClick={() => navigate(`${base}/update/${e.empId}`)} />}
                          {e.empId != null && !e.primaryOwner && <Button variant="ghost" size="sm" aria-label={`Remove ${e.name}`} icon={<LuTrash2 size={15} />} onClick={() => void remove(e)} />}
                        </div></td>}
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        {!loading && !failed && shown.length > 0 && (
          <div className="tn-pager-row">
            <label className="tn-pager-size">Rows per page
              <Select aria-label="Rows per page" value={pageSize} onChange={e => setPageSize(Number(e.target.value))} style={{ width: "auto", minWidth: 72 }}>
                {[10, 25, 50, 100].map(n => <option key={n} value={n}>{n}</option>)}
              </Select>
            </label>
            <Pagination page={current} pageCount={pageCount} total={shown.length} onPage={setPage} label={shown.length === 1 ? "person" : "people"} />
          </div>
        )}
      </Card>
    </div>
  );
}

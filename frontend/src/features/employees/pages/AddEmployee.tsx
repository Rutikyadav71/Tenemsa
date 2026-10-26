import { useState, type ChangeEvent, type FormEvent } from "react";
import { addEmployee } from "../services/EmployeeService";
import { useNavigate } from "react-router-dom";
import type { EmployeeFormInput, Role } from "../../../shared/types/models";

const EyeIcon = ({open}: {open: boolean}) => open
  ? <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M1 1l22 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
  : <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="1.8"/><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8"/></svg>;

const departments = ["Engineering","Marketing","HR","Finance","Operations","Sales","Design","Product","Legal","Support"];

// Mirrors the backend's RoleAssignmentRegistry (who may create a user of
// which role) — purely a UX nicety so the dropdown doesn't offer options
// that would always be rejected server-side. The backend is the real
// enforcement point regardless; if this ever drifts out of sync with
// RoleAssignmentRegistry, the worst outcome is a confusing 403, not a
// security gap.
const ASSIGNABLE_ROLES: Record<string, { value: Role; label: string }[]> = {
  OWNER: [
    { value: "ADMIN", label: "Admin" }, { value: "HR", label: "HR" },
    { value: "MANAGER", label: "Manager" }, { value: "EMPLOYEE", label: "Employee" },
    { value: "INTERN", label: "Intern" },
  ],
  ADMIN: [{ value: "MANAGER", label: "Manager" }],
  HR: [{ value: "EMPLOYEE", label: "Employee" }, { value: "INTERN", label: "Intern" }],
};

export default function AddEmployee() {
  const navigate = useNavigate();
  const callerRole = localStorage.getItem("userRole") || "";
  const roleOptions = ASSIGNABLE_ROLES[callerRole] || [];
  const [employee, setEmployee] = useState<EmployeeFormInput>({empId:"",name:"",department:"",email:"",password:"",salary:"",role:roleOptions[0]?.value || "EMPLOYEE"});
  const [message, setMessage] = useState("");
  const [msgType, setMsgType] = useState("success");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setEmployee({...employee, [e.target.name]: e.target.value});

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    if (employee.password.length < 6) { setMessage("Password must be at least 6 characters."); setMsgType("error"); return; }
    setLoading(true);
    try {
      await addEmployee(employee);
      setMessage("Employee added successfully!"); setMsgType("success");
      setTimeout(() => navigate("/admin/employees"), 1500);
    } catch(err: any) {
      setMessage(err.response?.data?.message || "Failed to add employee."); setMsgType("error");
    } finally { setLoading(false); }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Add Employee</div>
          <div className="page-sub">Create a new team member account</div>
        </div>
      </div>

      <div style={{maxWidth:600,margin:"0 auto"}}>
        <div className="ems-card">
          {message && <div className={`alert-ems alert-${msgType}`}>{message}</div>}

          <form onSubmit={handleSubmit}>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0 16px"}}>
              {([
                {name:"empId",label:"Employee ID",placeholder:"e.g. 1001",type:"text"},
                {name:"name",label:"Full Name",placeholder:"Enter full name",type:"text"},
              ] as { name: keyof EmployeeFormInput; label: string; placeholder: string; type: string }[]).map(f => (
                <div className="form-group" key={f.name}>
                  <label className="form-label">{f.label}</label>
                  <input name={f.name} type={f.type} value={employee[f.name]} onChange={handleChange}
                    className="form-control-ems" placeholder={f.placeholder} required/>
                </div>
              ))}
            </div>

            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0 16px"}}>
              <div className="form-group">
                <label className="form-label">Department</label>
                <select name="department" value={employee.department} onChange={handleChange} className="form-control-ems" required>
                  <option value="">Select department</option>
                  {departments.map(d => <option key={d} value={d}>{d}</option>)}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Salary (₹)</label>
                <input name="salary" type="number" value={employee.salary} onChange={handleChange}
                  className="form-control-ems" placeholder="e.g. 50000" required/>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Email Address</label>
              <input name="email" type="email" value={employee.email} onChange={handleChange}
                className="form-control-ems" placeholder="employee@company.com" required autoComplete="email"/>
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <div className="input-wrap">
                <input name="password" type={showPassword ? "text" : "password"} value={employee.password}
                  onChange={handleChange} className="form-control-ems" placeholder="Min. 6 characters"
                  style={{paddingRight:40}} required autoComplete="new-password"/>
                <span className="input-icon-right" onClick={() => setShowPassword(v => !v)}><EyeIcon open={showPassword}/></span>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Role</label>
              <select name="role" value={employee.role} onChange={handleChange} className="form-control-ems">
                {roleOptions.map(opt => <option key={opt.value} value={opt.value}>{opt.label}</option>)}
              </select>
              {/* Admin/HR accounts specifically are created from Admin Management
                  (Owner-only), not here — see RoleAssignmentRegistry on the backend. */}
            </div>

            <div style={{display:"flex",gap:10,marginTop:8}}>
              <button type="button" className="btn-ems btn-secondary-ems" onClick={() => navigate("/admin/employees")}>Cancel</button>
              <button type="submit" className="btn-ems btn-primary-ems" disabled={loading} style={{flex:1}}>
                {loading ? "Adding..." : "Add Employee"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

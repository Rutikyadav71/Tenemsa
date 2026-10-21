import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import { updateEmployee, getEmployees } from "../services/EmployeeService";
import { useNavigate, useParams } from "react-router-dom";

interface UpdateEmployeeFormState {
  empId: string; name: string; department: string; salary: string;
  email: string; password: string; role: string;
}

const EyeIcon = ({open}: {open: boolean}) => open
  ? <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/><path d="M1 1l22 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>
  : <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="1.8"/><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8"/></svg>;

const departments = ["Engineering","Marketing","HR","Finance","Operations","Sales","Design","Product","Legal","Support"];

function UpdateEmployee() {
  const [employee, setEmployee] = useState<UpdateEmployeeFormState>({empId:"",name:"",department:"",salary:"",email:"",password:"",role:"EMPLOYEE"});
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const navigate = useNavigate();
  const { id } = useParams();

  useEffect(() => {
    getEmployees().then(res => {
      const emp = res.data.find(e => e.empId === parseInt(id ?? ""));
      if (emp) setEmployee({
        empId: String(emp.empId ?? ""),
        name: emp.name,
        department: emp.department ?? "",
        salary: emp.salary != null ? String(emp.salary) : "",
        email: emp.email,
        password: "",
        role: emp.role,
      });
      else navigate("/admin/employees");
    }).catch(() => navigate("/admin/employees"));
  }, [id]);

  const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setEmployee({...employee, [e.target.name]: e.target.value});

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (employee.password && employee.password.length < 6) { setMessage("Password must be at least 6 characters."); return; }
    setLoading(true);
    try {
      // role isn't sent — the backend's update endpoint never applies it
      // (see EmployeeServiceImpl.update on the backend), and it's not
      // editable from this form anyway.
      const { role: _role, ...payload } = employee;
      await updateEmployee(employee.empId, payload);
      navigate("/admin/employees");
    } catch(err: any) { setMessage(err.response?.data?.message || "Failed to update employee."); setLoading(false); }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Update Employee</div>
          <div className="page-sub">Edit employee #{id} information</div>
        </div>
      </div>

      <div style={{maxWidth:600,margin:"0 auto"}}>
        <div className="ems-card">
          {message && <div className="alert-ems alert-error">{message}</div>}

          <form onSubmit={handleSubmit}>
            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0 16px"}}>
              <div className="form-group">
                <label className="form-label">Employee ID</label>
                <input name="empId" value={employee.empId} className="form-control-ems" readOnly/>
              </div>
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <input name="name" value={employee.name} onChange={handleChange} className="form-control-ems" placeholder="Full name" required/>
              </div>
            </div>

            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0 16px"}}>
              <div className="form-group">
                <label className="form-label">Department</label>
                <select name="department" value={employee.department} onChange={handleChange} className="form-control-ems" required>
                  <option value="">Select department</option>
                  {departments.map(d => <option key={d} value={d}>{d}</option>)}
                  {employee.department && !departments.includes(employee.department) && (
                    <option value={employee.department}>{employee.department}</option>
                  )}
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Salary (₹)</label>
                <input name="salary" type="number" value={employee.salary} onChange={handleChange} className="form-control-ems" required/>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Email</label>
              <input name="email" type="email" value={employee.email} onChange={handleChange} className="form-control-ems" required autoComplete="email"/>
            </div>

            <div className="form-group">
              <label className="form-label">New Password <span style={{color:"var(--text-3)",fontWeight:400}}>(leave blank to keep current)</span></label>
              <div className="input-wrap">
                <input name="password" type={showPassword ? "text" : "password"} value={employee.password}
                  onChange={handleChange} className="form-control-ems" placeholder="Leave blank to keep current"
                  style={{paddingRight:40}} autoComplete="new-password"/>
                <span className="input-icon-right" onClick={() => setShowPassword(v => !v)}><EyeIcon open={showPassword}/></span>
              </div>
            </div>

            <div style={{display:"flex",gap:10,marginTop:8}}>
              <button type="button" className="btn-ems btn-secondary-ems" onClick={() => navigate("/admin/employees")}>Cancel</button>
              <button type="submit" className="btn-ems btn-primary-ems" disabled={loading} style={{flex:1}}>
                {loading ? "Saving..." : "Save Changes"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default UpdateEmployee;

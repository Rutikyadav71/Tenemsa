import { useState, type ChangeEvent, type FormEvent } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { useNavigate } from "react-router-dom";

export default function LeaveRequestForm() {
  const [form, setForm] = useState({empId:localStorage.getItem("empId") || "",startDate:"",endDate:"",reason:""});
  // Only Owner and HR may file leave for someone else; the server enforces this too.
  const canApplyForOthers = ["OWNER", "HR"].includes(localStorage.getItem("userRole") || "");
  const [message, setMessage] = useState("");
  const [msgType, setMsgType] = useState("success");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const today = new Date().toISOString().split("T")[0];

  const handleChange = (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({...form, [e.target.name]: e.target.value});

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    if (form.startDate > form.endDate) { setMessage("End date must be after start date."); setMsgType("error"); return; }
    setLoading(true);
    try {
      await axiosInstance.post("/api/leaves", form);
      setMessage("Leave request submitted successfully!"); setMsgType("success");
      setForm({empId:localStorage.getItem("empId") || "",startDate:"",endDate:"",reason:""});
    } catch(err: any) { setMessage(err.response?.data?.message || "Failed to submit leave."); setMsgType("error"); }
    finally { setLoading(false); }
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">Apply Leave</div>
          <div className="page-sub">Submit a leave request on behalf of an employee</div>
        </div>
      </div>

      <div style={{maxWidth:560,margin:"0 auto"}}>
        <div className="ems-card">
          {message && <div className={`alert-ems alert-${msgType}`}>{message}</div>}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label">Employee ID</label>
              <input name="empId" value={form.empId} onChange={handleChange} className="form-control-ems" placeholder="e.g. 1001" readOnly={!canApplyForOthers} required/>
            </div>

            <div style={{display:"grid",gridTemplateColumns:"1fr 1fr",gap:"0 16px"}}>
              <div className="form-group">
                <label className="form-label">Start Date</label>
                <input name="startDate" type="date" value={form.startDate} onChange={handleChange} className="form-control-ems" min={today} required/>
              </div>
              <div className="form-group">
                <label className="form-label">End Date</label>
                <input name="endDate" type="date" value={form.endDate} onChange={handleChange} className="form-control-ems" min={form.startDate || today} required/>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Reason</label>
              <textarea name="reason" value={form.reason} onChange={handleChange} className="form-control-ems"
                placeholder="Describe the reason for leave..." rows={4} required style={{resize:"vertical"}}/>
            </div>

            <div style={{display:"flex",gap:10}}>
              <button type="button" className="btn-ems btn-secondary-ems" onClick={() => navigate("/admin/dashboard")}>Cancel</button>
              <button type="submit" className="btn-ems btn-primary-ems" disabled={loading} style={{flex:1}}>
                {loading ? "Submitting..." : "Submit Leave Request"}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

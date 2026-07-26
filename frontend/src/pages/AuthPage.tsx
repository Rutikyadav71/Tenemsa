import React, { useState } from "react";
import axiosInstance from "../services/axiosInstance";
import { useNavigate, Link } from "react-router-dom";

const EyeIcon = ({ open }) => open
  ? <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M1 1l22 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
  : <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="1.8" /><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" /></svg>;

export default function AuthPage() {
  const [form, setForm] = useState({ email: "Employee1@gmail.com", password: "Employee@1" });
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async e => {
    e.preventDefault(); setMessage("");
    if (form.password.length < 6) { setMessage("Password must be at least 6 characters."); return; }
    setLoading(true);
    try {
      const res = await axiosInstance.post("/api/auth/login", form);
      const data = res.data;
      localStorage.clear();

      const token = data.token || data.accessToken || data.jwt;
      if (token) localStorage.setItem("token", token);

      localStorage.setItem("role", "USER");
      localStorage.setItem("name", data.name || "");
      localStorage.setItem("email", data.email || "");
      localStorage.setItem("empId", data.empId || "");
      // These now come from AuthResponse (department + salary added to backend)
      localStorage.setItem("department", data.department || "");
      localStorage.setItem("salary", data.salary != null ? String(data.salary) : "");

      navigate("/user/dashboard");
    } catch (err: any) {
      const msg = err.response?.data;
      if (typeof msg === "string" && msg.includes("ACCOUNT_FROZEN")) {
        setMessage("Your account has been frozen. Contact your administrator.");
      } else {
        setMessage(err.response?.data?.message || msg || "Invalid credentials.");
      }
    } finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div style={{ position: "relative", zIndex: 1, textAlign: "center", maxWidth: 400 }}>
          <div style={{
            width: 64, height: 64, background: "var(--teal)", borderRadius: 16,
            display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 24px",
            boxShadow: "0 0 40px var(--teal-glow)"
          }}>
            <svg width="30" height="30" fill="none" viewBox="0 0 24 24">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="white" strokeWidth="2" strokeLinecap="round" />
              <circle cx="12" cy="7" r="4" stroke="white" strokeWidth="2" />
            </svg>
          </div>
          <h1 style={{ fontSize: 30, fontWeight: 800, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 10, letterSpacing: "-0.5px" }}>
            Employee Portal
          </h1>
          <p style={{ fontSize: 15, color: "var(--text-3)", lineHeight: 1.7, marginBottom: 40 }}>
            View your profile, apply for leave, track approvals, and chat with your team.
          </p>
          {[["View Your Profile", "Salary, department & personal info"],
          ["Apply for Leave", "Submit and track leave requests"],
          ["Leave Notifications", "Real-time status updates"],
          ["Team Chat", "Message admins and colleagues"]].map(([t, s], i) => (
            <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 12, marginBottom: 14, textAlign: "left" }}>
              <div style={{
                width: 22, height: 22, borderRadius: 6, background: "var(--teal-glow)",
                display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0
              }}>
                <svg width="11" height="11" fill="none" viewBox="0 0 24 24">
                  <polyline points="20 6 9 17 4 12" stroke="var(--teal)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </div>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-1)" }}>{t}</div>
                <div style={{ fontSize: 12, color: "var(--text-3)" }}>{s}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="auth-right">
        <div style={{ marginBottom: 8, display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{
            width: 28, height: 28, background: "var(--teal)", borderRadius: 7,
            display: "flex", alignItems: "center", justifyContent: "center"
          }}>
            <svg width="14" height="14" fill="none" viewBox="0 0 24 24">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="white" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </div>
          <span style={{ fontFamily: "'Outfit',sans-serif", fontWeight: 700, fontSize: 15 }}>EMS</span>
        </div>

        <div className="auth-form-title">Welcome back</div>
        <div className="auth-form-sub">Sign in to your employee account</div>

        {message && <div className="alert-ems alert-error">{message}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input type="email" className="form-control-ems" value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              placeholder="your@email.com" required autoComplete="email" />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <div className="input-wrap">
              <input type={showPassword ? "text" : "password"} className="form-control-ems"
                value={form.password} onChange={e => setForm({ ...form, password: e.target.value })}
                placeholder="Enter your password" style={{ paddingRight: 40 }} required autoComplete="current-password" />
              <span className="input-icon-right" onClick={() => setShowPassword(v => !v)}>
                <EyeIcon open={showPassword} />
              </span>
            </div>
          </div>

          <button type="submit" className="btn-ems" disabled={loading}
            style={{
              width: "100%", justifyContent: "center", marginTop: 8,
              background: "var(--teal)", color: "white", border: "1px solid var(--teal)"
            }}>
            {loading ? "Signing in..." : "Sign In"}
          </button>
        </form>

        <div style={{ marginTop: 20, textAlign: "center" }}>
          <Link to="/" style={{ fontSize: 13, color: "var(--text-3)" }}>Back to home</Link>
        </div>

        <div style={{
          marginTop: 24, padding: "14px 16px", background: "var(--bg-elevated)",
          borderRadius: 8, border: "1px solid var(--border)", fontSize: 12, color: "var(--text-3)"
        }}>
          <div style={{ fontWeight: 600, color: "var(--text-2)", marginBottom: 4 }}>Demo Credentials</div>
          <div>Email: Employee1@gmail.com</div>
          <div>Password: Employee@1</div>
        </div>
      </div>
    </div>
  );
}

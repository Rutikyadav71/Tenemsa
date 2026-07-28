import { useEffect, useState, type FormEvent } from "react";
import axiosInstance from "../services/axiosInstance";
import { API_BASE_URL } from "../services/apiConfig";
import { storeAuthResponse } from "../services/authStorage";
import { useNavigate, Link } from "react-router-dom";
import type { AuthResponse } from "../types/models";

const EyeIcon = ({ open }: { open: boolean }) => open
  ? <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M1 1l22 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
  : <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="1.8" /><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" /></svg>;

export default function AdminAuthPage() {
  const [firstTime, setFirstTime] = useState(false); // no admins at all
  const [adminFull, setAdminFull] = useState(false); // 4 admins
  const [form, setForm] = useState({ name: "", email: "admin1@gmail.com", password: "Admin@1" });
  const [rememberMe, setRememberMe] = useState(false);
  const [message, setMessage] = useState("");
  const [msgType, setMsgType] = useState("error");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    axiosInstance.get<{ firstTime: boolean; full: boolean; exists: boolean }>("/api/admin/exists").then(res => {
      setFirstTime(res.data.firstTime);   // true = no admins exist yet
      setAdminFull(res.data.full);
    }).catch(() => { });
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    if (form.password.length < 6) {
      setMessage("Password must be at least 6 characters."); setMsgType("error"); return;
    }
    setLoading(true);
    try {
      // Only the very first admin uses public /register. Everyone else logs in.
      if (firstTime) {
        await axiosInstance.post("/api/admin/register", form);
        setMessage("Main admin registered! Check your email to verify your address, then log in."); setMsgType("success");
        setFirstTime(false);
      } else {
        const res = await axiosInstance.post<AuthResponse>("/api/admin/login", { ...form, rememberMe });
        storeAuthResponse(res.data, "ADMIN");
        // Kept for AdminProfile/AdminNavbar reads that predate the shared
        // storeAuthResponse helper's canonical "name"/"email" keys.
        localStorage.setItem("adminEmail", res.data.email || "");
        localStorage.setItem("adminName", res.data.name || "");
        navigate("/admin/dashboard");
      }
    } catch (err: any) {
      const msg = err.response?.data || "";
      if (typeof msg === "string" && msg.includes("ACCOUNT_FROZEN")) {
        setMessage("Your account has been frozen by the main administrator.");
      } else {
        setMessage(typeof msg === "string" ? msg : "Authentication failed.");
      }
      setMsgType("error");
    } finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      {/* Left panel */}
      <div className="auth-left">
        <div style={{ position: "relative", zIndex: 1, textAlign: "center", maxWidth: 400 }}>
          <div style={{
            width: 64, height: 64, background: "var(--accent)", borderRadius: 16,
            display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 24px",
            boxShadow: "0 0 40px var(--accent-glow)"
          }}>
            <svg width="30" height="30" fill="none" viewBox="0 0 24 24">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          <h1 style={{ fontSize: 32, fontWeight: 800, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 10, letterSpacing: "-0.5px" }}>
            Tenemsa Admin
          </h1>
          <p style={{ fontSize: 15, color: "var(--text-3)", lineHeight: 1.7, marginBottom: 40 }}>
            {firstTime
              ? "Welcome! Set up the first administrator account to get started."
              : "Sign in to manage your team, leaves, attendance, and more."}
          </p>
          {[
            ["Team Dashboard", "Real-time stats and analytics"],
            ["Leave Management", "Approve or reject with one click"],
            ["Attendance", "Track daily check-ins & work hours"],
            ["Team Management", "Create and manage project teams"],
            ["Admin Control", "Add up to 4 admins, freeze accounts"],
          ].map(([t, s], i) => (
            <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 12, marginBottom: 12, textAlign: "left" }}>
              <div style={{
                width: 20, height: 20, borderRadius: 5, background: "var(--accent-glow)",
                display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, marginTop: 1
              }}>
                <svg width="10" height="10" fill="none" viewBox="0 0 24 24">
                  <polyline points="20 6 9 17 4 12" stroke="var(--accent-light)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
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

      {/* Right form */}
      <div className="auth-right">
        <div style={{ marginBottom: 8, display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{
            width: 28, height: 28, background: "var(--accent)", borderRadius: 7,
            display: "flex", alignItems: "center", justifyContent: "center"
          }}>
            <svg width="14" height="14" fill="none" viewBox="0 0 24 24">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="white" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </div>
          <span style={{ fontFamily: "'Outfit',sans-serif", fontWeight: 700, fontSize: 15 }}>Tenemsa</span>
        </div>

        <div className="auth-form-title">
          {firstTime ? "Create Main Admin Account" : "Welcome back"}
        </div>
        <div className="auth-form-sub">
          {firstTime
            ? "This is the primary administrator account. Sub-admins are added from Admin Control after login."
            : "Sign in to your admin dashboard"}
        </div>

        {message && <div className={`alert-ems alert-${msgType}`}>{message}</div>}

        {!firstTime && (
          <>
            <a href={`${API_BASE_URL}/oauth2/authorization/google`}
              className="btn-ems btn-secondary-ems"
              style={{ width: "100%", justifyContent: "center", gap: 10, marginBottom: 16 }}>
              Continue with Google
            </a>
            <div style={{ display: "flex", alignItems: "center", gap: 10, margin: "4px 0 16px", color: "var(--text-3)", fontSize: 12 }}>
              <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
              or sign in with email
              <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
            </div>
          </>
        )}

        <form onSubmit={handleSubmit}>
          {firstTime && (
            <div className="form-group">
              <label className="form-label">Full Name</label>
              <input type="text" className="form-control-ems" value={form.name}
                onChange={e => setForm({ ...form, name: e.target.value })}
                placeholder="Your full name" required />
            </div>
          )}

          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input type="email" className="form-control-ems" value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              placeholder="admin@company.com" required autoComplete="email" />
          </div>

          <div className="form-group">
            <label className="form-label">Password</label>
            <div className="input-wrap">
              <input type={showPassword ? "text" : "password"} className="form-control-ems"
                value={form.password} onChange={e => setForm({ ...form, password: e.target.value })}
                placeholder="Min. 6 characters" style={{ paddingRight: 40 }}
                required autoComplete="current-password" />
              <span className="input-icon-right" onClick={() => setShowPassword(v => !v)}>
                <EyeIcon open={showPassword} />
              </span>
            </div>
          </div>

          {!firstTime && (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", margin: "4px 0 8px" }}>
              <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--text-3)", cursor: "pointer" }}>
                <input type="checkbox" checked={rememberMe} onChange={e => setRememberMe(e.target.checked)} />
                Remember me
              </label>
              <Link to="/forgot-password" style={{ fontSize: 13, color: "var(--accent-light)" }}>Forgot password?</Link>
            </div>
          )}

          <button type="submit" className="btn-ems btn-primary-ems"
            disabled={loading}
            style={{ width: "100%", justifyContent: "center", marginTop: 8 }}>
            {loading
              ? (firstTime ? "Creating account..." : "Signing in...")
              : (firstTime ? "Create Main Admin Account" : "Sign In")}
          </button>
        </form>

        {/* Info box - registration is closed if admins already exist */}
        {!firstTime && (
          <div style={{
            marginTop: 20, padding: "12px 14px", background: "var(--bg-elevated)",
            borderRadius: 8, border: "1px solid var(--border)", fontSize: 12, color: "var(--text-3)"
          }}>
            <div style={{ fontWeight: 600, color: "var(--text-2)", marginBottom: 4 }}>
              New admin?
            </div>
            Sub-admin accounts are created by the Main Administrator from the Admin Control panel after logging in.
          </div>
        )}

        <div style={{
          marginTop: 16, padding: "12px 14px", background: "var(--bg-elevated)",
          borderRadius: 8, border: "1px solid var(--border)", fontSize: 12, color: "var(--text-3)"
        }}>
          <div style={{ fontWeight: 600, color: "var(--text-2)", marginBottom: 4 }}>Demo Credentials</div>
          <div>Email: admin1@gmail.com</div>
          <div>Password: Admin@1</div>
        </div>
      </div>
    </div>
  );
}

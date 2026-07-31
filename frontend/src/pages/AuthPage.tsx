import { useState, type ChangeEvent, type FormEvent, useEffect } from "react";
import axiosInstance from "../services/axiosInstance";
import { API_BASE_URL } from "../services/apiConfig";
import { storeAuthResponse } from "../services/authStorage";
import { useNavigate, useSearchParams, Link } from "react-router-dom";
import type { AuthResponse } from "../types/models";

const EyeIcon = ({ open }: { open: boolean }) => open
  ? <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><path d="M1 1l22 22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
  : <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" strokeWidth="1.8" /><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" /></svg>;

const GoogleIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24">
    <path fill="#4285F4" d="M23.52 12.27c0-.85-.08-1.67-.22-2.45H12v4.64h6.47a5.53 5.53 0 0 1-2.4 3.63v3h3.87c2.27-2.09 3.58-5.17 3.58-8.82z"/>
    <path fill="#34A853" d="M12 24c3.24 0 5.95-1.07 7.94-2.91l-3.87-3c-1.08.72-2.45 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.27v3.1A12 12 0 0 0 12 24z"/>
    <path fill="#FBBC05" d="M5.27 14.28A7.2 7.2 0 0 1 4.89 12c0-.79.14-1.56.38-2.28v-3.1H1.27A12 12 0 0 0 0 12c0 1.94.46 3.77 1.27 5.38l4-3.1z"/>
    <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.69 1.27 6.62l4 3.1C6.22 6.86 8.87 4.75 12 4.75z"/>
  </svg>
);

export default function AuthPage() {
  const [form, setForm] = useState({ email: "Employee1@gmail.com", password: "Employee@1" });
  const [rememberMe, setRememberMe] = useState(false);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  // Google sign-in failures (no matching account, frozen account, etc.)
  // redirect back here with ?error=... — see backend's
  // OAuth2LoginSuccessHandler. Without this, that redirect silently landed
  // on a blank login form with no explanation of what went wrong.
  useEffect(() => {
    const oauthError = searchParams.get("error");
    if (oauthError) setMessage(oauthError);
  }, [searchParams]);

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    if (form.password.length < 6) { setMessage("Password must be at least 6 characters."); return; }
    setLoading(true);
    try {
      const res = await axiosInstance.post<AuthResponse>("/api/auth/login", { ...form, rememberMe });
      storeAuthResponse(res.data, "USER");
      navigate("/user/dashboard");
    } catch (err: any) {
      const msg = err.response?.data;
      if (typeof msg === "string" && msg.includes("ACCOUNT_FROZEN")) {
        setMessage("Your account has been frozen. Contact your administrator.");
      } else if (typeof msg === "string" && msg.includes("Google")) {
        setMessage(msg);
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
          <span style={{ fontFamily: "'Outfit',sans-serif", fontWeight: 700, fontSize: 15 }}>Tenemsa</span>
        </div>

        <div className="auth-form-title">Welcome back</div>
        <div className="auth-form-sub">Sign in to your employee account</div>

        {message && <div className="alert-ems alert-error">{message}</div>}

        <a href={`${API_BASE_URL}/oauth2/authorization/google`}
          className="btn-ems btn-secondary-ems"
          style={{ width: "100%", justifyContent: "center", gap: 10, marginBottom: 16 }}>
          <GoogleIcon /> Continue with Google
        </a>
        <div style={{ display: "flex", alignItems: "center", gap: 10, margin: "4px 0 16px", color: "var(--text-3)", fontSize: 12 }}>
          <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
          or sign in with email
          <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Email Address</label>
            <input type="email" name="email" className="form-control-ems" value={form.email}
              onChange={handleChange}
              placeholder="your@email.com" required autoComplete="email" />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <div className="input-wrap">
              <input type={showPassword ? "text" : "password"} name="password" className="form-control-ems"
                value={form.password} onChange={handleChange}
                placeholder="Enter your password" style={{ paddingRight: 40 }} required autoComplete="current-password" />
              <span className="input-icon-right" onClick={() => setShowPassword(v => !v)}>
                <EyeIcon open={showPassword} />
              </span>
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", margin: "4px 0 8px" }}>
            <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--text-3)", cursor: "pointer" }}>
              <input type="checkbox" checked={rememberMe} onChange={e => setRememberMe(e.target.checked)} />
              Remember me
            </label>
            <Link to="/forgot-password" style={{ fontSize: 13, color: "var(--teal)" }}>Forgot password?</Link>
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

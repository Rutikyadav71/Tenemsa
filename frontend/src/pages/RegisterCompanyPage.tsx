import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { registerCompany, rememberWorkspace } from "../services/companyService";
import { storeAuthResponse } from "../services/authStorage";

export default function RegisterCompanyPage() {
  const [form, setForm] = useState({ companyName: "", ownerName: "", email: "", password: "" });
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const navigate = useNavigate();

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    if (form.password.length < 8) { setMessage("Password must be at least 8 characters."); return; }
    setLoading(true);
    try {
      const resp = await registerCompany(form);
      storeAuthResponse(resp, "ADMIN");
      localStorage.setItem("adminEmail", resp.email || "");
      localStorage.setItem("adminName", resp.name || "");
      const slug = resp.companySlug;
      if (slug) rememberWorkspace(slug);
      navigate("/admin/dashboard");
    } catch (err: any) {
      const d = err.response?.data;
      setMessage(d?.message || (typeof d === "string" ? d : "Could not create the workspace."));
    } finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div style={{ position: "relative", zIndex: 1, textAlign: "center", maxWidth: 400 }}>
          <h1 style={{ fontSize: 32, fontWeight: 800, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 10 }}>
            Create your workspace
          </h1>
          <p style={{ fontSize: 15, color: "var(--text-3)", lineHeight: 1.7, marginBottom: 32 }}>
            Every company on Tenemsa gets its own private workspace. Your data is never visible to other companies.
          </p>
          {[
            ["You become the Owner", "Full control, including company settings"],
            ["Invite your people", "Add admins, HR, managers and employees"],
            ["Role-based access", "Everyone sees only what their role allows"],
          ].map(([t, s], i) => (
            <div key={i} style={{ textAlign: "left", marginBottom: 14 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-1)" }}>{t}</div>
              <div style={{ fontSize: 12, color: "var(--text-3)" }}>{s}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="auth-right">
        <div style={{ fontFamily: "'Outfit',sans-serif", fontWeight: 700, fontSize: 15, marginBottom: 8 }}>Tenemsa</div>
        <div className="auth-form-title">Set up your company</div>
        <div className="auth-form-sub">Takes under a minute. You'll be signed in as the workspace Owner.</div>

        {message && <div className="alert-ems alert-error">{message}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Company name</label>
            <input className="form-control-ems" value={form.companyName} onChange={set("companyName")}
              placeholder="Acme Industries" required />
          </div>
          <div className="form-group">
            <label className="form-label">Your full name</label>
            <input className="form-control-ems" value={form.ownerName} onChange={set("ownerName")}
              placeholder="Jane Doe" required />
          </div>
          <div className="form-group">
            <label className="form-label">Work email</label>
            <input type="email" className="form-control-ems" value={form.email} onChange={set("email")}
              placeholder="jane@acme.com" required autoComplete="email" />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <div className="input-wrap">
              <input type={showPassword ? "text" : "password"} className="form-control-ems"
                value={form.password} onChange={set("password")}
                placeholder="Min. 8 characters, mixed case & a number" style={{ paddingRight: 56 }}
                required autoComplete="new-password" />
              <span className="input-icon-right" style={{ fontSize: 11, cursor: "pointer" }}
                onClick={() => setShowPassword(v => !v)}>{showPassword ? "Hide" : "Show"}</span>
            </div>
          </div>
          <button type="submit" className="btn-ems btn-primary-ems" disabled={loading}
            style={{ width: "100%", justifyContent: "center", marginTop: 8 }}>
            {loading ? "Creating workspace..." : "Create workspace"}
          </button>
        </form>

        <div style={{ marginTop: 20, fontSize: 13, color: "var(--text-3)", textAlign: "center" }}>
          Already have a workspace? <Link to="/workspace" style={{ color: "var(--accent-light)" }}>Sign in</Link>
          <div style={{ marginTop: 8 }}><Link to="/" style={{ color: "var(--text-3)" }}>Back to home</Link></div>
        </div>
      </div>
    </div>
  );
}

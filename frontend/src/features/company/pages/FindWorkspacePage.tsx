import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { lookupWorkspace, getRememberedWorkspace, rememberWorkspace } from "../services/companyService";
import type { WorkspaceLookup } from "../../../shared/types/models";

export default function FindWorkspacePage() {
  const [slug, setSlug] = useState("");
  const [found, setFound] = useState<WorkspaceLookup | null>(null);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => { setSlug(getRememberedWorkspace()); }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage(""); setFound(null);
    if (!slug.trim()) return;
    setLoading(true);
    try {
      const ws = await lookupWorkspace(slug);
      if (ws) { setFound(ws); rememberWorkspace(ws.slug); }
      else setMessage("We couldn't find a workspace with that address. Check the spelling or ask your admin.");
    } catch {
      setMessage("Could not reach the server. Please try again.");
    } finally { setLoading(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div style={{ position: "relative", zIndex: 1, textAlign: "center", maxWidth: 400 }}>
          <h1 style={{ fontSize: 32, fontWeight: 800, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 10 }}>
            Find your workspace
          </h1>
          <p style={{ fontSize: 15, color: "var(--text-3)", lineHeight: 1.7 }}>
            Enter your company's workspace address, then sign in as an employee or an administrator.
          </p>
        </div>
      </div>

      <div className="auth-right">
        <div style={{ fontFamily: "'Outfit',sans-serif", fontWeight: 700, fontSize: 15, marginBottom: 8 }}>Tenemsa</div>
        <div className="auth-form-title">Sign in to your workspace</div>
        <div className="auth-form-sub">Your workspace address looks like <b>acme-industries</b></div>

        {message && <div className="alert-ems alert-error">{message}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Workspace address</label>
            <input className="form-control-ems" value={slug} onChange={e => { setSlug(e.target.value); setFound(null); }}
              placeholder="your-company" required autoFocus />
          </div>
          <button type="submit" className="btn-ems btn-primary-ems" disabled={loading}
            style={{ width: "100%", justifyContent: "center" }}>
            {loading ? "Looking up..." : "Continue"}
          </button>
        </form>

        {found && (
          <div style={{ marginTop: 20, padding: 18, background: "var(--bg-elevated)", border: "1px solid var(--border)", borderRadius: 10 }}>
            <div style={{ fontSize: 12, color: "var(--text-3)" }}>Workspace found</div>
            <div style={{ fontSize: 18, fontWeight: 700, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 14 }}>{found.name}</div>
            <div style={{ display: "flex", gap: 10 }}>
              <Link to={`/auth/employee?workspace=${found.slug}`} style={{ flex: 1 }}>
                <button className="btn-ems" style={{ width: "100%", justifyContent: "center", background: "var(--teal-glow)", color: "var(--teal)", border: "1px solid rgba(45,212,191,0.3)" }}>
                  I'm an employee
                </button>
              </Link>
              <Link to={`/auth/admin?workspace=${found.slug}`} style={{ flex: 1 }}>
                <button className="btn-ems btn-primary-ems" style={{ width: "100%", justifyContent: "center" }}>
                  I'm an admin
                </button>
              </Link>
            </div>
          </div>
        )}

        <div style={{ marginTop: 22, fontSize: 13, color: "var(--text-3)", textAlign: "center" }}>
          New to Tenemsa? <Link to="/register" style={{ color: "var(--accent-light)" }}>Create a workspace</Link>
          <div style={{ marginTop: 8 }}><Link to="/" style={{ color: "var(--text-3)" }}>Back to home</Link></div>
        </div>
      </div>
    </div>
  );
}

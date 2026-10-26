import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { acceptInvitation, previewInvitation } from "../services/invitationService";
import { rememberWorkspace } from "../services/companyService";
import { storeAuthResponse } from "../services/authStorage";
import type { InvitationPreview } from "../types/models";

export default function AcceptInvitePage() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const navigate = useNavigate();
  const [invite, setInvite] = useState<InvitationPreview | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "invalid">("loading");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!token) { setState("invalid"); return; }
    previewInvitation(token)
      .then(p => { if (p) { setInvite(p); setName(p.name); setState("ready"); } else setState("invalid"); })
      .catch(() => setState("invalid"));
  }, [token]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage(""); setSaving(true);
    try {
      const resp = await acceptInvitation(token, name, password);
      // Owner/Admin use the admin panel; every other role uses the employee portal.
      const isAdminTier = resp.role === "OWNER" || resp.role === "ADMIN";
      storeAuthResponse(resp, isAdminTier ? "ADMIN" : "USER");
      if (isAdminTier) {
        localStorage.setItem("adminEmail", resp.email || "");
        localStorage.setItem("adminName", resp.name || "");
      }
      if (resp.companySlug) rememberWorkspace(resp.companySlug);
      navigate(isAdminTier ? "/admin/dashboard" : "/user/dashboard");
    } catch (err: any) {
      const d = err.response?.data;
      setMessage(d?.message || (typeof d === "string" ? d : "Could not accept the invitation."));
    } finally { setSaving(false); }
  };

  return (
    <div className="auth-page">
      <div className="auth-right" style={{ margin: "0 auto" }}>
        <div style={{ fontFamily: "'Outfit',sans-serif", fontWeight: 700, fontSize: 15, marginBottom: 8 }}>Tenemsa</div>

        {state === "loading" && <div className="auth-form-sub">Checking your invitation...</div>}

        {state === "invalid" && (
          <>
            <div className="auth-form-title">Invitation not valid</div>
            <div className="auth-form-sub">This link is invalid, already used, or has expired. Ask your administrator to send a new one.</div>
            <Link to="/workspace" style={{ color: "var(--accent-light)" }}>Sign in to a workspace</Link>
          </>
        )}

        {state === "ready" && invite && (
          <>
            <div className="auth-form-title">Join {invite.companyName}</div>
            <div className="auth-form-sub">You were invited as <b>{invite.role}</b> with {invite.email}. Choose a password to finish.</div>
            {message && <div className="alert-ems alert-error">{message}</div>}
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label className="form-label">Your name</label>
                <input className="form-control-ems" value={name} onChange={e => setName(e.target.value)} required />
              </div>
              <div className="form-group">
                <label className="form-label">Password</label>
                <input type="password" className="form-control-ems" value={password}
                  onChange={e => setPassword(e.target.value)} required autoComplete="new-password"
                  placeholder="Min. 8 characters, mixed case & a number" />
              </div>
              <button type="submit" className="btn-ems btn-primary-ems" disabled={saving}
                style={{ width: "100%", justifyContent: "center" }}>
                {saving ? "Joining..." : "Accept & join"}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}

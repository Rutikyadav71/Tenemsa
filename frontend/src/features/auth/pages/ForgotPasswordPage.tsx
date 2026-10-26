import { useState, type FormEvent } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { Link } from "react-router-dom";
import { getRememberedWorkspace } from "../../company/services/companyService";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  // Optional: narrows the reset to one company when the same email is used in several workspaces.
  const [workspace, setWorkspace] = useState(getRememberedWorkspace());
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      // Backend deliberately returns the same response whether or not the
      // email exists (avoids confirming to an attacker which emails are
      // registered) — so this always shows the same success state.
      await axiosInstance.post("/api/auth/forgot-password", { email, workspace: workspace.trim() || undefined });
      setSubmitted(true);
    } catch {
      setError("Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Reset your password</h1>
        {submitted ? (
          <div className="auth-success">
            <p>If an account exists for <strong>{email}</strong>, we've sent a link to reset your password.</p>
            <p>Check your inbox — the link expires in 30 minutes.</p>
            <Link to="/auth/employee">Back to sign in</Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <p>Enter your email and we'll send you a link to reset your password.</p>
            <div className="form-group">
              <label className="form-label">Email</label>
              <input
                type="email"
                className="form-control-ems"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="form-group">
              <label className="form-label">Workspace address (optional)</label>
              <input className="form-control-ems" value={workspace} onChange={(e) => setWorkspace(e.target.value)}
                placeholder="your-company" />
            </div>
            {error && <p className="auth-error">{error}</p>}
            <button type="submit" className="btn-ems btn-primary-ems" disabled={loading}>
              {loading ? "Sending..." : "Send reset link"}
            </button>
            <Link to="/auth/employee" className="auth-link">Back to sign in</Link>
          </form>
        )}
      </div>
    </div>
  );
}

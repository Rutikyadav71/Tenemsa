import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import axiosInstance from "../../../shared/api/axiosInstance";
import { API_BASE_URL } from "../../../shared/api/apiConfig";
import { homeFor } from "../../../shared/lib/roles";
import { storeAuthResponse } from "../../../shared/lib/authStorage";
import { errorMessage } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";
import type { AuthResponse } from "../../../shared/types/models";
import { lookupWorkspace, rememberWorkspace } from "../../company/services/companyService";
import { Alert, Button, Field, Input } from "../../../shared/ui";
import AuthLayout from "./AuthLayout";
import { ADMIN_POINTS, EMPLOYEE_POINTS } from "./brandPoints";
import PasswordField from "./PasswordField";
import { takeSessionExpired } from "./sessionNotice";

const GoogleIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
    <path fill="#4285F4" d="M23.52 12.27c0-.85-.08-1.67-.22-2.45H12v4.64h6.47a5.53 5.53 0 0 1-2.4 3.63v3h3.87c2.27-2.09 3.58-5.17 3.58-8.82z" />
    <path fill="#34A853" d="M12 24c3.24 0 5.95-1.07 7.94-2.91l-3.87-3c-1.08.72-2.45 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.27v3.1A12 12 0 0 0 12 24z" />
    <path fill="#FBBC05" d="M5.27 14.28A7.2 7.2 0 0 1 4.89 12c0-.79.14-1.56.38-2.28v-3.1H1.27A12 12 0 0 0 0 12c0 1.94.46 3.77 1.27 5.38l4-3.1z" />
    <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.69 1.27 6.62l4 3.1C6.22 6.86 8.87 4.75 12 4.75z" />
  </svg>
);

/** One sign-in screen for both audiences; only the endpoint, wording and Google option differ. */
export default function LoginForm({ kind }: { kind: "employee" | "admin" }) {
  const admin = kind === "admin";
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const workspace = params.get("workspace") || "";
  const [workspaceName, setWorkspaceName] = useState("");
  const [form, setForm] = useState({ email: "", password: "" });
  const [rememberMe, setRememberMe] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [notice, setNotice] = useState<{ tone: "error" | "info"; text: string } | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (workspace) lookupWorkspace(workspace).then(w => setWorkspaceName(w?.name || "")).catch(() => { /* name is only decoration */ });
  }, [workspace]);

  // Google sign-in failures redirect back with ?error=...; an expired session sets a one-time flag.
  useEffect(() => {
    const oauthError = params.get("error");
    if (oauthError) setNotice({ tone: "error", text: oauthError });
    else if (takeSessionExpired()) setNotice({ tone: "info", text: "Your session has expired. Please sign in again." });
  }, [params]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setNotice(null);
    const next = { email: v.email(form.email), password: form.password ? "" : "Password is required." };
    setErrors(next);
    if (v.hasErrors(next)) return;
    setLoading(true);
    try {
      const res = await axiosInstance.post<AuthResponse>(admin ? "/api/admin/login" : "/api/auth/login",
        { ...form, email: form.email.trim(), rememberMe, workspace: workspace || undefined });
      storeAuthResponse(res.data);
      if (admin) {
        // Older screens still read these two keys.
        localStorage.setItem("adminEmail", res.data.email || "");
        localStorage.setItem("adminName", res.data.name || "");
      }
      if (res.data.companySlug) rememberWorkspace(res.data.companySlug);
      navigate(homeFor(res.data.role));
    } catch (err: unknown) {
      const raw = (err as { response?: { data?: unknown } }).response?.data;
      const text = typeof raw === "string" ? raw : (raw as { message?: string } | undefined)?.message ?? "";
      if (text.includes("WORKSPACE_SUSPENDED")) setNotice({ tone: "error", text: text.replace(/^.*WORKSPACE_SUSPENDED:\s*/s, "") || "This workspace has been suspended. Contact Tenemsa support." });
      else if (text.includes("ACCOUNT_FROZEN")) setNotice({ tone: "error", text: admin ? "Your account has been frozen by the main administrator." : "Your account has been frozen. Contact your administrator." });
      else if (text.includes("Google")) setNotice({ tone: "error", text });
      else setNotice({ tone: "error", text: errorMessage(err, "We couldn't sign you in. Check your details and try again.") });
    } finally { setLoading(false); }
  };

  const wsQuery = workspace ? `?workspace=${encodeURIComponent(workspace)}` : "";

  return (
    <AuthLayout
      title="Welcome back"
      subtitle={workspaceName ? <>Sign in to <b>{workspaceName}</b></> : admin ? "Sign in to manage your workspace." : "Sign in to your Tenemsa account."}
      heading={admin ? "Your workspace, under your control." : undefined}
      lead={admin ? "Manage people, access and company settings from one secure place." : undefined}
      points={admin ? ADMIN_POINTS : EMPLOYEE_POINTS}>
      {notice && <div style={{ marginBottom: 16 }}><Alert tone={notice.tone}>{notice.text}</Alert></div>}

      {!admin && (
        <>
          <a href={`${API_BASE_URL}/oauth2/authorization/google`} className="tn-btn tn-btn-secondary tn-btn-block au-google"><GoogleIcon /> Continue with Google</a>
          <div className="au-divider" style={{ margin: "16px 0" }}>or sign in with email</div>
        </>
      )}

      <form className="au-form" onSubmit={submit} noValidate>
        <Field label="Email" required error={errors.email}>
          <Input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} autoComplete="email" placeholder="you@company.com" autoFocus />
        </Field>
        <PasswordField value={form.password} onChange={p => setForm({ ...form, password: p })} error={errors.password} autoComplete="current-password" placeholder="Enter your password" />
        <div className="au-row">
          <label className="au-check"><input type="checkbox" checked={rememberMe} onChange={e => setRememberMe(e.target.checked)} /> Keep me signed in</label>
          <Link className="au-link" to="/forgot-password">Forgot password?</Link>
        </div>
        <Button type="submit" variant="primary" block loading={loading}>{loading ? "Signing in…" : "Sign in"}</Button>
      </form>

      <div className="au-switch">
        <Link className="au-link" to="/workspace">Use a different workspace</Link>
        <span>{admin ? <>Not an admin? <Link className="au-link" to={`/auth/employee${wsQuery}`}>Employee sign in</Link></> : <>Administrator? <Link className="au-link" to={`/auth/admin${wsQuery}`}>Admin sign in</Link></>}</span>
      </div>
    </AuthLayout>
  );
}

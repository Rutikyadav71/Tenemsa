import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { LuMailCheck } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import { errorMessage } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";
import { Alert, Button, Field, Input } from "../../../shared/ui";
import { getRememberedWorkspace } from "../../company/services/companyService";
import AuthLayout from "../components/AuthLayout";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  // Optional: narrows the reset to one company when the same email is used in several workspaces.
  const [workspace, setWorkspace] = useState(getRememberedWorkspace());
  const [emailError, setEmailError] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const err = v.email(email);
    setEmailError(err);
    if (err) return;
    setLoading(true);
    try {
      // The backend answers identically whether or not the email exists, so this always shows the same success state.
      await axiosInstance.post("/api/auth/forgot-password", { email: email.trim(), workspace: workspace.trim() || undefined });
      setSubmitted(true);
    } catch (e2) {
      setError(errorMessage(e2));
    } finally { setLoading(false); }
  };

  return (
    <AuthLayout title={submitted ? "Check your email" : "Reset your password"}
      subtitle={submitted ? undefined : "Enter your email and we'll send you a link to choose a new password."}>
      {submitted ? (
        <div className="au-status">
          <div className="big ok"><LuMailCheck size={26} /></div>
          <p className="sub" style={{ margin: 0 }}>If an account exists for <b>{email.trim()}</b>, we've sent a link to reset your password. It expires in 30 minutes.</p>
          <Link className="au-link" to="/auth/employee">Back to sign in</Link>
        </div>
      ) : (
        <form className="au-form" onSubmit={submit} noValidate>
          {error && <Alert tone="error">{error}</Alert>}
          <Field label="Email" required error={emailError}>
            <Input type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="email" autoFocus placeholder="you@company.com" />
          </Field>
          <Field label="Workspace address" hint="Only needed if you use the same email in more than one workspace.">
            <Input value={workspace} onChange={e => setWorkspace(e.target.value)} placeholder="your-company" />
          </Field>
          <Button type="submit" variant="primary" block loading={loading}>{loading ? "Sending…" : "Send reset link"}</Button>
          <div className="au-switch" style={{ marginTop: 0 }}><Link className="au-link" to="/auth/employee">Back to sign in</Link></div>
        </form>
      )}
    </AuthLayout>
  );
}

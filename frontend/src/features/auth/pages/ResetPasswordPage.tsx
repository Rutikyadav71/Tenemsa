import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { LuCircleCheck, LuLink } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import { errorMessage } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";
import { Alert, Button } from "../../../shared/ui";
import AuthLayout from "../components/AuthLayout";
import PasswordField from "../components/PasswordField";

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const setup = params.get("setup") === "1";   // new person following the welcome email
  const navigate = useNavigate();
  const [pw, setPw] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<{ pw?: string; confirm?: string }>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (!done) return;
    const t = setTimeout(() => navigate("/auth/employee"), 2500);
    return () => clearTimeout(t);
  }, [done, navigate]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const next = { pw: v.password(pw), confirm: pw !== confirm ? "The passwords don't match." : "" };
    setErrors(next);
    if (v.hasErrors(next)) return;
    setLoading(true);
    try {
      await axiosInstance.post("/api/auth/reset-password", { token, newPassword: pw });
      setDone(true);
    } catch (err) {
      setError(errorMessage(err, "This link is invalid or has expired. Request a new one."));
    } finally { setLoading(false); }
  };

  if (!token) {
    return (
      <AuthLayout title="This link isn't valid">
        <div className="au-status">
          <div className="big bad"><LuLink size={24} /></div>
          <p className="sub" style={{ margin: 0 }}>The reset link is incomplete. Request a new one and use the link from the latest email.</p>
          <Link className="tn-btn tn-btn-primary" to="/forgot-password">Request a new link</Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={done ? (setup ? "You're all set" : "Password updated") : setup ? "Set your password" : "Choose a new password"} subtitle={done ? undefined : setup ? "This activates your account. Pick something you haven't used elsewhere." : "Pick something you haven't used elsewhere."}>
      {done ? (
        <div className="au-status">
          <div className="big ok"><LuCircleCheck size={26} /></div>
          <p className="sub" style={{ margin: 0 }}>{setup ? "Your password is set and your email is confirmed. Taking you to sign in…" : "Your password has been changed. Taking you to sign in…"}</p>
          <Link className="au-link" to="/auth/employee">Sign in now</Link>
        </div>
      ) : (
        <form className="au-form" onSubmit={submit} noValidate>
          {error && <Alert tone="error">{error}</Alert>}
          <PasswordField label="New password" value={pw} onChange={setPw} error={errors.pw} autoComplete="new-password" autoFocus showRules />
          <PasswordField label="Confirm new password" value={confirm} onChange={setConfirm} error={errors.confirm} autoComplete="new-password" />
          <Button type="submit" variant="primary" block loading={loading}>{loading ? "Saving…" : setup ? "Set password" : "Update password"}</Button>
        </form>
      )}
    </AuthLayout>
  );
}

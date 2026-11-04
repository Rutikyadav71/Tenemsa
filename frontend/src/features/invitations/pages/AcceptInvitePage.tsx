import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { acceptInvitation, previewInvitation } from "../services/invitationService";
import { rememberWorkspace } from "../../company/services/companyService";
import { storeAuthResponse } from "../../../shared/lib/authStorage";
import { errorMessage } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";
import { Alert, Button, Field, Input } from "../../../shared/ui";
import AuthLayout from "../../auth/components/AuthLayout";
import PasswordField from "../../auth/components/PasswordField";
import type { InvitationPreview } from "../../../shared/types/models";

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
  const [errors, setErrors] = useState<{ name?: string; password?: string }>({});

  useEffect(() => {
    if (!token) { setState("invalid"); return; }
    previewInvitation(token)
      .then(p => { if (p) { setInvite(p); setName(p.name); setState("ready"); } else setState("invalid"); })
      .catch(() => setState("invalid"));
  }, [token]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault(); setMessage("");
    const next = { name: v.required(name, "Your name"), password: v.password(password) };
    setErrors(next);
    if (v.hasErrors(next)) return;
    setSaving(true);
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
    } catch (err) {
      setMessage(errorMessage(err, "We couldn't accept the invitation. Please try again."));
    } finally { setSaving(false); }
  };

  if (state === "loading") {
    return (
      <AuthLayout title="Checking your invitation…">
        <div className="au-status" role="status"><span className="tn-spinner" aria-hidden="true" /></div>
      </AuthLayout>
    );
  }

  if (state === "invalid" || !invite) {
    return (
      <AuthLayout title="This invitation isn't valid" subtitle="The link is invalid, already used, or has expired. Ask your administrator to send a new one.">
        <Link className="tn-btn tn-btn-primary" to="/workspace">Sign in to a workspace</Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={`Join ${invite.companyName}`}
      subtitle={<>You were invited as <b>{invite.role}</b> with {invite.email}. Choose a password to finish.</>}
      heading="You're invited." lead="Set your password and you'll land straight in your workspace.">
      <form className="au-form" onSubmit={handleSubmit} noValidate>
        {message && <Alert tone="error">{message}</Alert>}
        <Field label="Your name" required error={errors.name}>
          <Input value={name} onChange={e => setName(e.target.value)} autoComplete="name" autoFocus />
        </Field>
        <PasswordField value={password} onChange={setPassword} error={errors.password} autoComplete="new-password" showRules />
        <Button type="submit" variant="primary" block loading={saving}>{saving ? "Joining…" : "Accept and join"}</Button>
      </form>
    </AuthLayout>
  );
}

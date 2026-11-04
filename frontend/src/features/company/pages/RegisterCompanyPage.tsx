import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { registerCompany, rememberWorkspace } from "../services/companyService";
import { storeAuthResponse } from "../../../shared/lib/authStorage";
import { errorMessage, fieldErrors } from "../../../shared/lib/errors";
import * as v from "../../../shared/lib/validators";
import { Alert, Button, Field, Input } from "../../../shared/ui";
import AuthLayout from "../../auth/components/AuthLayout";
import { ADMIN_POINTS } from "../../auth/components/brandPoints";
import PasswordField from "../../auth/components/PasswordField";

type Form = { companyName: string; ownerName: string; email: string; password: string };

export default function RegisterCompanyPage() {
  const [form, setForm] = useState<Form>({ companyName: "", ownerName: "", email: "", password: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const set = (k: keyof Form) => (val: string) => setForm(f => ({ ...f, [k]: val }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setMessage("");
    const next = {
      companyName: v.required(form.companyName, "Company name"),
      ownerName: v.required(form.ownerName, "Your name"),
      email: v.email(form.email),
      password: v.password(form.password),
    };
    setErrors(next);
    if (v.hasErrors(next)) return;
    setLoading(true);
    try {
      const resp = await registerCompany({ ...form, email: form.email.trim(), companyName: form.companyName.trim(), ownerName: form.ownerName.trim() });
      storeAuthResponse(resp, "ADMIN");
      localStorage.setItem("adminEmail", resp.email || "");
      localStorage.setItem("adminName", resp.name || "");
      if (resp.companySlug) rememberWorkspace(resp.companySlug);
      navigate("/admin/dashboard");
    } catch (err) {
      const fe = fieldErrors(err);
      if (Object.keys(fe).length) setErrors(fe);
      setMessage(errorMessage(err, "We couldn't create the workspace. Please try again."));
    } finally { setLoading(false); }
  };

  return (
    <AuthLayout title="Set up your company" subtitle="It takes under a minute. You'll be signed in as the workspace Owner."
      heading="Give your company its own private workspace." lead="Every company on Tenemsa is separate. Your data is never visible to anyone else." points={ADMIN_POINTS}>
      <form className="au-form" onSubmit={submit} noValidate>
        {message && <Alert tone="error">{message}</Alert>}
        <Field label="Company name" required error={errors.companyName}>
          <Input value={form.companyName} onChange={e => set("companyName")(e.target.value)} placeholder="Acme Industries" autoFocus autoComplete="organization" />
        </Field>
        <Field label="Your full name" required error={errors.ownerName}>
          <Input value={form.ownerName} onChange={e => set("ownerName")(e.target.value)} placeholder="Jane Doe" autoComplete="name" />
        </Field>
        <Field label="Work email" required error={errors.email}>
          <Input type="email" value={form.email} onChange={e => set("email")(e.target.value)} placeholder="jane@acme.com" autoComplete="email" />
        </Field>
        <PasswordField value={form.password} onChange={set("password")} error={errors.password} autoComplete="new-password" showRules />
        <Button type="submit" variant="primary" block loading={loading}>{loading ? "Creating workspace…" : "Create workspace"}</Button>
      </form>
      <div className="au-switch">
        <span>Already have a workspace? <Link className="au-link" to="/workspace">Sign in</Link></span>
        <Link className="au-link" to="/">Back to home</Link>
      </div>
    </AuthLayout>
  );
}

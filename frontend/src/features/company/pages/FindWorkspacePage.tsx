import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { lookupWorkspace, getRememberedWorkspace, rememberWorkspace } from "../services/companyService";
import type { WorkspaceLookup } from "../../../shared/types/models";
import { errorMessage } from "../../../shared/lib/errors";
import { Alert, Button, Field, Input } from "../../../shared/ui";
import AuthLayout from "../../auth/components/AuthLayout";

export default function FindWorkspacePage() {
  const [slug, setSlug] = useState(getRememberedWorkspace());
  const [found, setFound] = useState<WorkspaceLookup | null>(null);
  const [error, setError] = useState("");
  const [fieldError, setFieldError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(""); setFound(null);
    if (!slug.trim()) { setFieldError("Enter your workspace address."); return; }
    setFieldError("");
    setLoading(true);
    try {
      const ws = await lookupWorkspace(slug.trim());
      if (ws) { setFound(ws); rememberWorkspace(ws.slug); }
      else setError("We couldn't find a workspace with that address. Check the spelling or ask your administrator.");
    } catch (err) {
      setError(errorMessage(err));
    } finally { setLoading(false); }
  };

  return (
    <AuthLayout title="Find your workspace" subtitle={<>Your workspace address looks like <b>acme-industries</b>.</>}>
      <form className="au-form" onSubmit={submit} noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Workspace address" required error={fieldError}>
          <Input value={slug} onChange={e => { setSlug(e.target.value); setFound(null); }} placeholder="your-company" autoFocus autoCapitalize="none" />
        </Field>
        <Button type="submit" variant="primary" block loading={loading}>{loading ? "Looking up…" : "Continue"}</Button>
      </form>

      {found && (
        <div className="au-ws" style={{ marginTop: 20 }}>
          <div><small>Workspace found</small><br /><strong>{found.name}</strong></div>
          <div className="au-row" style={{ flexWrap: "nowrap" }}>
            <Link className="tn-btn tn-btn-secondary" style={{ flex: 1 }} to={`/auth/employee?workspace=${encodeURIComponent(found.slug)}`}>I'm an employee</Link>
            <Link className="tn-btn tn-btn-primary" style={{ flex: 1 }} to={`/auth/admin?workspace=${encodeURIComponent(found.slug)}`}>I'm an admin</Link>
          </div>
        </div>
      )}

      <div className="au-switch">
        <span>New to Tenemsa? <Link className="au-link" to="/register">Create a workspace</Link></span>
        <Link className="au-link" to="/">Back to home</Link>
      </div>
    </AuthLayout>
  );
}

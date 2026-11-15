import { Link, useSearchParams } from "react-router-dom";
import { Alert, Button, PageHeader } from "../../../shared/ui";
import { workspaceFor } from "../../../shared/lib/roles";

const TEXT: Record<string, { tone: "success" | "error" | "info"; text: string }> = {
  ok: { tone: "success", text: "Connected. You can now close this and use the app from Tenemsa." },
  denied: { tone: "info", text: "You chose not to allow access, so nothing was connected." },
  off: { tone: "error", text: "This app is switched off for your company. Ask an owner or admin to switch it on." },
  error: { tone: "error", text: "We couldn't finish connecting. The sign-in may have expired, or your plan's connection limit was reached. Please try again." },
};

/** Where an app sends the browser back to after sign-in. */
export default function ConnectionsDone() {
  const [q] = useSearchParams();
  const r = TEXT[q.get("status") || "error"] ?? TEXT.error;
  const ws = workspaceFor(localStorage.getItem("userRole"));
  const signedIn = !!localStorage.getItem("token");
  return (
    <div style={{ maxWidth: 520, margin: "10vh auto", padding: 16 }}>
      <PageHeader title="Connected apps" />
      <Alert tone={r.tone}>{r.text}</Alert>
      <div style={{ marginTop: 16 }}>
        <Link to={signedIn ? `/${ws}/connections` : "/"}><Button variant="primary">{signedIn ? "Back to connected apps" : "Go to sign in"}</Button></Link>
      </div>
    </div>
  );
}

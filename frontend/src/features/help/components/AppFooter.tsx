import { Link } from "react-router-dom";
import type { Workspace } from "../../../shared/lib/roles";
import { SITE } from "../../../shared/lib/site";
import "./help.css";

/** The slim footer at the bottom of every signed-in page. */
export default function AppFooter({ workspace }: { workspace: Workspace }) {
  return (
    <footer className="hl-foot">
      <span>© {new Date().getFullYear()} {SITE.legalName || SITE.name}{SITE.version ? ` · ${SITE.version}` : ""}</span>
      <nav aria-label="Help and legal">
        <Link to={`/${workspace}/help`}>Help</Link>
        <Link to="/privacy">Privacy</Link>
        <Link to="/terms">Terms</Link>
        <Link to="/contact">Contact</Link>
      </nav>
    </footer>
  );
}

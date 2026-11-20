import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { LuCircleHelp } from "react-icons/lu";
import { Button } from "../../../shared/ui";
import type { Workspace } from "../../../shared/lib/roles";
import { findItem, toPath } from "../../../layouts/shell/navConfig";
import { guideForNav } from "../content";
import "./help.css";

const key = (id: string) => `tenemsa-hint:${id}`;
const read = (id: string) => { try { return localStorage.getItem(key(id)) === "1"; } catch { return false; } };

/**
 * A short "How this works" card at the top of a page's main screen. Open the first time, folded away once the
 * person says "Got it", and always one click away after that. The words come from the guide for that page.
 */
export default function PageHint({ workspace }: { workspace: Workspace }) {
  const { pathname } = useLocation();
  const match = findItem(workspace, pathname);
  // Only on the page's own screen, not on its detail screens (a single project, a single meeting).
  const guide = match && pathname.replace(/\/$/, "") === toPath(workspace, match.item) ? guideForNav(match.item.id) : undefined;
  const [open, setOpen] = useState(false);

  useEffect(() => { setOpen(guide ? !read(guide.id) : false); }, [guide?.id]);
  if (!guide) return null;

  const dismiss = () => {
    try { localStorage.setItem(key(guide.id), "1"); } catch { /* it will show again next time */ }
    setOpen(false);
  };

  if (!open) {
    return (
      <div className="hl-hint-closed">
        <button type="button" onClick={() => setOpen(true)} aria-expanded="false"><LuCircleHelp size={15} aria-hidden="true" /> How this works</button>
      </div>
    );
  }
  return (
    <section className="hl-hint" aria-label={`How this works: ${guide.title}`}>
      <div className="hl-hint-head">
        <h2><LuCircleHelp size={17} aria-hidden="true" /> How this works: {guide.title}</h2>
        <Link to={`/${workspace}/help#${guide.id}`} className="hl-link">Full guide</Link>
      </div>
      <ol>{guide.steps.map(s => <li key={s.title}><b>{s.title}.</b> {s.text}</li>)}</ol>
      {guide.tips?.[0] && <p className="hl-tip">Tip: {guide.tips[0]}</p>}
      <div className="hl-hint-foot"><Button size="sm" variant="secondary" onClick={dismiss}>Got it</Button></div>
    </section>
  );
}

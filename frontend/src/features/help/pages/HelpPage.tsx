import { useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { LuCircleHelp, LuSearch } from "react-icons/lu";
import { Button, EmptyState, Input, PageHeader } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";
import type { Workspace } from "../../../shared/lib/roles";
import { GUIDES, ROLE_GLOSSARY } from "../content";
import { showSetupChecklistAgain } from "../components/GettingStarted";
import { FAQ } from "../../marketing/content";
import "../components/help.css";

export default function HelpPage({ workspace }: { workspace: Workspace }) {
  const { hash } = useLocation();
  const [q, setQ] = useState("");
  const [shown, setShown] = useState(false);
  const target = hash.slice(1);

  const visible = useMemo(() => GUIDES.filter(g => !g.perms || can(...g.perms)), []);
  const list = useMemo(() => {
    const t = q.trim().toLowerCase();
    if (!t) return visible;
    return visible.filter(g => `${g.title} ${g.summary} ${g.steps.map(s => `${s.title} ${s.text}`).join(" ")}`.toLowerCase().includes(t));
  }, [q, visible]);

  useEffect(() => { if (target) document.getElementById(target)?.scrollIntoView({ block: "start" }); }, [target]);

  return (
    <div className="hl-page">
      <PageHeader title="Help" subtitle="Step-by-step guides for the things you can do here. Use the search at the top to jump straight to any page." />

      <div className="hl-search">
        <LuSearch size={16} aria-hidden="true" />
        <Input type="search" aria-label="Search the guides" placeholder="Search the guides, for example leave or invite" value={q} onChange={e => setQ(e.target.value)} />
      </div>

      {can("user:create") && (
        <p className="hl-restore">
          <Button size="sm" variant="ghost" onClick={() => { showSetupChecklistAgain(); setShown(true); }}>Show the setup checklist on my dashboard again</Button>
          {shown && <span> Done. <Link to={`/${workspace}/dashboard`}>Go to the dashboard</Link>.</span>}
        </p>
      )}

      {list.length === 0 ? <EmptyState icon={<LuCircleHelp size={22} />} title="No guide matches" text="Try a shorter word, or clear the search." /> : (
        <div className="hl-guides">
          {list.map(g => (
            <details key={g.id} id={g.id} className="hl-guide" open={g.id === target || undefined}>
              <summary><span><b>{g.title}</b><small>{g.summary}</small></span></summary>
              <ol>{g.steps.map(s => <li key={s.title}><b>{s.title}.</b> {s.text}</li>)}</ol>
              {g.tips && g.tips.length > 0 && <ul className="hl-tips">{g.tips.map(t => <li key={t}>{t}</li>)}</ul>}
              {g.path && <p><Link className="tn-btn tn-btn-secondary tn-btn-sm" to={`/${workspace}/${g.path}`}>Open it</Link></p>}
            </details>
          ))}
        </div>
      )}

      <h2 className="hl-h2">Who is who</h2>
      <dl className="hl-roles">{ROLE_GLOSSARY.map(r => <div key={r.role}><dt>{r.role}</dt><dd>{r.text}</dd></div>)}</dl>

      <h2 className="hl-h2">Common questions</h2>
      {FAQ.map(f => <details key={f.q} className="hl-guide hl-faq"><summary><span><b>{f.q}</b></span></summary><p>{f.a}</p></details>)}

      <p className="hl-more">Still stuck? <Link to="/contact">Contact us</Link>, or ask your company's owner or HR.</p>
    </div>
  );
}

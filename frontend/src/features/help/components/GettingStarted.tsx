import { useState } from "react";
import { Link } from "react-router-dom";
import { LuCheck, LuRocket } from "react-icons/lu";
import { Button, Card } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";
import "./help.css";

interface Item { id: string; title: string; text: string; to: string; label: string; perms?: string[] }

const ITEMS: Item[] = [
  { id: "company", title: "Check your company settings", text: "Look over the workspace details and decide whether AI is on.", to: "settings?tab=workspace", label: "Open settings", perms: ["company:manage"] },
  { id: "people", title: "Add your first people", text: "Add them yourself or send invitations. They choose their own password.", to: "add", label: "Add employee", perms: ["user:create"] },
  { id: "teams", title: "Group people into teams", text: "Teams let managers see and approve for their own people.", to: "teams", label: "Open teams", perms: ["team:manage:any"] },
  { id: "project", title: "Create a project", text: "Start a board so work has a home.", to: "projects", label: "Open projects", perms: ["project:view"] },
  { id: "announce", title: "Say hello to everyone", text: "Send a first announcement so people know where things are.", to: "announcements", label: "Write one", perms: ["announcement:send"] },
  { id: "plan", title: "Look at your plan", text: "See your limits and AI allowance, and choose a plan when you need more.", to: "billing", label: "Plan & billing", perms: ["billing:view"] },
];

const storeKey = () => `tenemsa-setup:${localStorage.getItem("companyName") || "company"}`;
interface Saved { done: string[]; hidden: boolean }
function load(): Saved {
  try { const v = JSON.parse(localStorage.getItem(storeKey()) || "null"); if (v && Array.isArray(v.done)) return { done: v.done, hidden: !!v.hidden }; } catch { /* start fresh */ }
  return { done: [], hidden: false };
}
export function showSetupChecklistAgain() {
  try { const s = load(); localStorage.setItem(storeKey(), JSON.stringify({ ...s, hidden: false })); } catch { /* nothing to do */ }
}

/**
 * A checklist for the person setting the company up. It cannot know what they have already done, so each step is
 * ticked by hand and remembered in this browser; it only offers steps the person's role can actually carry out.
 */
export default function GettingStarted({ base }: { base: string }) {
  const [s, setS] = useState<Saved>(load);
  const items = ITEMS.filter(i => !i.perms || can(...i.perms));
  if (s.hidden || items.length === 0) return null;

  const save = (next: Saved) => { setS(next); try { localStorage.setItem(storeKey(), JSON.stringify(next)); } catch { /* not remembered */ } };
  const toggle = (id: string) => save({ ...s, done: s.done.includes(id) ? s.done.filter(x => x !== id) : [...s.done, id] });
  const doneCount = items.filter(i => s.done.includes(i.id)).length;
  const all = doneCount === items.length;

  return (
    <Card className="hl-start">
      <div className="hl-start-head">
        <div>
          <h2><LuRocket size={18} aria-hidden="true" /> {all ? "You're all set" : "Set up your company"}</h2>
          <p>{all ? "Everything on the list is done. You can hide this." : `${doneCount} of ${items.length} done. Tick a step when you've finished it.`}</p>
        </div>
        <Button size="sm" variant="ghost" onClick={() => save({ ...s, hidden: true })}>Hide</Button>
      </div>
      <div className="hl-start-bar" role="progressbar" aria-valuemin={0} aria-valuemax={items.length} aria-valuenow={doneCount} aria-label="Setup progress"><i style={{ width: `${(doneCount / items.length) * 100}%` }} /></div>
      <ul className="hl-start-list">
        {items.map(i => {
          const done = s.done.includes(i.id);
          return (
            <li key={i.id} className={done ? "done" : ""}>
              <button type="button" role="checkbox" aria-checked={done} aria-label={`${i.title}: ${done ? "done" : "not done"}`} onClick={() => toggle(i.id)} className="hl-check">{done && <LuCheck size={14} aria-hidden="true" />}</button>
              <div><b>{i.title}</b><span>{i.text}</span></div>
              <Link to={`${base}/${i.to}`} className="tn-btn tn-btn-secondary tn-btn-sm">{i.label}</Link>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

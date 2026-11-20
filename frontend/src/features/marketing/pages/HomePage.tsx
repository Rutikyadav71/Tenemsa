import { Link } from "react-router-dom";
import { LuBookOpenText, LuCalendarCheck, LuClock3, LuKanban, LuLock, LuMessagesSquare, LuSparkles, LuUsersRound } from "react-icons/lu";
import PublicLayout from "../components/PublicLayout";
import HeroMock from "../components/HeroMock";
import WorkflowSteps from "../components/WorkflowSteps";
import Reveal from "../components/Reveal";
import { ROLES } from "../content";

const FEATURES = [
  { icon: <LuUsersRound size={20} />, title: "People", text: "Directory, teams and role-based access for everyone in the company." },
  { icon: <LuCalendarCheck size={20} />, title: "Leave", text: "Apply, approve and track time off, with team views for managers." },
  { icon: <LuClock3 size={20} />, title: "Attendance", text: "Daily check-ins and working hours at a glance." },
  { icon: <LuKanban size={20} />, title: "Projects", text: "Boards, sprints and backlogs, plus meetings turned into tasks." },
  { icon: <LuMessagesSquare size={20} />, title: "Chat", text: "Direct messages and file sharing without leaving the workspace." },
  { icon: <LuSparkles size={20} />, title: "Tenemsa AI", text: "Answers, drafts and summaries, with usage you can see and limit." },
  { icon: <LuBookOpenText size={20} />, title: "Knowledge base", text: "Ask questions across your documents and get answers with sources." },
  { icon: <LuLock size={20} />, title: "Private by design", text: "Every company has its own workspace. Nothing is shared between companies." },
];

export default function HomePage() {
  return (
    <PublicLayout>
      <section className="mk-hero">
        <div className="mk-hero-copy">
          <h1>Run your whole company <em>on Tenemsa</em></h1>
          <p>People, leave, attendance, projects, chat and an AI assistant in one secure workspace, with the right access for owners, admins, HR, managers and employees.</p>
          <div className="mk-cta">
            <Link className="tn-btn tn-btn-primary tn-btn-lg" to="/register">Create your workspace</Link>
            <Link className="tn-btn tn-btn-secondary tn-btn-lg" to="/how-it-works">See how it works</Link>
          </div>
          <p className="mk-hero-note">Start on the free plan. Already have a workspace? <Link to="/workspace">Sign in</Link>.</p>
        </div>
        <HeroMock />
      </section>

      <section className="mk-section" aria-labelledby="how">
        <Reveal><h2 id="how">How it works</h2><p className="mk-lead">From an empty company to a working day in four steps.</p></Reveal>
        <WorkflowSteps />
        <div className="mk-more"><Link to="/how-it-works">Read the full walkthrough</Link></div>
      </section>

      <section className="mk-section" aria-labelledby="inside">
        <Reveal><h2 id="inside">What's inside</h2></Reveal>
        <div className="mk-grid">
          {FEATURES.map((f, i) => (
            <Reveal key={f.title} delay={(i % 4) * 50} className="mk-feature">
              <div className="ic" aria-hidden="true">{f.icon}</div>
              <b>{f.title}</b><span>{f.text}</span>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mk-section" aria-labelledby="roles">
        <Reveal><h2 id="roles">The right view for every role</h2><p className="mk-lead">People only see what their role allows, and the server checks it again on every request.</p></Reveal>
        <div className="mk-grid mk-roles">
          {ROLES.map((r, i) => (
            <Reveal key={r.role} delay={i * 50} className="mk-role">
              <b>{r.role}</b><span>{r.text}</span>
              <ul>{r.can.map(c => <li key={c}>{c}</li>)}</ul>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mk-cta-band">
        <Reveal>
          <h2>Ready when you are</h2>
          <p>Set up a workspace in a few minutes. Add your people when you're ready.</p>
          <div className="mk-cta"><Link className="tn-btn tn-btn-primary tn-btn-lg" to="/register">Create your workspace</Link></div>
        </Reveal>
      </section>
    </PublicLayout>
  );
}

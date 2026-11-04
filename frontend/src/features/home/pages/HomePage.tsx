import { Link } from "react-router-dom";
import { LuBookOpenText, LuCalendarCheck, LuClock3, LuKanban, LuMessagesSquare, LuMoon, LuShieldCheck, LuSparkles, LuSun, LuUsersRound } from "react-icons/lu";
import { Logo } from "../../../shared/ui";
import { useTheme } from "../../../shared/theme/ThemeProvider";
import "./HomePage.css";

const FEATURES = [
  { icon: <LuUsersRound size={20} />, title: "People", text: "Directory, teams and role-based access for everyone in the company." },
  { icon: <LuCalendarCheck size={20} />, title: "Leave", text: "Apply, approve and track time off, with team views for managers." },
  { icon: <LuClock3 size={20} />, title: "Attendance", text: "Daily check-ins and working hours at a glance." },
  { icon: <LuKanban size={20} />, title: "Projects", text: "Boards, backlogs and meetings to keep work moving." },
  { icon: <LuMessagesSquare size={20} />, title: "Chat", text: "Direct messages and file sharing without leaving the workspace." },
  { icon: <LuSparkles size={20} />, title: "Tenemsa AI", text: "An assistant that answers questions, drafts documents and summarises meetings." },
  { icon: <LuBookOpenText size={20} />, title: "Knowledge base", text: "Ask questions across your company documents and get sourced answers." },
  { icon: <LuShieldCheck size={20} />, title: "Private by design", text: "Every company gets its own workspace. Nothing is shared between companies." },
];

export default function HomePage() {
  const { theme, setMode } = useTheme();
  const dark = theme === "dark";
  return (
    <div className="hp">
      <header className="hp-bar">
        <Logo height={32} />
        <nav aria-label="Primary">
          <button type="button" className="tn-btn tn-btn-ghost tn-btn-icon" onClick={() => setMode(dark ? "light" : "dark")}
            aria-label={dark ? "Switch to light theme" : "Switch to dark theme"}>{dark ? <LuSun size={18} /> : <LuMoon size={18} />}</button>
          <Link className="tn-btn tn-btn-secondary" to="/workspace">Sign in</Link>
        </nav>
      </header>
      <main>
        <section className="hp-hero">
          <h1>Run your whole company <em>on Tenemsa</em></h1>
          <p>People, leave, attendance, projects, chat and an AI assistant in one secure workspace, with the right access for owners, admins, HR, managers and employees.</p>
          <div className="hp-cta">
            <Link className="tn-btn tn-btn-primary tn-btn-lg" to="/register">Create your workspace</Link>
            <Link className="tn-btn tn-btn-secondary tn-btn-lg" to="/workspace">Find your workspace</Link>
          </div>
        </section>
        <section className="hp-grid" aria-label="What's inside">
          {FEATURES.map(f => (
            <div className="hp-feature" key={f.title}>
              <div className="ic" aria-hidden="true">{f.icon}</div>
              <b>{f.title}</b><span>{f.text}</span>
            </div>
          ))}
        </section>
      </main>
      <footer className="hp-foot">© {new Date().getFullYear()} Tenemsa</footer>
    </div>
  );
}

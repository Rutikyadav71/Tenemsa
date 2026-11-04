import type { ReactNode } from "react";
import { Logo } from "../../../shared/ui";
import { DEFAULT_POINTS, type BrandPoint } from "./brandPoints";
import "./auth.css";

interface Props {
  title: string;
  subtitle?: ReactNode;
  heading?: string;
  lead?: string;
  points?: BrandPoint[];
  children: ReactNode;
}

/** Shared frame for every signed-out screen: brand panel on wide screens, logo on top on small ones. */
export default function AuthLayout({ title, subtitle, heading = "Run your people and projects with confidence.", lead = "Tenemsa brings HR, work management and AI assistance into one secure workspace.", points = DEFAULT_POINTS, children }: Props) {
  return (
    <div className="au-page">
      <aside className="au-brand" aria-label="About Tenemsa">
        <Logo height={40} />
        <div>
          <h1>{heading}</h1>
          <p className="lead">{lead}</p>
          <ul className="au-points">
            {points.map(p => (
              <li key={p.title}><span className="ic" aria-hidden="true">{p.icon}</span><div><b>{p.title}</b><span>{p.text}</span></div></li>
            ))}
          </ul>
        </div>
        <div className="au-foot">© {new Date().getFullYear()} Tenemsa</div>
      </aside>
      <main className="au-main">
        <div className="au-card">
          <div className="au-mobile-logo"><Logo height={34} /></div>
          <h2>{title}</h2>
          {subtitle && <p className="sub">{subtitle}</p>}
          {children}
        </div>
      </main>
    </div>
  );
}

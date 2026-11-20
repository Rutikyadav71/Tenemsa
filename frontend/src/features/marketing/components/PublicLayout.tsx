import { useEffect, type ReactNode } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { LuMoon, LuSun } from "react-icons/lu";
import { Logo } from "../../../shared/ui";
import { useTheme } from "../../../shared/theme/ThemeProvider";
import { SITE } from "../../../shared/lib/site";
import "./marketing.css";

/** Header and footer shared by every public page (home, how it works, privacy, terms, contact). */
export default function PublicLayout({ children }: { children: ReactNode }) {
  const { theme, setMode } = useTheme();
  const dark = theme === "dark";
  const { pathname, hash } = useLocation();

  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView();
    else window.scrollTo(0, 0);
  }, [pathname, hash]);

  return (
    <div className="mk">
      <a className="sh-skip" href="#content">Skip to content</a>
      <header className="mk-bar">
        <Link to="/" aria-label="Tenemsa home"><Logo height={32} /></Link>
        <nav aria-label="Primary" className="mk-nav">
          <NavLink to="/how-it-works" className="mk-navlink">How it works</NavLink>
          <NavLink to="/contact" className="mk-navlink">Contact</NavLink>
          <button type="button" className="tn-btn tn-btn-ghost tn-btn-icon" onClick={() => setMode(dark ? "light" : "dark")}
            aria-label={dark ? "Switch to light theme" : "Switch to dark theme"}>{dark ? <LuSun size={18} /> : <LuMoon size={18} />}</button>
          <Link className="tn-btn tn-btn-secondary" to="/workspace">Sign in</Link>
        </nav>
      </header>
      <main id="content" tabIndex={-1}>{children}</main>
      <SiteFooter />
    </div>
  );
}

export function SiteFooter() {
  return (
    <footer className="mk-foot">
      <div className="mk-foot-grid">
        <div className="mk-foot-brand">
          <Logo height={28} />
          <p>People, leave, attendance, projects, chat and an AI assistant in one secure workspace for your company.</p>
        </div>
        <nav aria-label="Product">
          <h2>Product</h2>
          <Link to="/how-it-works">How it works</Link>
          <Link to="/how-it-works#roles">For every role</Link>
          <Link to="/how-it-works#faq">Questions</Link>
          <Link to="/register">Create a workspace</Link>
        </nav>
        <nav aria-label="Get started">
          <h2>Get started</h2>
          <Link to="/workspace">Sign in</Link>
          <Link to="/workspace">Find your workspace</Link>
          <Link to="/forgot-password">Forgot password</Link>
        </nav>
        <nav aria-label="Company and legal">
          <h2>Company</h2>
          <Link to="/contact">Contact us</Link>
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
        </nav>
      </div>
      <div className="mk-foot-base">
        <span>© {new Date().getFullYear()} {SITE.legalName || SITE.name}. All rights reserved.</span>
        {SITE.version && <span>{SITE.version}</span>}
      </div>
    </footer>
  );
}

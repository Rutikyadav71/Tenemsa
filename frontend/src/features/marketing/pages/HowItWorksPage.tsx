import { Link } from "react-router-dom";
import PublicLayout from "../components/PublicLayout";
import WorkflowSteps from "../components/WorkflowSteps";
import Reveal from "../components/Reveal";
import { FAQ, ROLES } from "../content";

export default function HowItWorksPage() {
  return (
    <PublicLayout>
      <section className="mk-page">
        <h1>How Tenemsa works</h1>
        <p className="mk-lead">Tenemsa is one workspace per company. The owner sets it up, people join it, and everyone sees the parts that fit their role.</p>
      </section>

      <section className="mk-section" aria-labelledby="steps">
        <h2 id="steps">Step by step</h2>
        <WorkflowSteps detailed />
      </section>

      <section className="mk-section" aria-labelledby="roles">
        <h2 id="roles">What each role does</h2>
        <div className="mk-grid mk-roles">
          {ROLES.map(r => (
            <Reveal key={r.role} className="mk-role"><b>{r.role}</b><span>{r.text}</span><ul>{r.can.map(c => <li key={c}>{c}</li>)}</ul></Reveal>
          ))}
        </div>
      </section>

      <section className="mk-section mk-narrow" aria-labelledby="faq">
        <h2 id="faq">Questions</h2>
        {FAQ.map(f => (
          <details key={f.q} className="mk-faq"><summary>{f.q}</summary><p>{f.a}</p></details>
        ))}
        <p className="mk-more">Something else? <Link to="/contact">Contact us</Link>.</p>
      </section>

      <section className="mk-cta-band">
        <h2>Try it with your own company</h2>
        <div className="mk-cta"><Link className="tn-btn tn-btn-primary tn-btn-lg" to="/register">Create your workspace</Link>
          <Link className="tn-btn tn-btn-secondary tn-btn-lg" to="/workspace">Sign in</Link></div>
      </section>
    </PublicLayout>
  );
}

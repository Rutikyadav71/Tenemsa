import { STEPS } from "../content";
import Reveal from "./Reveal";

/** The four-step story as a vertical timeline whose line and steps appear as the page scrolls. */
export default function WorkflowSteps({ detailed = false }: { detailed?: boolean }) {
  return (
    <ol className="mk-steps">
      {STEPS.map((s, i) => (
        <li key={s.n}>
          <Reveal delay={i * 60} className="mk-step">
            <span className="mk-step-n" aria-hidden="true">{s.n}</span>
            <div>
              <div className="mk-step-who">{s.who}</div>
              <h3>{s.title}</h3>
              <p>{s.text}</p>
              {detailed && <ul>{s.points.map(p => <li key={p}>{p}</li>)}</ul>}
            </div>
          </Reveal>
        </li>
      ))}
    </ol>
  );
}

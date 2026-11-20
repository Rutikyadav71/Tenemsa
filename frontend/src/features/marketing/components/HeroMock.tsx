/**
 * A small animated picture of the product (no screenshots to go stale): stats, a board card moving between
 * columns, and an AI answer being written. Decorative, so it is hidden from screen readers.
 */
export default function HeroMock() {
  return (
    <div className="mk-mock" aria-hidden="true">
      <div className="mk-mock-bar"><i /><i /><i /><span>acme.tenemsa</span></div>
      <div className="mk-mock-body">
        <div className="mk-mock-side">
          {["Dashboard", "People", "Leave", "Projects", "Chat", "AI"].map((t, i) => <b key={t} className={i === 0 ? "on" : ""}>{t}</b>)}
        </div>
        <div className="mk-mock-main">
          <div className="mk-mock-stats">
            <div><small>People</small><strong>48</strong></div>
            <div><small>Present today</small><strong className="up">41</strong></div>
            <div><small>Leave to review</small><strong className="warn">3</strong></div>
          </div>
          <div className="mk-mock-board">
            <div><small>To do</small><span className="c c1" /><span className="c c2" /></div>
            <div><small>In progress</small><span className="c c3" /><span className="c c4 move" /></div>
            <div><small>Done</small><span className="c c5" /></div>
          </div>
          <div className="mk-mock-ai">
            <span className="q">How much leave do I have left?</span>
            <span className="a"><i /><i /><i /></span>
            <span className="a2">You have 12 days left this year.</span>
          </div>
        </div>
      </div>
    </div>
  );
}

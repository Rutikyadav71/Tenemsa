import { useEffect, useRef } from "react";

/**
 * Decorative skyline for the dashboard header: a cloud, a growing city and a drifting sky.
 * Day and night are two different scenes, switched by the theme through CSS (hero.css).
 * Motion is choreographed rather than looped: the sun/moon travels an arc, a plane crosses now and then, cars stop and go,
 * windows light in a sweep and the chart line draws itself. All of it is transform/opacity only (GPU friendly), pauses when the
 * header is off-screen or the tab is hidden, adds a light pointer parallax on hover devices, and stops for reduced-motion users.
 * Purely visual, so it is hidden from screen readers.
 */
const BUILDINGS = [
  { x: 262, w: 46, h: 76, tone: 1 }, { x: 314, w: 40, h: 110, tone: 2 }, { x: 360, w: 54, h: 150, tone: 3 },
  { x: 420, w: 42, h: 98, tone: 2 }, { x: 468, w: 46, h: 64, tone: 1 },
];
const GROUND = 196;
const STARS = [[40, 26], [120, 50], [210, 18], [300, 36], [380, 14], [450, 40], [500, 22], [170, 78], [70, 90]];

export default function HeroScene() {
  const ref = useRef<SVGSVGElement>(null);

  useEffect(() => {
    const svg = ref.current;
    if (!svg) return;
    const hero = svg.closest<HTMLElement>(".db-hero");
    let visible = true;
    const sync = () => svg.setAttribute("data-paused", String(!visible || document.hidden));
    const io = new IntersectionObserver(([e]) => { visible = e.isIntersecting; sync(); }, { threshold: 0.05 });
    io.observe(svg);
    document.addEventListener("visibilitychange", sync);

    // parallax only where there is a real pointer and the user has not asked for less motion
    const fine = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
    const calm = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let raf = 0;
    const move = (ev: PointerEvent) => {
      if (raf || !hero) return;
      raf = requestAnimationFrame(() => {
        raf = 0;
        const r = hero.getBoundingClientRect();
        svg.style.setProperty("--px", String(((ev.clientX - r.left) / r.width - 0.5) * 2));
        svg.style.setProperty("--py", String(((ev.clientY - r.top) / r.height - 0.5) * 2));
      });
    };
    const leave = () => { svg.style.setProperty("--px", "0"); svg.style.setProperty("--py", "0"); };
    if (hero && fine && !calm) { hero.addEventListener("pointermove", move, { passive: true }); hero.addEventListener("pointerleave", leave); }
    return () => {
      io.disconnect(); document.removeEventListener("visibilitychange", sync);
      hero?.removeEventListener("pointermove", move); hero?.removeEventListener("pointerleave", leave);
      if (raf) cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <svg ref={ref} className="hero-scene" viewBox="0 0 540 210" preserveAspectRatio="xMaxYMax meet" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="hs-hill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="var(--hs-hill-a)" /><stop offset="1" stopColor="var(--hs-hill-b)" />
        </linearGradient>
        <radialGradient id="hs-glow"><stop offset="0" stopColor="var(--hs-glow)" stopOpacity=".9" /><stop offset="1" stopColor="var(--hs-glow)" stopOpacity="0" /></radialGradient>
      </defs>

      <g className="hs-par hs-par-sky">
      {/* sun by day, moon by night: each travels an arc across the sky */}
      <g className="hs-arc"><g className="hs-sun"><circle cx="150" cy="62" r="62" fill="url(#hs-glow)" /><circle cx="150" cy="62" r="20" fill="var(--hs-sun)" />
        <g className="hs-rays" stroke="var(--hs-sun)" strokeWidth="2.5" strokeLinecap="round">
          {Array.from({ length: 10 }, (_, i) => <line key={i} x1="150" y1="30" x2="150" y2="22" transform={`rotate(${i * 36} 150 62)`} />)}
        </g>
      </g>
      <g className="hs-moon"><circle cx="150" cy="62" r="64" fill="url(#hs-glow)" /><path d="M156 42 a20 20 0 1 0 12 32 a16 16 0 0 1 -12 -32 Z" fill="var(--hs-moon)" /></g></g>
      <g className="hs-stars">{STARS.map(([x, y], i) => <circle key={i} cx={x} cy={y} r={i % 3 === 0 ? 1.8 : 1.2} fill="#fff" style={{ animationDelay: `${(i * 0.45) % 3}s` }} />)}</g>

      {/* drifting clouds */}
      <g className="hs-drift hs-d1"><Cloud x={30} y={40} s={0.9} /></g>
      <g className="hs-drift hs-d2"><Cloud x={300} y={20} s={0.6} /></g>
      <g className="hs-drift hs-d3"><Cloud x={420} y={70} s={0.5} /></g>

      </g>

      {/* a small plane crosses now and then, leaving a fading trail */}
      <g className="hs-plane">
        <line x1="-46" y1="0" x2="-4" y2="0" stroke="var(--hs-trail)" strokeWidth="1.6" strokeLinecap="round" strokeDasharray="1 5" />
        <path d="M0 0 l-9 -5 l3 5 l-3 5 Z" fill="var(--hs-plane)" />
      </g>

      <g className="hs-par hs-par-city">
      {/* hills */}
      <path className="hs-wave" d="M-900 170 L0 168 C60 140 120 150 190 166 S330 186 400 160 S500 150 560 164 L560 210 L-900 210 Z" fill="url(#hs-hill)" opacity=".9" />

      {/* the city grows in, then its windows light up one by one */}
      <g>
        {BUILDINGS.map((b, bi) => {
          const top = GROUND - b.h;
          const cols = Math.floor((b.w - 10) / 12), rows = Math.floor((b.h - 14) / 16);
          return (
            <g key={bi} className="hs-building" style={{ animationDelay: `${0.15 * bi}s`, transformOrigin: `${b.x + b.w / 2}px ${GROUND}px` }}>
              <rect x={b.x} y={top} width={b.w} height={b.h} rx="4" fill={`var(--hs-b${b.tone})`} />
              <rect x={b.x} y={top} width={b.w} height="5" rx="2.5" fill="var(--hs-roof)" />
              {Array.from({ length: rows * cols }, (_, k) => {
                const r = Math.floor(k / cols), c = k % cols;
                // the light sweeps upward floor by floor, drifting left to right across the skyline
                return <rect key={k} className="hs-win" x={b.x + 7 + c * 12} y={top + 13 + r * 16} width="6" height="8" rx="1.5"
                  style={{ animationDelay: `${1.2 + (rows - r) * 0.22 + bi * 0.35 + c * 0.05}s` }} />;
              })}
            </g>
          );
        })}
      </g>

      {/* traffic: they cruise, stop at the lights and move on */}
      <g className="hs-car hs-car1"><Car /></g>
      <g className="hs-car hs-car2"><Car flip /></g>
      </g>

      <g className="hs-par hs-par-fore">
      {/* floating cloud badge with a rising chart: the "Tenemsa cloud" */}
      <g className="hs-float">
        <g transform="translate(168 96)">
          <path d="M20 52 a18 18 0 0 1 4-35 a26 26 0 0 1 50 6 a15 15 0 0 1 2 29 Z" fill="var(--hs-badge)" stroke="var(--hs-badge-line)" strokeWidth="1.5" />
          <g className="hs-bars" fill="var(--hs-bar)">
            <rect x="30" y="38" width="7" height="9" rx="2" /><rect x="41" y="30" width="7" height="17" rx="2" /><rect x="52" y="22" width="7" height="25" rx="2" />
          </g>
          <path className="hs-line" pathLength="1" d="M28 36 L40 28 L50 31 L66 14" fill="none" stroke="var(--hs-line)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
          <circle className="hs-dot" cx="66" cy="14" r="3.2" fill="var(--hs-line)" />
        </g>
      </g>

      {/* sparkles */}
      <g fill="var(--hs-spark)">
        <path className="hs-spark" d="M110 120 l3 8 8 3 -8 3 -3 8 -3 -8 -8 -3 8 -3 Z" />
        <path className="hs-spark hs-s2" d="M236 36 l2 5 5 2 -5 2 -2 5 -2 -5 -5 -2 5 -2 Z" />
      </g>
      </g>
      <rect x="-900" y={GROUND} width="1460" height="14" fill="var(--hs-ground)" />
    </svg>
  );
}

function Car({ flip }: { flip?: boolean }) {
  return (
    <g transform={flip ? "translate(0 200) scale(-1 1)" : "translate(0 200)"}>
      <rect x="-9" y="-6" width="18" height="5" rx="2" fill="var(--hs-car)" />
      <rect x="-5" y="-9" width="9" height="4" rx="1.5" fill="var(--hs-car)" />
      <circle cx="-5" cy="-0.8" r="1.7" fill="var(--hs-ground)" /><circle cx="5" cy="-0.8" r="1.7" fill="var(--hs-ground)" />
      <rect x="8" y="-5" width="3" height="1.8" rx=".9" fill="var(--hs-win)" className="hs-head" />
    </g>
  );
}

function Cloud({ x, y, s }: { x: number; y: number; s: number }) {
  return (
    <path transform={`translate(${x} ${y}) scale(${s})`} fill="var(--hs-cloud)"
      d="M20 34 a14 14 0 0 1 3-27 a20 20 0 0 1 38 5 a12 12 0 0 1 3 22 Z" />
  );
}

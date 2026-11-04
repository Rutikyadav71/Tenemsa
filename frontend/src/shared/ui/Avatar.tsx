const PALETTE = ["#6366f1", "#0d9488", "#16a34a", "#d97706", "#dc2626", "#7c3aed", "#db2777", "#0284c7"];
const colorFor = (s: string) => PALETTE[(s.charCodeAt(0) || 0) % PALETTE.length];
const initials = (name?: string | null) => (name ? name.trim().split(/\s+/).map(n => n[0]).join("").toUpperCase().slice(0, 2) : "?");

/** A round profile picture, or the person's initials on a soft colour when there is no picture. */
export function Avatar({ name, src, size = 34 }: { name?: string | null; src?: string | null; size?: number }) {
  const c = colorFor(name || "?");
  if (src) return <img className="tn-avatar" src={src} alt="" style={{ width: size, height: size }} loading="lazy" />;
  return (
    <span className="tn-avatar" aria-hidden="true" style={{ width: size, height: size, background: `${c}22`, color: `color-mix(in srgb, ${c} 55%, var(--text-1))`, fontSize: Math.round(size * 0.38) }}>
      {initials(name)}
    </span>
  );
}

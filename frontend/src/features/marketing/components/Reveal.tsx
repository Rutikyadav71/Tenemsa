import { useEffect, useRef, useState, type ElementType, type ReactNode } from "react";

/**
 * Fades its content in when it scrolls into view. People who ask their system for less motion, and browsers
 * without IntersectionObserver, simply see the content straight away.
 */
export default function Reveal({ children, delay = 0, as: Tag = "div", className = "" }:
  { children: ReactNode; delay?: number; as?: ElementType; className?: string }) {
  const ref = useRef<HTMLElement | null>(null);
  const [shown, setShown] = useState(() =>
    typeof IntersectionObserver === "undefined" || (typeof matchMedia !== "undefined" && matchMedia("(prefers-reduced-motion: reduce)").matches));

  useEffect(() => {
    if (shown || !ref.current) return;
    const io = new IntersectionObserver(entries => {
      if (entries.some(e => e.isIntersecting)) { setShown(true); io.disconnect(); }
    }, { threshold: 0.15 });
    io.observe(ref.current);
    return () => io.disconnect();
  }, [shown]);

  return <Tag ref={ref} className={`mk-reveal${shown ? " mk-in" : ""} ${className}`} style={{ transitionDelay: `${delay}ms` }}>{children}</Tag>;
}

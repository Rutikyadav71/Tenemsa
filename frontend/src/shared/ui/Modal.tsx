import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { LuX } from "react-icons/lu";
import { Button } from "./Button";

interface ModalProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children?: ReactNode;
  footer?: ReactNode;
  size?: "sm" | "md" | "lg";
  /** Slides in from the right instead of opening in the middle. */
  drawer?: boolean;
  /** Set false while a request is running so the dialog can't be dismissed half-way. */
  dismissible?: boolean;
}

const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

/** Accessible dialog: Escape closes, focus is kept inside and returned on close, page scroll is locked. */
export function Modal({ open, title, description, onClose, children, footer, size = "md", drawer = false, dismissible = true }: ModalProps) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const opener = useRef<Element | null>(null);

  useEffect(() => {
    if (!open) return;
    opener.current = document.activeElement;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const first = panel.current?.querySelector<HTMLElement>("[data-autofocus]") ?? panel.current?.querySelector<HTMLElement>(FOCUSABLE);
    (first ?? panel.current)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && dismissible) { e.stopPropagation(); onClose(); return; }
      if (e.key !== "Tab" || !panel.current) return;
      const items = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (!items.length) return;
      const a = items[0], z = items[items.length - 1];
      if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus(); }
      else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
      (opener.current as HTMLElement | null)?.focus?.();
    };
  }, [open, dismissible, onClose]);

  if (!open) return null;
  return createPortal(
    <div className={`tn-overlay ${drawer ? "tn-drawer-wrap" : ""}`} onMouseDown={e => { if (e.target === e.currentTarget && dismissible) onClose(); }}>
      <div ref={panel} className={`tn-modal ${drawer ? "tn-drawer" : size !== "md" ? `tn-modal-${size}` : ""}`} role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <div className="tn-modal-head">
          <div><h2 className="tn-modal-title" id={titleId}>{title}</h2>{description && <p className="tn-modal-desc">{description}</p>}</div>
          {dismissible && <Button variant="ghost" size="sm" aria-label="Close" onClick={onClose} icon={<LuX size={18} />} />}
        </div>
        <div className="tn-modal-body">{children}</div>
        {footer && <div className="tn-modal-foot">{footer}</div>}
      </div>
    </div>,
    document.body
  );
}

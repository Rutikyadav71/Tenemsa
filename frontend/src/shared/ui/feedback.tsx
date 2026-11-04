import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { LuCircleAlert, LuCircleCheck, LuInfo, LuX } from "react-icons/lu";
import { Button } from "./Button";
import { Modal } from "./Modal";
import { errorMessage } from "../lib/errors";

/* ── Toasts ───────────────────────────────────────────────────────────── */
type ToastKind = "success" | "error" | "info";
interface ToastItem { id: number; kind: ToastKind; text: string }
interface ToastApi {
  success: (text: string) => void;
  error: (text: string) => void;
  info: (text: string) => void;
  /** Shows a readable message for a failed request. */
  fail: (e: unknown, fallback?: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/* ── Confirmations ────────────────────────────────────────────────────── */
export interface ConfirmOptions { title: string; message?: string; confirmLabel?: string; cancelLabel?: string; tone?: "danger" | "primary" }
type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>;
const ConfirmContext = createContext<ConfirmFn | null>(null);

const ICONS = { success: LuCircleCheck, error: LuCircleAlert, info: LuInfo } as const;

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const [ask, setAsk] = useState<(ConfirmOptions & { resolve: (v: boolean) => void }) | null>(null);

  const dismiss = useCallback((id: number) => setToasts(t => t.filter(x => x.id !== id)), []);
  const push = useCallback((kind: ToastKind, text: string) => {
    const id = nextId.current++;
    setToasts(t => [...t.slice(-3), { id, kind, text }]);
    window.setTimeout(() => dismiss(id), kind === "error" ? 7000 : 4500);
  }, [dismiss]);

  const toast = useMemo<ToastApi>(() => ({
    success: t => push("success", t),
    error: t => push("error", t),
    info: t => push("info", t),
    fail: (e, fallback) => push("error", errorMessage(e, fallback)),
  }), [push]);

  const confirm = useCallback<ConfirmFn>(options => new Promise<boolean>(resolve => setAsk({ ...options, resolve })), []);
  const settle = (v: boolean) => { ask?.resolve(v); setAsk(null); };

  return (
    <ToastContext.Provider value={toast}>
      <ConfirmContext.Provider value={confirm}>
        {children}
        {createPortal(
          <div className="tn-toasts" aria-live="polite" aria-atomic="false">
            {toasts.map(t => { const Icon = ICONS[t.kind]; return (
              <div key={t.id} className={`tn-toast tn-toast-${t.kind}`} role={t.kind === "error" ? "alert" : "status"}>
                <Icon className="tn-toast-icon" aria-hidden="true" />
                <span>{t.text}</span>
                <button className="tn-toast-close" aria-label="Dismiss" onClick={() => dismiss(t.id)}><LuX size={16} /></button>
              </div>
            ); })}
          </div>, document.body)}
        <Modal open={!!ask} size="sm" title={ask?.title ?? ""} description={ask?.message} onClose={() => settle(false)}
          footer={<>
            <Button onClick={() => settle(false)}>{ask?.cancelLabel ?? "Cancel"}</Button>
            <Button variant={ask?.tone === "primary" ? "primary" : "danger"} data-autofocus onClick={() => settle(true)}>{ask?.confirmLabel ?? "Confirm"}</Button>
          </>} />
      </ConfirmContext.Provider>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used inside <FeedbackProvider>");
  return ctx;
}

/** `if (!(await confirm({ title: "Delete project?", message: "…" }))) return;` */
export function useConfirm(): ConfirmFn {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error("useConfirm must be used inside <FeedbackProvider>");
  return ctx;
}

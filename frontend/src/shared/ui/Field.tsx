import { cloneElement, forwardRef, isValidElement, useId, type InputHTMLAttributes, type ReactElement, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

interface FieldProps {
  label: string;
  required?: boolean;
  hint?: string;
  error?: string;
  children: ReactElement<{ id?: string; "aria-invalid"?: boolean; "aria-describedby"?: string }>;
}

/** Wires label, hint and error to the control for screen readers; `error` also turns the control red. */
export function Field({ label, required, hint, error, children }: FieldProps) {
  const id = useId();
  const msgId = `${id}-msg`;
  const control = isValidElement(children)
    ? cloneElement(children, { id, "aria-invalid": error ? true : undefined, "aria-describedby": error || hint ? msgId : undefined })
    : children;
  return (
    <div className="tn-field">
      <label className="tn-label" htmlFor={id}>{label}{required && <span className="tn-req" aria-hidden="true">*</span>}{required && <span className="sr-only"> (required)</span>}</label>
      {control as ReactNode}
      {error ? <div className="tn-error" id={msgId} role="alert">{error}</div> : hint ? <div className="tn-hint" id={msgId}>{hint}</div> : null}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input({ className = "", ...rest }, ref) {
  return <input ref={ref} className={`tn-input ${className}`} {...rest} />;
});
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select({ className = "", ...rest }, ref) {
  return <select ref={ref} className={`tn-input ${className}`} {...rest} />;
});
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea({ className = "", ...rest }, ref) {
  return <textarea ref={ref} className={`tn-input ${className}`} {...rest} />;
});

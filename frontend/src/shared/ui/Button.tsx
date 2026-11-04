import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
  block?: boolean;
  icon?: ReactNode;
}

/** While `loading`, the button is disabled and shows a spinner, so a double click can't submit twice. */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "secondary", size = "md", loading = false, block = false, icon, disabled, className = "", children, type = "button", ...rest }, ref) {
  const cls = ["tn-btn", `tn-btn-${variant}`, size !== "md" ? `tn-btn-${size}` : "", block ? "tn-btn-block" : "", !children ? "tn-btn-icon" : "", className].filter(Boolean).join(" ");
  return (
    <button ref={ref} type={type} className={cls} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading ? <span className="tn-spinner" aria-hidden="true" /> : icon}
      {children}
    </button>
  );
});

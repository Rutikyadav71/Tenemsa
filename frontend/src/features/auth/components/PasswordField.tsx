import { useState } from "react";
import { LuEye, LuEyeOff } from "react-icons/lu";
import { Field, Input } from "../../../shared/ui";

interface Props {
  label?: string;
  value: string;
  onChange: (v: string) => void;
  error?: string;
  hint?: string;
  autoComplete: "current-password" | "new-password";
  placeholder?: string;
  autoFocus?: boolean;
  /** Show the live policy checklist (8+ characters, upper, lower, digit). */
  showRules?: boolean;
}

const RULES: [string, (v: string) => boolean][] = [
  ["At least 8 characters", v => v.length >= 8],
  ["An uppercase and a lowercase letter", v => /[a-z]/.test(v) && /[A-Z]/.test(v)],
  ["A number", v => /\d/.test(v)],
];

export default function PasswordField({ label = "Password", value, onChange, error, hint, autoComplete, placeholder, autoFocus, showRules }: Props) {
  const [show, setShow] = useState(false);
  return (
    <div>
      <Field label={label} error={error} hint={hint} required>
        <div className="au-pw">
          <Input type={show ? "text" : "password"} value={value} onChange={e => onChange(e.target.value)}
            autoComplete={autoComplete} placeholder={placeholder} autoFocus={autoFocus} />
          <button type="button" onClick={() => setShow(s => !s)} aria-label={show ? "Hide password" : "Show password"} aria-pressed={show}>
            {show ? <LuEyeOff size={16} /> : <LuEye size={16} />}
          </button>
        </div>
      </Field>
      {showRules && (
        <ul className="au-rules" aria-label="Password requirements">
          {RULES.map(([t, ok]) => <li key={t} className={ok(value) ? "ok" : ""}>{ok(value) ? "✓" : "○"} {t}</li>)}
        </ul>
      )}
    </div>
  );
}

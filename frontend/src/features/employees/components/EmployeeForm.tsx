import { useState, type FormEvent } from "react";
import { LuEye, LuEyeOff } from "react-icons/lu";
import { Alert, Button, Field, Input, Select } from "../../../shared/ui";
import type { Role } from "../../../shared/types/models";
import * as v from "../../../shared/lib/validators";

export const DEPARTMENTS = ["Engineering", "Marketing", "HR", "Finance", "Operations", "Sales", "Design", "Product", "Legal", "Support"];

export interface EmployeeValues { empId: string; name: string; department: string; email: string; password: string; salary: string; role: string }
type Errors = Partial<Record<keyof EmployeeValues, string>>;

interface Props {
  mode: "add" | "edit";
  initial: EmployeeValues;
  roleOptions?: { value: Role; label: string }[];
  submitting: boolean;
  error: string;
  onSubmit: (values: EmployeeValues) => void;
  onCancel: () => void;
}

/** The one form behind Add and Edit employee. Edit keeps the ID fixed and treats the password as optional. */
export default function EmployeeForm({ mode, initial, roleOptions = [], submitting, error, onSubmit, onCancel }: Props) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<Errors>({});
  const [show, setShow] = useState(false);
  const edit = mode === "edit";
  const set = (k: keyof EmployeeValues) => (val: string) => setValues(s => ({ ...s, [k]: val }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const next: Errors = {
      empId: edit ? "" : !/^\d+$/.test(values.empId.trim()) ? "Use numbers only, for example 1001." : "",
      name: v.required(values.name, "Name"),
      department: values.department ? "" : "Choose a department.",
      email: v.email(values.email),
      salary: values.salary === "" || Number(values.salary) < 0 || Number.isNaN(Number(values.salary)) ? "Enter a salary of 0 or more." : "",
      password: edit && !values.password ? "" : v.minLength(values.password, 6, "Password"),
    };
    setErrors(next);
    if (!v.hasErrors(next as Record<string, string>)) onSubmit({ ...values, name: values.name.trim(), email: values.email.trim(), empId: values.empId.trim() });
  };

  return (
    <form onSubmit={submit} noValidate style={{ display: "grid", gap: 16 }}>
      {error && <Alert tone="error">{error}</Alert>}
      <div className="tn-form-grid">
        <Field label="Employee ID" required={!edit} error={errors.empId} hint={edit ? "The ID can't be changed." : "A unique number, for example 1001."}>
          <Input value={values.empId} onChange={e => set("empId")(e.target.value)} readOnly={edit} inputMode="numeric" autoFocus={!edit} />
        </Field>
        <Field label="Full name" required error={errors.name}>
          <Input value={values.name} onChange={e => set("name")(e.target.value)} autoComplete="off" autoFocus={edit} />
        </Field>
        <Field label="Department" required error={errors.department}>
          <Select value={values.department} onChange={e => set("department")(e.target.value)}>
            <option value="">Select a department</option>
            {DEPARTMENTS.map(d => <option key={d} value={d}>{d}</option>)}
            {values.department && !DEPARTMENTS.includes(values.department) && <option value={values.department}>{values.department}</option>}
          </Select>
        </Field>
        <Field label="Salary (₹)" required error={errors.salary}>
          <Input type="number" min={0} inputMode="decimal" value={values.salary} onChange={e => set("salary")(e.target.value)} />
        </Field>
      </div>
      <Field label="Email" required error={errors.email}>
        <Input type="email" value={values.email} onChange={e => set("email")(e.target.value)} autoComplete="off" />
      </Field>
      <Field label={edit ? "New password" : "Password"} required={!edit} error={errors.password}
        hint={edit ? "Leave blank to keep the current password." : "At least 6 characters. The person gets it by email and can change it after signing in."}>
        <div className="au-pw">
          <Input type={show ? "text" : "password"} value={values.password} onChange={e => set("password")(e.target.value)} autoComplete="new-password" style={{ paddingRight: 44 }} />
          <button type="button" onClick={() => setShow(s => !s)} aria-label={show ? "Hide password" : "Show password"} aria-pressed={show}>{show ? <LuEyeOff size={16} /> : <LuEye size={16} />}</button>
        </div>
      </Field>
      {!edit && roleOptions.length > 0 && (
        <Field label="Role" hint="Admin and HR accounts are created by the Owner from Roles & admins.">
          <Select value={values.role} onChange={e => set("role")(e.target.value)}>{roleOptions.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</Select>
        </Field>
      )}
      <div className="tn-form-actions">
        <Button onClick={onCancel}>Cancel</Button>
        <Button type="submit" variant="primary" loading={submitting}>{edit ? "Save changes" : "Add employee"}</Button>
      </div>
    </form>
  );
}

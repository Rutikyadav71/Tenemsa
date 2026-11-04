import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { addEmployee } from "../services/EmployeeService";
import type { EmployeeFormInput, Role } from "../../../shared/types/models";
import { Card, PageHeader, useToast } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";
import EmployeeForm, { type EmployeeValues } from "../components/EmployeeForm";
import "../../auth/components/auth.css";

// Mirrors the backend's RoleAssignmentRegistry (who may create which role). It only keeps the list tidy;
// the backend is the real check.
const ASSIGNABLE_ROLES: Record<string, { value: Role; label: string }[]> = {
  OWNER: [{ value: "ADMIN", label: "Admin" }, { value: "HR", label: "HR" }, { value: "MANAGER", label: "Manager" }, { value: "EMPLOYEE", label: "Employee" }, { value: "INTERN", label: "Intern" }],
  ADMIN: [{ value: "MANAGER", label: "Manager" }],
  HR: [{ value: "EMPLOYEE", label: "Employee" }, { value: "INTERN", label: "Intern" }],
};

export default function AddEmployee() {
  const navigate = useNavigate();
  const toast = useToast();
  const base = `/${useLocation().pathname.split("/")[1]}`;
  const roleOptions = ASSIGNABLE_ROLES[localStorage.getItem("userRole") || ""] || [];
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const save = async (v: EmployeeValues) => {
    setSaving(true); setError("");
    try {
      await addEmployee({ ...v, role: (v.role || roleOptions[0]?.value || "EMPLOYEE") as Role } as EmployeeFormInput);
      toast.success(`${v.name} was added.`);
      navigate(`${base}/employees`);
    } catch (e) { setError(errorMessage(e, "We couldn't add this person. Please try again.")); }
    finally { setSaving(false); }
  };

  return (
    <div style={{ maxWidth: 680 }}>
      <PageHeader title="Add employee" subtitle="Create an account for a new team member." />
      <Card>
        <EmployeeForm mode="add" roleOptions={roleOptions} submitting={saving} error={error} onSubmit={save} onCancel={() => navigate(`${base}/employees`)}
          initial={{ empId: "", name: "", department: "", email: "", password: "", salary: "", role: roleOptions[0]?.value || "EMPLOYEE" }} />
      </Card>
    </div>
  );
}

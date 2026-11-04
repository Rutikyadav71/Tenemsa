import { useEffect, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { getEmployees, updateEmployee } from "../services/EmployeeService";
import { Card, ErrorState, PageHeader, SkeletonList, useToast } from "../../../shared/ui";
import { errorMessage } from "../../../shared/lib/errors";
import EmployeeForm, { type EmployeeValues } from "../components/EmployeeForm";
import "../../auth/components/auth.css";

export default function UpdateEmployee() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const base = `/${useLocation().pathname.split("/")[1]}`;
  const [initial, setInitial] = useState<EmployeeValues | null>(null);
  const [failed, setFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let live = true;
    getEmployees().then(res => {
      if (!live) return;
      const e = res.data.find(x => x.empId === parseInt(id ?? "", 10));
      if (!e) { setFailed(true); return; }
      setInitial({ empId: String(e.empId ?? ""), name: e.name, department: e.department ?? "", salary: e.salary != null ? String(e.salary) : "", email: e.email, password: "", role: e.role });
    }).catch(() => { if (live) setFailed(true); });
    return () => { live = false; };
  }, [id]);

  const save = async (v: EmployeeValues) => {
    setSaving(true); setError("");
    try {
      // The role isn't sent: the backend never applies it on update, and it isn't editable here.
      const { role: _role, ...payload } = v;
      void _role;
      await updateEmployee(v.empId, payload);
      toast.success("Changes saved.");
      navigate(`${base}/employees`);
    } catch (e) { setError(errorMessage(e, "We couldn't save the changes. Please try again.")); }
    finally { setSaving(false); }
  };

  return (
    <div style={{ maxWidth: 680 }}>
      <PageHeader title="Edit employee" subtitle={initial ? `Update ${initial.name}'s details.` : undefined} />
      <Card>
        {failed ? <ErrorState title="We couldn't find this person" message="They may have been removed, or the list couldn't be loaded." onRetry={() => navigate(`${base}/employees`)} />
          : !initial ? <SkeletonList rows={4} />
          : <EmployeeForm mode="edit" initial={initial} submitting={saving} error={error} onSubmit={save} onCancel={() => navigate(`${base}/employees`)} />}
      </Card>
    </div>
  );
}

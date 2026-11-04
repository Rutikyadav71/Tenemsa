import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import axiosInstance from "../../../shared/api/axiosInstance";
import ProfileImageUpload from "../../../shared/components/ProfileImageUpload";
import type { Workspace } from "../../../shared/lib/roles";
import type { User } from "../../../shared/types/models";
import { Badge, Button, Card, PageHeader, Skeleton } from "../../../shared/ui";
import "./profile.css";

const ROLE_LABEL: Record<string, string> = {
  OWNER: "Owner", ADMIN: "Admin", HR: "HR", MANAGER: "Manager", EMPLOYEE: "Employee", INTERN: "Intern", SUPER_ADMIN: "Super admin",
};
const inr = (n: number) => `₹${Number(n).toLocaleString("en-IN")}`;

/** One profile for every role. Admin-tier details come from the sign-in; others are loaded from the employee record. */
export default function ProfilePage({ workspace }: { workspace: Workspace }) {
  const isAdminTier = workspace === "admin";
  const role = localStorage.getItem("userRole") || "";
  const userId = Number(localStorage.getItem("id") || 0);
  const empId = Number(localStorage.getItem("empId") || 0);
  const [record, setRecord] = useState<User | null>(null);
  const [loading, setLoading] = useState(!isAdminTier);
  const [photo, setPhoto] = useState<string | null>(
    localStorage.getItem(isAdminTier ? "adminProfileImageUrl" : "profileImageUrl") || localStorage.getItem("profileImageUrl") || null);

  useEffect(() => {
    if (isAdminTier || !empId) { setLoading(false); return; }
    axiosInstance.get<User>(`/api/employees/${empId}`)
      .then(r => { setRecord(r.data); if (r.data.profileImageUrl) setPhoto(r.data.profileImageUrl); })
      .catch(() => { /* the sign-in details below are still shown */ })
      .finally(() => setLoading(false));
  }, [isAdminTier, empId]);

  const name = record?.name || localStorage.getItem("name") || localStorage.getItem("adminName") || "Member";
  const email = record?.email || localStorage.getItem("email") || localStorage.getItem("adminEmail") || "—";
  const dept = record?.department || localStorage.getItem("department") || "";
  const company = localStorage.getItem("companyName") || "—";
  const verified = localStorage.getItem("emailVerified") === "true";
  const isMain = localStorage.getItem("primaryOwner") === "true";

  const uploadAdmin = async (file: File): Promise<string> => {
    const fd = new FormData();
    fd.append("file", file, "profile.jpg");
    return (await axiosInstance.post(`/api/upload/admin-profile/${userId}`, fd)).data.url as string;
  };
  const onUpload = (url: string) => {
    setPhoto(url);
    localStorage.setItem(isAdminTier ? "adminProfileImageUrl" : "profileImageUrl", url);
    localStorage.setItem("profileImageUrl", url);
  };

  const rows: { label: string; value: ReactNode }[] = [
    { label: "Full name", value: name },
    { label: "Email", value: email },
    ...(empId ? [{ label: "Employee ID", value: `#${empId}` }] : [{ label: "User ID", value: `#${userId}` }]),
    { label: "Role", value: <Badge tone="info">{ROLE_LABEL[role] ?? role}</Badge> },
    ...(dept ? [{ label: "Department", value: dept }] : []),
    { label: "Company", value: company },
    ...(isAdminTier ? [
      { label: "Email status", value: verified ? <Badge tone="success">Verified</Badge> : <Badge tone="warning">Not verified</Badge> },
      ...(role === "OWNER" ? [{ label: "Access", value: <Badge tone="accent">{isMain ? "Main owner" : "Owner"}</Badge> }] : []),
    ] : []),
    ...(record?.salary != null ? [{ label: "Salary", value: inr(record.salary) }] : []),
  ];

  return (
    <div>
      <PageHeader title="My profile" subtitle="How you appear to colleagues."
        actions={<Link to={`/${workspace}/settings`}><Button variant="secondary">Settings</Button></Link>} />
      <Card className="pf-card" pad={false}>
        <div className="pf-banner" aria-hidden="true" />
        <div className="pf-head">
          <ProfileImageUpload empId={isAdminTier ? userId : empId} currentUrl={photo} name={name} onUpload={onUpload} size={84} editable
            customUploadFn={isAdminTier ? uploadAdmin : undefined} />
          <div className="pf-name">
            <h2>{name}</h2>
            <span>{[ROLE_LABEL[role] ?? role, dept].filter(Boolean).join(" · ")}</span>
          </div>
        </div>
        <dl className="pf-grid">
          {loading
            ? Array.from({ length: 4 }, (_, i) => <div key={i}><Skeleton height={11} width="40%" /><div style={{ height: 6 }} /><Skeleton height={15} width="70%" /></div>)
            : rows.map((r, i) => <div key={r.label + i}><dt>{r.label}</dt><dd>{r.value}</dd></div>)}
        </dl>
      </Card>
    </div>
  );
}

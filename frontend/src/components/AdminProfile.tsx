import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axiosInstance from "../services/axiosInstance";
import { performLogout, performLogoutAllDevices } from "../services/logout";
import ProfileImageUpload from "./ProfileImageUpload";

const LogoutIcon = () => <svg width="15" height="15" fill="none" viewBox="0 0 24 24"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>;

export default function AdminProfile() {
  const navigate = useNavigate();
  const name = localStorage.getItem("adminName") || "Admin";
  const email = localStorage.getItem("adminEmail") || "—";
  const companyName = localStorage.getItem("companyName") || "";
  const emailVerified = localStorage.getItem("emailVerified") === "true";
  const isMain = localStorage.getItem("primaryOwner") === "true";
  const adminId = Number(localStorage.getItem("id") || 0);
  const initials = name.split(" ").map(n => n[0]).join("").toUpperCase().slice(0, 2);
  const [profileImg, setProfileImg] = useState<string | null>(localStorage.getItem("adminProfileImageUrl") || null);
  const [showSignout, setShowSignout] = useState(false);
  const [signoutAllDevices, setSignoutAllDevices] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const handleConfirmSignOut = async () => {
    setLoggingOut(true);
    try {
      if (signoutAllDevices) await performLogoutAllDevices();
      else await performLogout();
    } finally {
      navigate("/");
    }
  };

  // Admin profile upload uses a dedicated backend endpoint
  const handleAdminImageUpload = async (croppedFile: File): Promise<string> => {
    const fd = new FormData();
    fd.append("file", croppedFile, "profile.jpg");
    const res = await axiosInstance.post(`/api/upload/admin-profile/${adminId}`, fd);
    const url = res.data.url;
    setProfileImg(url);
    localStorage.setItem("adminProfileImageUrl", url);
    return url;
  };

  return (
    <div className="fade-in">
      <div className="page-header"><div className="page-title">Profile</div></div>

      <div className="profile-card" style={{ maxWidth: 640 }}>
        {/* Banner */}
        <div className="profile-banner" style={{ background: "linear-gradient(120deg,rgba(99,102,241,0.2) 0%,#111827 60%)" }} />

        {/* Avatar row */}
        <div style={{ padding: "0 24px 20px", marginTop: -44, display: "flex", alignItems: "flex-end", justifyContent: "space-between" }}>
          {/* Reuse ProfileImageUpload with admin-specific upload function */}
          <ProfileImageUpload
            empId={adminId}
            currentUrl={profileImg}
            name={name}
            onUpload={url => { setProfileImg(url); localStorage.setItem("adminProfileImageUrl", url); }}
            size={84}
            editable={true}
            customUploadFn={handleAdminImageUpload}
          />
          <button className="btn-ems btn-danger-ems" onClick={() => setShowSignout(true)}>
            <LogoutIcon /> Sign Out
          </button>
        </div>

        {/* Name + role */}
        <div style={{ padding: "0 24px 16px" }}>
          <div style={{ fontSize: 20, fontWeight: 700, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)" }}>{name}</div>
          <div style={{ fontSize: 12, color: isMain ? "var(--accent-light)" : "var(--text-3)", marginTop: 3 }}>
            {isMain ? "Main Administrator" : "Administrator"}
          </div>
        </div>

        <div className="divider" style={{ margin: "0 24px" }} />

        <div className="profile-info-grid">
          {[
            { label: "Full Name", value: name },
            { label: "Email", value: email },
            { label: "Admin ID", value: `#${adminId}` },
            { label: "Role", value: <span className="badge badge-blue">ADMIN</span> },
            { label: "Access Level", value: <span className={`badge ${isMain ? "badge-teal" : "badge-blue"}`}>{isMain ? "Main Admin" : "Sub-Admin"}</span> },
            { label: "Company", value: companyName || "—" },
            { label: "Email Verified", value: emailVerified
              ? <span className="badge badge-green">Verified</span>
              : <span className="badge badge-amber">Unverified</span> },
            { label: "Status", value: <span className="badge badge-green">Active</span> },
          ].map(({ label, value }) => (
            <div className="profile-field" key={label}>
              <div className="profile-field-label">{label}</div>
              <div className="profile-field-value">{value}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Sign out confirm */}
      {showSignout && (
        <div className="ems-modal-backdrop" onClick={() => setShowSignout(false)}>
          <div className="ems-modal" style={{ width: 340 }} onClick={e => e.stopPropagation()}>
            <div style={{ textAlign: "center", padding: "8px 0 16px" }}>
              <div style={{
                width: 48, height: 48, borderRadius: "50%", background: "var(--red-glow)",
                display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 14px"
              }}>
                <LogoutIcon />
              </div>
              <div style={{ fontSize: 17, fontWeight: 700, color: "var(--text-1)", marginBottom: 6 }}>Sign out?</div>
              <div style={{ fontSize: 13, color: "var(--text-3)", lineHeight: 1.6 }}>You will be returned to the login page.</div>
            </div>
            <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--text-3)", marginBottom: 16, cursor: "pointer" }}>
              <input type="checkbox" checked={signoutAllDevices} onChange={e => setSignoutAllDevices(e.target.checked)} />
              Sign out of all devices (revokes every active session)
            </label>
            <div style={{ display: "flex", gap: 10 }}>
              <button className="btn-ems btn-secondary-ems" style={{ flex: 1, justifyContent: "center" }} onClick={() => setShowSignout(false)}>Cancel</button>
              <button className="btn-ems btn-danger-ems" style={{ flex: 1, justifyContent: "center" }} disabled={loggingOut} onClick={handleConfirmSignOut}>
                <LogoutIcon /> {loggingOut ? "Signing out..." : "Sign Out"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

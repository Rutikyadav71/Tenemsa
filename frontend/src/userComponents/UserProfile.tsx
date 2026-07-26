import { useEffect, useState } from "react";
import axiosInstance from "../services/axiosInstance";
import ProfileImageUpload from "../components/ProfileImageUpload";
import type { User } from "../types/models";

export default function UserProfile() {
  const empId     = Number(localStorage.getItem("empId"));
  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    axiosInstance.get<User>(`/api/employees/${empId}`)
      .then(r => setProfile(r.data))
      .catch(e => console.error(e))
      .finally(() => setLoading(false));
  }, [empId]);

  const handleImageUpload = (url: string) => {
    setProfile(p => p ? ({ ...p, profileImageUrl: url }) : p);
    localStorage.setItem("profileImageUrl", url);
  };

  const name   = profile?.name     || localStorage.getItem("name")       || "Employee";
  const dept   = profile?.department|| localStorage.getItem("department") || "—";
  const email  = profile?.email    || localStorage.getItem("email")      || "—";
  const salary = profile?.salary;
  const imgUrl = profile?.profileImageUrl || localStorage.getItem("profileImageUrl") || null;

  if (loading) return (
    <div className="fade-in">
      <div className="page-header"><div className="page-title">My Profile</div></div>
      <div className="profile-card">
        <div className="profile-banner"/>
        <div style={{ display:"flex",justifyContent:"center",padding:"24px" }}>
          <div className="skeleton" style={{ width:80,height:80,borderRadius:"50%" }}/>
        </div>
        {Array(4).fill(0).map((_,i)=>(
          <div key={i} className="profile-field">
            <div className="skeleton" style={{ width:"40%",height:11,borderRadius:4,marginBottom:6 }}/>
            <div className="skeleton" style={{ width:"60%",height:14,borderRadius:4 }}/>
          </div>
        ))}
      </div>
    </div>
  );

  return (
    <div className="fade-in">
      <div className="page-header"><div className="page-title">My Profile</div></div>

      <div className="profile-card" style={{ maxWidth:640 }}>
        <div className="profile-banner"/>

        <div style={{ padding:"0 24px 20px", marginTop:-44, display:"flex",
          alignItems:"flex-end", justifyContent:"space-between" }}>
          <ProfileImageUpload
            empId={empId}
            currentUrl={imgUrl}
            name={name}
            onUpload={handleImageUpload}
            size={84}
            editable={true}
          />
        </div>

        <div style={{ padding:"0 24px 16px" }}>
          <div style={{ fontSize:20,fontWeight:700,fontFamily:"'Outfit',sans-serif",color:"var(--text-1)" }}>{name}</div>
          <div style={{ fontSize:12,color:"var(--text-3)",marginTop:3 }}>{dept} · Employee</div>
        </div>

        <div className="divider" style={{ margin:"0 24px" }}/>

        <div className="profile-info-grid">
          {[
            { label:"Employee ID", value:`#${empId}` },
            { label:"Full Name",   value:name },
            { label:"Email",       value:email },
            { label:"Department",  value:dept },
            { label:"Salary",      value:salary!=null?`₹${Number(salary).toLocaleString("en-IN")}`:"—" },
            { label:"Role",        value:<span className="badge badge-blue">EMPLOYEE</span> },
          ].map(({label,value})=>(
            <div className="profile-field" key={label}>
              <div className="profile-field-label">{label}</div>
              <div className="profile-field-value">{value}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

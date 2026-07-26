import { Link } from "react-router-dom";

const LogoIcon = () => (
  <svg width="32" height="32" fill="none" viewBox="0 0 24 24">
    <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const features = [
  { icon: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><circle cx="9" cy="7" r="4" stroke="currentColor" strokeWidth="1.8" /><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>, label: "Employee Management", sub: "Add, update & manage team members" },
  { icon: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2" stroke="currentColor" strokeWidth="1.8" /><path d="M16 2v4M8 2v4M3 10h18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>, label: "Leave Management", sub: "Apply, approve & track leave requests" },
  { icon: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>, label: "Team Chat", sub: "Real-time messaging with your team" },
  { icon: <svg width="20" height="20" fill="none" viewBox="0 0 24 24"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>, label: "Analytics", sub: "Visual dashboards and reports" },
];

export default function HomePage() {
  return (
    <div className="home-page">
      {/* Noise texture overlay */}
      <div style={{
        position: "fixed", inset: 0, pointerEvents: "none", zIndex: 0,
        backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E")`,
        backgroundRepeat: "repeat", opacity: 0.4,
      }} />

      <div style={{ position: "relative", zIndex: 1, width: "100%", maxWidth: 900, margin: "0 auto" }}>
        {/* Logo */}
        <div style={{ textAlign: "center", marginBottom: 48 }}>
          <div style={{ width: 64, height: 64, background: "var(--accent)", borderRadius: 16, display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 20px", boxShadow: "0 0 40px var(--accent-glow)" }}>
            <LogoIcon />
          </div>
          <div style={{ fontFamily: "'Outfit',sans-serif", fontSize: 44, fontWeight: 800, color: "var(--text-1)", letterSpacing: "-1px", lineHeight: 1.1, marginBottom: 14 }}>
            Employee Management<br />
            <span style={{ background: "linear-gradient(90deg, var(--accent-light), var(--teal))", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
              System
            </span>
          </div>
          <p style={{ fontSize: 16, color: "var(--text-3)", maxWidth: 480, margin: "0 auto", lineHeight: 1.7 }}>
            A modern platform to manage employees, track leaves, and keep your team connected — all in one place.
          </p>
        </div>

        {/* Portal cards */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginBottom: 40, maxWidth: 640, margin: "0 auto 40px" }}>
          {/* Employee */}
          <div style={{
            background: "var(--bg-surface)", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)",
            padding: 28, textAlign: "center", transition: "all 0.2s", cursor: "pointer",
          }}
            onMouseEnter={e => { e.currentTarget.style.borderColor = "var(--border-bright)"; e.currentTarget.style.transform = "translateY(-2px)"; e.currentTarget.style.boxShadow = "var(--shadow)"; }}
            onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.transform = "translateY(0)"; e.currentTarget.style.boxShadow = "none"; }}
          >
            <div style={{ width: 52, height: 52, borderRadius: 14, background: "var(--teal-glow)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 14px", color: "var(--teal)" }}>
              <svg width="24" height="24" fill="none" viewBox="0 0 24 24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /><circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="1.8" /></svg>
            </div>
            <div style={{ fontSize: 16, fontWeight: 700, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 6 }}>Employee Portal</div>
            <div style={{ fontSize: 12.5, color: "var(--text-3)", marginBottom: 20, lineHeight: 1.6 }}>View profile, apply leave, and track your requests</div>
            <Link to="/auth/employee">
              <button className="btn-ems" style={{ width: "100%", justifyContent: "center", background: "var(--teal-glow)", color: "var(--teal)", border: "1px solid rgba(45,212,191,0.3)" }}>
                Login as Employee
              </button>
            </Link>
          </div>

          {/* Admin */}
          <div style={{
            background: "var(--bg-surface)", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)",
            padding: 28, textAlign: "center", transition: "all 0.2s", cursor: "pointer",
          }}
            onMouseEnter={e => { e.currentTarget.style.borderColor = "rgba(99,102,241,0.4)"; e.currentTarget.style.transform = "translateY(-2px)"; e.currentTarget.style.boxShadow = "0 0 20px var(--accent-glow)"; }}
            onMouseLeave={e => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.transform = "translateY(0)"; e.currentTarget.style.boxShadow = "none"; }}
          >
            <div style={{ width: 52, height: 52, borderRadius: 14, background: "var(--accent-glow)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 14px", color: "var(--accent-light)" }}>
              <svg width="24" height="24" fill="none" viewBox="0 0 24 24"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </div>
            <div style={{ fontSize: 16, fontWeight: 700, fontFamily: "'Outfit',sans-serif", color: "var(--text-1)", marginBottom: 6 }}>Admin Panel</div>
            <div style={{ fontSize: 12.5, color: "var(--text-3)", marginBottom: 20, lineHeight: 1.6 }}>Manage employees, leaves, and organization data</div>
            <Link to="/auth/admin">
              <button className="btn-ems btn-primary-ems" style={{ width: "100%", justifyContent: "center" }}>
                Login as Admin
              </button>
            </Link>
          </div>
        </div>

        {/* Feature strip */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12, maxWidth: 760, margin: "0 auto 40px" }}>
          {features.map((f, i) => (
            <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 12, padding: "14px 16px", background: "var(--bg-surface)", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)" }}>
              <div style={{ color: "var(--accent-light)", flexShrink: 0, marginTop: 1 }}>{f.icon}</div>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-1)" }}>{f.label}</div>
                <div style={{ fontSize: 11.5, color: "var(--text-3)", marginTop: 2 }}>{f.sub}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Demo creds */}
        <div style={{ maxWidth: 560, margin: "0 auto", background: "var(--bg-surface)", border: "1px solid var(--border)", borderRadius: "var(--radius)", padding: "16px 20px" }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: "var(--text-3)", textTransform: "uppercase", letterSpacing: "0.5px", marginBottom: 10 }}>Demo Credentials</div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div style={{ padding: "10px 14px", background: "var(--bg-elevated)", borderRadius: 8, border: "1px solid var(--border)" }}>
              <div style={{ fontSize: 11, color: "var(--accent-light)", fontWeight: 600, marginBottom: 5 }}>ADMIN</div>
              <div style={{ fontSize: 11.5, color: "var(--text-3)" }}>admin1@gmail.com</div>
              <div style={{ fontSize: 11.5, color: "var(--text-3)" }}>Admin@1</div>
            </div>
            <div style={{ padding: "10px 14px", background: "var(--bg-elevated)", borderRadius: 8, border: "1px solid var(--border)" }}>
              <div style={{ fontSize: 11, color: "var(--teal)", fontWeight: 600, marginBottom: 5 }}>EMPLOYEE</div>
              <div style={{ fontSize: 11.5, color: "var(--text-3)" }}>Employee1@gmail.com</div>
              <div style={{ fontSize: 11.5, color: "var(--text-3)" }}>Employee@1</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

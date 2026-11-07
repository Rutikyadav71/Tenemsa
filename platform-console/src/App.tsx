import { useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { api, getToken, setToken, type Admin } from "./api/client";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import Companies from "./pages/Companies";
import CompanyDetail from "./pages/CompanyDetail";
import Announcements from "./pages/Announcements";
import Audit from "./pages/Audit";
import Admins from "./pages/Admins";
import System from "./pages/System";
import { Skeleton } from "./components/ui";

function Guard() {
  const [admin, setAdmin] = useState<Admin | null>(null);
  const [state, setState] = useState<"loading" | "ok" | "out">(getToken() ? "loading" : "out");
  const loc = useLocation();
  useEffect(() => {
    if (!getToken()) { setState("out"); return; }
    api<Admin>("/api/platform/me").then(a => { setAdmin(a); setState("ok"); }).catch(() => { setToken(null); setState("out"); });
  }, []);
  useEffect(() => {
    const h = () => setState("out");
    window.addEventListener("pc-signed-out", h);
    return () => window.removeEventListener("pc-signed-out", h);
  }, []);
  if (state === "loading") return <div style={{ padding: 40 }}><Skeleton h={24} w={240} /></div>;
  if (state === "out" || !admin) return <Navigate to="/login" replace state={{ from: loc.pathname, expired: true }} />;
  return <Layout admin={admin} />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<Guard />}>
        <Route index element={<Dashboard />} />
        <Route path="companies" element={<Companies />} />
        <Route path="companies/:id" element={<CompanyDetail />} />
        <Route path="announcements" element={<Announcements />} />
        <Route path="audit" element={<Audit />} />
        <Route path="admins" element={<Admins />} />
        <Route path="system" element={<System />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

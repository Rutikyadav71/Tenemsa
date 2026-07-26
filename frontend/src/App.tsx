import { useState } from "react";
import "./App.css";
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet } from "react-router-dom";

import HomePage from "./pages/HomePage";
import EmployeeLogin from "./pages/AuthPage";
import AdminAuthPage from "./pages/AdminAuthPage";
import ChatPage from "./pages/ChatPage";
import AttendancePage from "./pages/Attendance";
import TeamManagement from "./pages/TeamManagement";
import AdminManagement from "./pages/AdminManagement";
import AdminNotifications from "./pages/AdminNotifications";
import UserNotifications from "./pages/UserNotifications";
import UserLeaveDetails from "./pages/UserLeaveDetails";

import AdminDashboard from "./components/AdminDashboard";
import AddEmployee from "./components/AddEmployee";
import EmployeeList from "./components/EmployeeList";
import UpdateEmployee from "./components/UpdateEmployee";
import AdminProfile from "./components/AdminProfile";
import AdminNavbar from "./components/AdminNavbar";
import AdminSidebar from "./components/AdminSidebar";
import LeaveManagement from "./components/LeaveManagement";
import LeaveRequestForm from "./components/LeaveRequestForm";

import UserDashboard from "./userComponents/UserDashboard";
import UserProfile from "./userComponents/UserProfile";
import UserLeaveRequestForm from "./userComponents/LeaveRequestForm";
import UserNavbar from "./userComponents/UserNavbar";
import UserSidebar from "./userComponents/UserSidebar";

import type { ChatRoleTag } from "./types/models";

interface ProtectedRouteProps {
  allowedRole: ChatRoleTag;
}

const ProtectedRoute = ({ allowedRole }: ProtectedRouteProps) => {
  const role = localStorage.getItem("role");
  return role === allowedRole ? <Outlet /> : <Navigate to="/" />;
};

/* ── Admin layout ──────────────────────────────────────────────────────── */
const AdminLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);  // mobile overlay
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);  // desktop collapse

  const toggleSidebar = () => setSidebarOpen(v => !v);
  const toggleCollapsed = () => setSidebarCollapsed(v => !v);

  return (
    <div className="app-container">
      {/* Sidebar */}
      <div className={`layout-sidebar${sidebarOpen ? " open" : ""}${sidebarCollapsed ? " collapsed" : ""}`}>
        <AdminSidebar
          isOpen={sidebarOpen}
          collapsed={sidebarCollapsed}
          toggleSidebar={toggleSidebar} />
      </div>
      {/* Mobile overlay backdrop */}
      {sidebarOpen && (
        <div className="sidebar-overlay" onClick={toggleSidebar} style={{ zIndex: 998 }} />
      )}
      <div className={`layout-body${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
        <div className="layout-navbar">
          <AdminNavbar
            toggleSidebar={toggleSidebar}
            toggleCollapsed={toggleCollapsed}
            sidebarCollapsed={sidebarCollapsed} />
        </div>
        <main className="layout-main">
          <Routes>
            <Route path="dashboard" element={<AdminDashboard />} />
            <Route path="employees" element={<EmployeeList />} />
            <Route path="add" element={<AddEmployee />} />
            <Route path="update/:id" element={<UpdateEmployee />} />
            <Route path="profile" element={<AdminProfile />} />
            <Route path="apply-leave" element={<LeaveRequestForm />} />
            <Route path="leaves" element={<LeaveManagement />} />
            <Route path="attendance" element={<AttendancePage isAdmin={true} />} />
            <Route path="teams" element={<TeamManagement isAdmin={true} />} />
            <Route path="notifications" element={<AdminNotifications />} />
            <Route path="chat" element={<ChatPage />} />
            <Route path="manage-admins" element={<AdminManagement />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

/* ── User layout ───────────────────────────────────────────────────────── */
const UserLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const empId = Number(localStorage.getItem("empId"));

  const toggleSidebar = () => setSidebarOpen(v => !v);
  const toggleCollapsed = () => setSidebarCollapsed(v => !v);

  return (
    <div className="app-container">
      <div className={`layout-sidebar${sidebarOpen ? " open" : ""}${sidebarCollapsed ? " collapsed" : ""}`}>
        <UserSidebar
          isOpen={sidebarOpen}
          collapsed={sidebarCollapsed}
          toggleSidebar={toggleSidebar} />
      </div>
      {sidebarOpen && (
        <div className="sidebar-overlay" onClick={toggleSidebar} style={{ zIndex: 998 }} />
      )}
      <div className={`layout-body${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
        <div className="layout-navbar">
          <UserNavbar
            toggleSidebar={toggleSidebar}
            toggleCollapsed={toggleCollapsed}
            sidebarCollapsed={sidebarCollapsed} />
        </div>
        <main className="layout-main">
          <Routes>
            <Route path="dashboard" element={<UserDashboard />} />
            <Route path="profile" element={<UserProfile />} />
            <Route path="apply-leave" element={<UserLeaveRequestForm />} />
            <Route path="leaves/employee/:empId" element={<UserLeaveDetails />} />
            <Route path="attendance" element={<AttendancePage isAdmin={false} />} />
            <Route path="teams" element={<TeamManagement isAdmin={false} empId={empId} />} />
            <Route path="notifications" element={<UserNotifications />} />
            <Route path="chat" element={<ChatPage />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/auth/employee" element={<EmployeeLogin />} />
        <Route path="/auth/admin" element={<AdminAuthPage />} />
        <Route element={<ProtectedRoute allowedRole="ADMIN" />}>
          <Route path="/admin/*" element={<AdminLayout />} />
        </Route>
        <Route element={<ProtectedRoute allowedRole="USER" />}>
          <Route path="/user/*" element={<UserLayout />} />
        </Route>
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  );
}

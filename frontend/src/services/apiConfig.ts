// Single source of truth for the backend base URL.
//
// Previously this exact production URL was hardcoded, independently, in
// SEVEN different places across the frontend (axiosInstance.js,
// chatService.js, AuthPage.jsx, AdminAuthPage.jsx, AdminProfile.jsx,
// ProfileImageUpload.jsx, ChatRoom.jsx) — meaning local development against
// a local backend was never actually possible without editing every one of
// those files by hand, and switching environments meant a 7-file find/replace.
//
// Set VITE_API_BASE_URL in a local .env file (see .env.example) to point
// this at a local backend. Falls back to the deployed backend so an existing
// production build with no .env configured keeps working unchanged.
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || "https://employee-management-system-backend-wc0p.onrender.com";

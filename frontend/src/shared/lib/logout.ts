import axiosInstance from "../api/axiosInstance";
import { clearAuthStorage, getRefreshToken } from "./authStorage";

/**
 * Every "Sign Out" button previously just did localStorage.clear() and
 * navigated away — the refresh token stayed valid server-side indefinitely
 * (see TokenService), meaning a signed-out browser's old token could still
 * be used to silently mint new access tokens if it leaked. This actually
 * revokes it first.
 */
export async function performLogout(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (refreshToken) {
    try {
      await axiosInstance.post("/api/auth/logout", { refreshToken });
    } catch {
      // Revocation failing shouldn't block the user from leaving — the
      // token still expires on its own eventually either way.
    }
  }
  clearAuthStorage();
}

export async function performLogoutAllDevices(): Promise<void> {
  await axiosInstance.post("/api/auth/logout-all");
  clearAuthStorage();
}

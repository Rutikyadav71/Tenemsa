import axios, { type InternalAxiosRequestConfig, type AxiosError, type AxiosRequestConfig } from 'axios';
import { API_BASE_URL } from './apiConfig';
import { clearAuthStorage, getRefreshToken, updateTokens } from './authStorage';
import type { AuthResponse } from '../types/models';

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
});

// Attach JWT token to every request
axiosInstance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }
    // IMPORTANT: Never manually set Content-Type for FormData/multipart requests.
    // Let the browser set it automatically with the correct boundary.
    // Only explicitly set for JSON requests if needed.
    return config;
  },
  (error: AxiosError) => Promise.reject(error)
);

// Access tokens are short-lived (15 min, see backend jwt.expiration-ms) on
// purpose now that refresh tokens exist — a 401 no longer automatically
// means "log the user out," it usually just means "the access token
// expired, silently get a new one." Concurrent requests that all 401 at
// once (e.g. a dashboard firing several API calls together) must not each
// trigger their own refresh call — that would race against the backend's
// refresh-token rotation (see TokenService.rotateRefreshToken) and revoke
// each other. inFlightRefresh makes every concurrent 401 await the SAME
// refresh call instead.
let inFlightRefresh: Promise<string | null> | null = null;

function redirectToLogin() {
  const role = localStorage.getItem('role');
  clearAuthStorage();
  window.location.href = role === 'ADMIN' ? '/auth/admin' : '/auth/employee';
}

async function performRefresh(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    // Deliberately a raw axios call, not axiosInstance — going through
    // axiosInstance here would re-enter this same interceptor if the
    // refresh call itself ever 401s, which is exactly the kind of infinite
    // loop this function exists to prevent.
    const res = await axios.post<AuthResponse>(`${API_BASE_URL}/api/auth/refresh`, { refreshToken });
    updateTokens(res.data.token, res.data.refreshToken);
    return res.data.token;
  } catch {
    return null;
  }
}

// Response interceptor — only redirect on 401 (token expired/invalid).
// Do NOT redirect on 403 (forbidden) — that just means the user lacks permission
// for that specific endpoint, not that they are logged out.
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;

    if (error.response && error.response.status === 401 && original && !original._retried) {
      // Never attempt to refresh in response to the refresh/login endpoints
      // themselves failing — that's a real "you are logged out," not an
      // expired-access-token blip.
      const url = original.url || '';
      if (url.includes('/api/auth/refresh') || url.includes('/api/auth/login') || url.includes('/api/admin/login')) {
        redirectToLogin();
        return Promise.reject(error);
      }

      original._retried = true;
      if (!inFlightRefresh) {
        inFlightRefresh = performRefresh().finally(() => { inFlightRefresh = null; });
      }
      const newToken = await inFlightRefresh;

      if (newToken) {
        original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
        return axiosInstance.request(original);
      }
      redirectToLogin();
    }

    // 403 = forbidden but authenticated, or any other error. Don't log out — just reject.
    return Promise.reject(error);
  }
);

export default axiosInstance;

import { flagSessionExpired } from '../../features/auth/components/sessionNotice';
import axios, { type InternalAxiosRequestConfig, type AxiosError, type AxiosRequestConfig } from 'axios';
import { API_BASE_URL } from './apiConfig';
import { clearAuthStorage, getRefreshToken, updateTokens } from '../lib/authStorage';
import type { AuthResponse } from '../types/models';

const axiosInstance = axios.create({
  baseURL: API_BASE_URL,
});

// Attach JWT token to every request
axiosInstance.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    let token = localStorage.getItem('token');
    // Renew a token that is about to expire (or already has) before sending, so the request
    // doesn't fail first. Never for the sign-in/refresh calls themselves.
    if (token && !isAuthCall(config.url) && secondsLeft(token) < 20 && getRefreshToken()) {
      if (!inFlightRefresh) inFlightRefresh = performRefresh().finally(() => { inFlightRefresh = null; });
      const fresh = await inFlightRefresh;
      if (fresh && fresh !== TRANSIENT) token = fresh;
    }
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

const isAuthCall = (url?: string) => !!url && /\/api\/(auth\/(login|refresh|forgot-password|reset-password)|admin\/login)/.test(url);

/** Seconds until the access token expires; 0 when it can't be read. Only used to decide when to refresh. */
function secondsLeft(token: string): number {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const exp = JSON.parse(atob(part)).exp;
    return typeof exp === 'number' ? exp - Date.now() / 1000 : 0;
  } catch { return 0; }
}

function redirectToLogin() {
  const role = localStorage.getItem('role');
  clearAuthStorage();
  flagSessionExpired();
  window.location.href = role === 'ADMIN' ? '/auth/admin' : '/auth/employee';
}

const TRANSIENT = '__transient__';

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
  } catch (e) {
    const status = (e as AxiosError).response?.status;
    // Only a real rejection of the refresh token means "signed out". A network blip or server
    // restart must not throw the person out of their session.
    if (status === 400 || status === 401 || status === 403) return null;
    return TRANSIENT;
  }
}

// Response interceptor — only redirect on 401 (token expired/invalid).
// Do NOT redirect on 403 (forbidden) — that just means the user lacks permission
// for that specific endpoint, not that they are logged out.
axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;

    const token = localStorage.getItem('token');
    // Older servers answered 403 (not 401) for an expired token.
    const expiredButForbidden = error.response?.status === 403 && !!token && secondsLeft(token) <= 0 && !!getRefreshToken();
    if (error.response && (error.response.status === 401 || expiredButForbidden) && original && !original._retried) {
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

      if (newToken === TRANSIENT) return Promise.reject(error);
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

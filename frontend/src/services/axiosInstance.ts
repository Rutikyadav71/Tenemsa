import axios, { type InternalAxiosRequestConfig, type AxiosError } from 'axios';
import { API_BASE_URL } from './apiConfig';

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

// Response interceptor — only redirect on 401 (token expired/invalid).
// Do NOT redirect on 403 (forbidden) — that just means the user lacks permission
// for that specific endpoint, not that they are logged out.
axiosInstance.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response && error.response.status === 401) {
      // Token invalid or expired → force re-login
      const role = localStorage.getItem('role');
      localStorage.clear();
      window.location.href = role === 'ADMIN' ? '/auth/admin' : '/auth';
    }
    // 403 = forbidden but authenticated. Don't log out — just reject the promise.
    // The calling component should handle this gracefully.
    return Promise.reject(error);
  }
);

export default axiosInstance;

/// <reference types="vite/client" />
import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { tokenStore } from './tokenStore';

/**
 * Central Axios instance for all MediSphere API calls.
 *
 * Responsibilities:
 *  - Attaches Bearer token from in-memory token store on every request
 *  - On 401, attempts a one-shot token refresh, then retries the original request
 *  - On refresh failure, dispatches a custom 'medisphere:auth-expired' event
 *    so AuthContext can react and redirect to login
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

// Flag to prevent multiple simultaneous refresh calls
let isRefreshing = false;
// Queue of requests waiting for the token to be refreshed
let failedRequestQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null = null): void {
  failedRequestQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token!);
  });
  failedRequestQueue = [];
}

const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,   // Required for httpOnly refresh token cookie
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30_000,
});

// ── Request interceptor — attach access token ─────────────────────────────

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenStore.get();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ── Response interceptor — handle 401 with one-shot refresh ──────────────

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Only attempt refresh on 401, only once per request, and not for the auth endpoints themselves
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/api/auth/')
    ) {
      if (isRefreshing) {
        // Another refresh is in progress — queue this request until it completes
        return new Promise((resolve, reject) => {
          failedRequestQueue.push({
            resolve: (token: string) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              resolve(api(originalRequest));
            },
            reject,
          });
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Attempt token refresh — uses httpOnly cookie automatically
        const refreshResponse = await axios.post(
          `${BASE_URL}/api/auth/refresh`,
          {},
          { withCredentials: true },
        );

        const newToken: string = refreshResponse.data.accessToken;
        tokenStore.set(newToken);
        processQueue(null, newToken);

        // Retry original request with new token
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);

      } catch (refreshError) {
        // Refresh failed — clear token and notify the app
        tokenStore.clear();
        processQueue(refreshError, null);

        // Dispatch event so AuthContext can redirect to login
        window.dispatchEvent(new CustomEvent('medisphere:auth-expired'));

        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  },
);

export default api;

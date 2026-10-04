import { createContext, useCallback, useEffect, useRef, useState } from 'react';
import * as authApi from '../api/auth';
import { tokenStore } from '../api/tokenStore';
import type { ProviderResponse } from '../schemas/auth.schema';

// ── Context shape ─────────────────────────────────────────────────────────

export interface AuthContextValue {
  /** Currently authenticated provider, or null if not authenticated. */
  currentUser: ProviderResponse | null;
  /** True after session restoration has completed (success or failure). */
  isAuthenticated: boolean;
  /** True while session restoration or login/logout is in progress. */
  isLoading: boolean;
  /** Non-null after a failed login attempt. Cleared on next login attempt. */
  authError: string | null;
  /** Authenticate with email + password. Throws on failure. */
  login: (email: string, password: string) => Promise<void>;
  /** Logout and clear all session state. */
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

// ── Provider ──────────────────────────────────────────────────────────────

export function AuthContextProvider({ children }: { children: React.ReactNode }) {
  const [currentUser, setCurrentUser] = useState<ProviderResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);   // true until session restore completes
  const [authError, setAuthError] = useState<string | null>(null);

  const isAuthenticated = currentUser !== null;

  // ── Session restoration on mount ─────────────────────────────────────────

  const restorationAttempted = useRef(false);

  useEffect(() => {
    if (restorationAttempted.current) return;
    restorationAttempted.current = true;

    async function restoreSession() {
      try {
        // Try to exchange the httpOnly refresh cookie for a new access token
        const authResponse = await authApi.refreshToken();
        if (authResponse) {
          tokenStore.set(authResponse.accessToken);
          setCurrentUser(authResponse.provider);
        }
      } catch {
        // No valid session — stay unauthenticated, no error shown
        tokenStore.clear();
      } finally {
        setIsLoading(false);
      }
    }

    restoreSession();
  }, []);

  // ── Listen for token-expired events (from Axios interceptor) ─────────────

  useEffect(() => {
    function handleAuthExpired() {
      tokenStore.clear();
      setCurrentUser(null);
    }

    window.addEventListener('medisphere:auth-expired', handleAuthExpired);
    return () => window.removeEventListener('medisphere:auth-expired', handleAuthExpired);
  }, []);

  // ── Login ─────────────────────────────────────────────────────────────────

  const login = useCallback(async (email: string, password: string) => {
    setAuthError(null);
    setIsLoading(true);
    try {
      const authResponse = await authApi.login(email, password);
      tokenStore.set(authResponse.accessToken);
      setCurrentUser(authResponse.provider);
    } catch (error: unknown) {
      tokenStore.clear();
      setCurrentUser(null);

      // Map error to user-facing message
      const message = extractErrorMessage(error);
      setAuthError(message);
      throw error;  // Re-throw so Login.tsx can react (e.g. stop spinner)
    } finally {
      setIsLoading(false);
    }
  }, []);

  // ── Logout ────────────────────────────────────────────────────────────────

  const logout = useCallback(async () => {
    setIsLoading(true);
    try {
      await authApi.logout();
    } catch {
      // Logout should always succeed from the user's perspective,
      // even if the server call fails
    } finally {
      tokenStore.clear();
      setCurrentUser(null);
      setAuthError(null);
      setIsLoading(false);
    }
  }, []);

  // ── Context value ─────────────────────────────────────────────────────────

  const value: AuthContextValue = {
    currentUser,
    isAuthenticated,
    isLoading,
    authError,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// ── Error message extraction ──────────────────────────────────────────────

function extractErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosError = error as { response?: { status?: number; data?: { message?: string } } };
    const status = axiosError.response?.status;

    if (status === 401) {
      return 'Invalid credentials. Please check your email and password.';
    }
    if (status === 400) {
      return axiosError.response?.data?.message ?? 'Please check your input and try again.';
    }
    if (status && status >= 500) {
      return 'Unable to connect to the server. Please try again.';
    }
  }

  if (error && typeof error === 'object' && 'code' in error) {
    const networkError = error as { code?: string };
    if (networkError.code === 'ERR_NETWORK' || networkError.code === 'ECONNABORTED') {
      return 'Unable to connect to the server. Please check your network connection.';
    }
  }

  return 'An unexpected error occurred. Please try again.';
}

import api from './client';
import {
  AuthResponseSchema,
  ProviderResponseSchema,
  type AuthResponse,
  type ProviderResponse,
} from '../schemas/auth.schema';

/**
 * Authentication API service.
 * All functions parse responses through Zod schemas before returning.
 */

/**
 * POST /api/auth/login
 * Authenticates a provider. Sets the httpOnly refresh token cookie server-side.
 */
export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await api.post('/api/auth/login', { email, password });
  return AuthResponseSchema.parse(response.data);
}

/**
 * POST /api/auth/logout
 * Revokes the refresh token server-side and clears the cookie.
 */
export async function logout(): Promise<void> {
  await api.post('/api/auth/logout');
}

/**
 * POST /api/auth/refresh
 * Exchanges the httpOnly refresh token cookie for a new access token.
 * Returns null if the refresh token is expired or invalid (caller should redirect to login).
 */
export async function refreshToken(): Promise<AuthResponse | null> {
  try {
    const response = await api.post('/api/auth/refresh');
    return AuthResponseSchema.parse(response.data);
  } catch {
    return null;
  }
}

/**
 * GET /api/auth/me
 * Returns the authenticated provider's profile.
 */
export async function getMe(): Promise<ProviderResponse> {
  const response = await api.get('/api/auth/me');
  return ProviderResponseSchema.parse(response.data);
}

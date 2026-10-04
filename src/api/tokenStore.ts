/**
 * In-memory access token store.
 *
 * The access token is intentionally NOT stored in localStorage or sessionStorage
 * to reduce XSS exposure. It lives only in module memory for the lifetime of the
 * browser tab. Session restoration is handled by the httpOnly refresh token cookie
 * (sent automatically by the browser to /api/auth/refresh).
 */

let _accessToken: string | null = null;

export const tokenStore = {
  get(): string | null {
    return _accessToken;
  },

  set(token: string): void {
    _accessToken = token;
  },

  clear(): void {
    _accessToken = null;
  },

  hasToken(): boolean {
    return _accessToken !== null;
  },
};

import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../mocks/server';
import { http, HttpResponse } from 'msw';
import { AuthContextProvider } from '../../context/AuthContext';
import { useAuth } from '../../hooks/useAuth';
import { tokenStore } from '../../api/tokenStore';

// ── MSW server lifecycle ──────────────────────────────────────────────────
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  tokenStore.clear();
});
afterAll(() => server.close());

// ── Test helper component ─────────────────────────────────────────────────
function TestConsumer() {
  const { currentUser, isAuthenticated, isLoading, authError, login, logout } = useAuth();
  return (
    <div>
      <div data-testid="loading">{String(isLoading)}</div>
      <div data-testid="authenticated">{String(isAuthenticated)}</div>
      <div data-testid="user-name">{currentUser?.name ?? 'none'}</div>
      <div data-testid="user-role">{currentUser?.role ?? 'none'}</div>
      <div data-testid="auth-error">{authError ?? 'none'}</div>
      <button onClick={() => login('a.mehta@medisphere.dev', 'Medisphere@123')}>
        Login valid
      </button>
      <button onClick={() => login('wrong@medisphere.dev', 'WrongPass@1')}>
        Login invalid
      </button>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

function renderWithAuth() {
  return render(
    <AuthContextProvider>
      <TestConsumer />
    </AuthContextProvider>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────

describe('AuthContext — initial state', () => {
  it('starts in loading state and resolves to unauthenticated when no refresh cookie exists', async () => {
    renderWithAuth();

    // Initially loading
    expect(screen.getByTestId('loading').textContent).toBe('true');

    // After session restoration attempt (refresh returns 401 by default in mock)
    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    expect(screen.getByTestId('authenticated').textContent).toBe('false');
    expect(screen.getByTestId('user-name').textContent).toBe('none');
  });
});

describe('AuthContext — login', () => {
  it('sets authenticated=true and currentUser on successful login', async () => {
    const user = userEvent.setup();
    renderWithAuth();

    // Wait for loading to finish
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    await user.click(screen.getByText('Login valid'));

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('true');
      expect(screen.getByTestId('user-name').textContent).toBe('Dr. A. Mehta');
      expect(screen.getByTestId('user-role').textContent).toBe('CLINICIAN');
    });
  });

  it('stores the access token in memory after successful login', async () => {
    const user = userEvent.setup();
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    await user.click(screen.getByText('Login valid'));

    await waitFor(() => {
      expect(tokenStore.hasToken()).toBe(true);
      expect(tokenStore.get()).toBe('mock.jwt.access.token');
    });
  });

  it('sets authError and remains unauthenticated on 401', async () => {
    const user = userEvent.setup();
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    // Wrap in try/catch — the login call rejects on 401, which is expected
    await act(async () => {
      try {
        await user.click(screen.getByText('Login invalid'));
      } catch { /* expected rejection */ }
    });

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('false');
      expect(screen.getByTestId('auth-error').textContent).toContain('Invalid credentials');
    });

    expect(tokenStore.hasToken()).toBe(false);
  });

  it('sets a server error message on 500', async () => {
    server.use(
      http.post('http://localhost:8080/api/auth/login', () => {
        return HttpResponse.json({ message: 'Internal error' }, { status: 500 });
      }),
    );

    const user = userEvent.setup();
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    await act(async () => {
      try {
        await user.click(screen.getByText('Login valid'));
      } catch { /* expected rejection */ }
    });

    await waitFor(() => {
      expect(screen.getByTestId('auth-error').textContent).toContain('server');
    });
  });
});

describe('AuthContext — logout', () => {
  it('clears currentUser and token on logout', async () => {
    const user = userEvent.setup();
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    // Login first
    await user.click(screen.getByText('Login valid'));
    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));

    // Then logout
    await user.click(screen.getByText('Logout'));

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('false');
      expect(screen.getByTestId('user-name').textContent).toBe('none');
    });

    expect(tokenStore.hasToken()).toBe(false);
  });

  it('still clears state if the logout API call fails', async () => {
    server.use(
      http.post('http://localhost:8080/api/auth/logout', () => {
        return HttpResponse.json({ message: 'Server error' }, { status: 500 });
      }),
    );

    const user = userEvent.setup();
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    await user.click(screen.getByText('Login valid'));
    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));

    await user.click(screen.getByText('Logout'));

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('false');
    });
  });
});

describe('AuthContext — token expiry event', () => {
  it('clears auth state when medisphere:auth-expired is dispatched', async () => {
    const user = userEvent.setup();
    renderWithAuth();

    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'));

    await user.click(screen.getByText('Login valid'));
    await waitFor(() => expect(screen.getByTestId('authenticated').textContent).toBe('true'));

    // Simulate token expiry event from Axios interceptor
    act(() => {
      window.dispatchEvent(new CustomEvent('medisphere:auth-expired'));
    });

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('false');
    });

    expect(tokenStore.hasToken()).toBe(false);
  });
});

describe('useAuth — outside provider', () => {
  it('throws when used outside AuthContextProvider', () => {
    // Suppress React error boundary console output for this test
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    function BareConsumer() {
      useAuth();
      return null;
    }

    expect(() => render(<BareConsumer />)).toThrow('useAuth must be used within an AuthContextProvider');

    consoleSpy.mockRestore();
  });
});

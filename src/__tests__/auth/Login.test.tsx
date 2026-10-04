import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { server } from '../mocks/server';
import { http, HttpResponse } from 'msw';
import { AuthContextProvider } from '../../context/AuthContext';
import { QueryProvider } from '../../context/QueryProvider';
import { Login } from '../../pages/Login';
import { tokenStore } from '../../api/tokenStore';

// ── MSW server lifecycle ──────────────────────────────────────────────────
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => {
  server.resetHandlers();
  tokenStore.clear();
});
afterAll(() => server.close());

// ── Render helper ─────────────────────────────────────────────────────────
function renderLogin() {
  return render(
    <MemoryRouter>
      <QueryProvider>
        <AuthContextProvider>
          <Login />
        </AuthContextProvider>
      </QueryProvider>
    </MemoryRouter>,
  );
}

// ── Tests ─────────────────────────────────────────────────────────────────

describe('Login page — rendering', () => {
  it('renders the MediSphere branding', async () => {
    renderLogin();
    await waitFor(() => expect(screen.getByText('MediSphere Cognitive Twin')).toBeInTheDocument());
    expect(screen.getByText('AI Health Prediction Platform')).toBeInTheDocument();
  });

  it('renders email and password fields', async () => {
    renderLogin();
    await waitFor(() => {
      expect(screen.getByLabelText(/Provider ID \/ Email/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Password/i)).toBeInTheDocument();
    });
  });

  it('renders the Sign In button', async () => {
    renderLogin();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Sign In/i })).toBeInTheDocument();
    });
  });

  it('renders the HIPAA compliance section', async () => {
    renderLogin();
    await waitFor(() => {
      expect(screen.getByText('Secure Clinical Environment')).toBeInTheDocument();
    });
  });
});

describe('Login page — validation', () => {
  it('shows required error when email is empty on submit', async () => {
    const user = userEvent.setup();
    renderLogin();

    await waitFor(() => screen.getByRole('button', { name: /Sign In/i }));

    await user.click(screen.getByRole('button', { name: /Sign In/i }));

    await waitFor(() => {
      expect(screen.getByText(/Provider ID or email is required/i)).toBeInTheDocument();
    });
  });

  it('shows required error when password is empty on submit', async () => {
    const user = userEvent.setup();
    renderLogin();

    await waitFor(() => screen.getByRole('button', { name: /Sign In/i }));

    await user.type(screen.getByLabelText(/Provider ID \/ Email/i), 'test@medisphere.dev');
    await user.click(screen.getByRole('button', { name: /Sign In/i }));

    await waitFor(() => {
      expect(screen.getByText(/Password is required/i)).toBeInTheDocument();
    });
  });
});

describe('Login page — API integration', () => {
  it('shows "Verifying credentials…" while submitting', async () => {
    // Slow down the mock response to observe loading state
    server.use(
      http.post('http://localhost:8080/api/auth/login', async () => {
        await new Promise((r) => setTimeout(r, 100));
        return HttpResponse.json({
          accessToken: 'mock.token',
          provider: {
            id: 'PROV-001', name: 'Dr. A. Mehta', email: 'a.mehta@medisphere.dev',
            role: 'CLINICIAN', active: true,
            notificationPrefs: { critical: true, risk: true, approvals: true, system: false },
          },
        });
      }),
    );

    const user = userEvent.setup();
    renderLogin();

    await waitFor(() => screen.getByLabelText(/Provider ID \/ Email/i));

    await user.type(screen.getByLabelText(/Provider ID \/ Email/i), 'a.mehta@medisphere.dev');
    await user.type(screen.getByLabelText(/Password/i), 'Medisphere@123');
    await user.click(screen.getByRole('button', { name: /Sign In/i }));

    expect(screen.getByText(/Verifying credentials/i)).toBeInTheDocument();
  });

  it('shows error message on 401 response', async () => {
    const user = userEvent.setup();
    renderLogin();

    await waitFor(() => screen.getByLabelText(/Provider ID \/ Email/i));

    await user.type(screen.getByLabelText(/Provider ID \/ Email/i), 'wrong@medisphere.dev');
    await user.type(screen.getByLabelText(/Password/i), 'WrongPass@1');
    await user.click(screen.getByRole('button', { name: /Sign In/i }));

    await waitFor(() => {
      expect(screen.getByText(/Invalid credentials/i)).toBeInTheDocument();
    });
  });

  it('shows server error message on 500 response', async () => {
    server.use(
      http.post('http://localhost:8080/api/auth/login', () => {
        return HttpResponse.json({}, { status: 500 });
      }),
    );

    const user = userEvent.setup();
    renderLogin();

    await waitFor(() => screen.getByLabelText(/Provider ID \/ Email/i));

    await user.type(screen.getByLabelText(/Provider ID \/ Email/i), 'a.mehta@medisphere.dev');
    await user.type(screen.getByLabelText(/Password/i), 'Medisphere@123');
    await user.click(screen.getByRole('button', { name: /Sign In/i }));

    await waitFor(() => {
      expect(screen.getByText(/Unable to connect to the server/i)).toBeInTheDocument();
    });
  });

  it('does not call API when fields are empty', async () => {
    let apiCalled = false;
    server.use(
      http.post('http://localhost:8080/api/auth/login', () => {
        apiCalled = true;
        return HttpResponse.json({}, { status: 200 });
      }),
    );

    const user = userEvent.setup();
    renderLogin();

    await waitFor(() => screen.getByRole('button', { name: /Sign In/i }));
    await user.click(screen.getByRole('button', { name: /Sign In/i }));

    // Give time for any async calls
    await new Promise((r) => setTimeout(r, 100));
    expect(apiCalled).toBe(false);
  });
});

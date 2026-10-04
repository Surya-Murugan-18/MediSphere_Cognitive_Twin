import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { Dashboard } from '../../pages/Dashboard';
import { tokenStore } from '../../api/tokenStore';
import { AuthContext } from '../../context/AuthContext';
import type { AuthContextValue } from '../../context/AuthContext';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

const mockAuthValue: AuthContextValue = {
  currentUser: {
    id: 'PROV-001',
    name: 'Dr. A. Mehta',
    email: 'a.mehta@medisphere.dev',
    role: 'CLINICIAN',
    specialty: 'Cardiology',
    facility: 'Hospital A — Northside General',
    active: true,
  },
  isAuthenticated: true,
  isLoading: false,
  authError: null,
  login: async () => {},
  logout: async () => {},
};

function renderDashboard() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <AuthContext.Provider value={mockAuthValue}>
          <Dashboard />
        </AuthContext.Provider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── All 8 KPIs from getDashboardStats ─────────────────────────────────────

describe('Dashboard — KPI cards from single API call', () => {
  it('renders patients onboarded KPI from getDashboardStats', async () => {
    renderDashboard();

    await waitFor(() =>
      expect(screen.getByText('1,247')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('renders wearables online KPI', async () => {
    renderDashboard();

    await waitFor(() =>
      expect(screen.getAllByText(/892/)[0]).toBeInTheDocument(),
      { timeout: 10000 },
    );
  });

  it('renders high risk patients KPI', async () => {
    renderDashboard();

    await waitFor(() =>
      expect(screen.getByText('23')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('renders active care plans KPI', async () => {
    renderDashboard();

    await waitFor(() =>
      expect(screen.getByText('1124')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });
});

// ── Risk donut uses API data ───────────────────────────────────────────────

describe('Dashboard — risk distribution from API', () => {
  it('calls /api/population/risk-distribution endpoint', async () => {
    let riskDistCalled = false;
    server.use(
      http.get('http://localhost:8080/api/population/risk-distribution', () => {
        riskDistCalled = true;
        return HttpResponse.json([
          { name: 'Low', value: 812, tone: '#0e9f7e' },
          { name: 'High', value: 23,  tone: '#d13f3f' },
        ]);
      }),
    );

    renderDashboard();

    await waitFor(() => expect(riskDistCalled).toBe(true), { timeout: 8000 });
  });
});

// ── Recent activity from audit log ────────────────────────────────────────

describe('Dashboard — recent activity from audit log', () => {
  it('renders audit log entries in Recent clinical activity section', async () => {
    renderDashboard();

    await waitFor(() =>
      expect(screen.getByText('Viewed Digital Twin')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('does not import from src/data/system.ts for recent activity', async () => {
    // The static recentActivity export is no longer used — if the API returns
    // entries, they should be from the mock server, not the static file.
    server.use(
      http.get('http://localhost:8080/api/audit', () =>
        HttpResponse.json({
          content: [{ id: 'AU-999', timestamp: '2026-09-21T12:00:00Z', userId: 'PROV-001',
            userName: 'Dr. B. Test', userRole: 'CLINICIAN', action: 'Test Action', module: 'Test', status: 'Success' }],
          totalElements: 1, totalPages: 1, page: 0, size: 4,
        }),
      ),
    );

    renderDashboard();

    await waitFor(() =>
      expect(screen.getByText('Test Action')).toBeInTheDocument(),
      { timeout: 8000 },
    );
    // The old static entry should NOT be present
    expect(screen.queryByText('Risk reviewed — John Doe')).not.toBeInTheDocument();
  });
});

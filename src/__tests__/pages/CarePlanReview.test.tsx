import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { CarePlanReview } from '../../pages/CarePlanReview';
import { tokenStore } from '../../api/tokenStore';

// Mock useAuth so provider identity is deterministic in tests
vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    currentUser: { id: 'PROV-001', name: 'Dr. A. Mehta', role: 'CLINICIAN', email: 'test@test.com', active: true },
    isAuthenticated: true,
    isLoading: false,
    authError: null,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderReview(planId = 'CP-001') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/care-plans/${planId}/review`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/care-plans/:planId/review"    element={<CarePlanReview />} />
          <Route path="/care-plans/:planId/adherence" element={<div>Adherence page</div>} />
          <Route path="/care-plans"                   element={<div>Care plans list</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Loading and rendering ──────────────────────────────────────────────────

describe('CarePlanReview — rendering', () => {
  it('renders page title', async () => {
    renderReview('CP-001');
    await waitFor(() =>
      expect(screen.getByText('Care plan review')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('renders plan goal from API', async () => {
    renderReview('CP-001');
    await waitFor(() =>
      expect(screen.getAllByText(/HbA1c < 7\.0%/).length).toBeGreaterThan(0),
      { timeout: 8000 },
    );
  });

  it('renders safety checks from API', async () => {
    renderReview('CP-001');
    await waitFor(() =>
      expect(screen.getByText('Safety checks')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('renders first recommendation from API', async () => {
    renderReview('CP-001');
    await waitFor(() =>
      expect(screen.getByText('Medication Management')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('redirects to care-plans when plan not found', async () => {
    server.use(
      http.get('http://localhost:8080/api/care-plans/CP-999', () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 }),
      ),
    );
    renderReview('CP-999');
    await waitFor(() =>
      expect(screen.getByText('Care plans list')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });
});

// ── Provider identity from useAuth ────────────────────────────────────────

describe('CarePlanReview — provider identity', () => {
  it('displays provider name from useAuth, not hardcoded', async () => {
    renderReview('CP-001');
    await waitFor(() => {
      const input = screen.getByDisplayValue('Dr. A. Mehta');
      expect(input).toBeInTheDocument();
    }, { timeout: 8000 });
  });

  it('displays provider ID from useAuth, not hardcoded', async () => {
    renderReview('CP-001');
    await waitFor(() => {
      const input = screen.getByDisplayValue('PROV-001');
      expect(input).toBeInTheDocument();
    }, { timeout: 8000 });
  });

  it('provider name field is readonly', async () => {
    renderReview('CP-001');
    await waitFor(() => {
      const input = screen.getByDisplayValue('Dr. A. Mehta') as HTMLInputElement;
      expect(input.readOnly).toBe(true);
    }, { timeout: 8000 });
  });
});

// ── Approval workflow ─────────────────────────────────────────────────────

describe('CarePlanReview — approval', () => {
  it('opens confirmation modal on Confirm approval click', async () => {
    renderReview('CP-001');
    await waitFor(() =>
      expect(screen.getByText('Confirm approval')).toBeInTheDocument(),
      { timeout: 8000 },
    );
    fireEvent.click(screen.getAllByText('Confirm approval')[0]);
    await waitFor(() =>
      expect(screen.getByText('Approve plan')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('Confirm approval button is disabled for already-active plan', async () => {
    renderReview('CP-002');
    await waitFor(() =>
      expect(screen.getByText('Approval confirmed')).toBeInTheDocument(),
      { timeout: 8000 },
    );
    const btn = screen.getByText('Approval confirmed') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });
});

// ── Rejection workflow ────────────────────────────────────────────────────

describe('CarePlanReview — rejection', () => {
  it('opens rejection modal on Reject click', async () => {
    renderReview('CP-001');
    await waitFor(() =>
      expect(screen.getByText('Reject')).toBeInTheDocument(),
      { timeout: 8000 },
    );
    // Click reject button
    const rejectBtn = screen.getAllByText('Reject').find(
      (el) => el.closest('button'),
    ) as HTMLElement;
    fireEvent.click(rejectBtn);
    await waitFor(() =>
      expect(screen.getByText('Reject this care plan?')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('reject button in modal is disabled without reason', async () => {
    renderReview('CP-001');
    await waitFor(() => screen.getByText('Reject'), { timeout: 8000 });
    const rejectBtns = screen.getAllByText('Reject');
    fireEvent.click(rejectBtns[0]);
    await waitFor(() => screen.getByText('Reject this care plan?'), { timeout: 3000 });
    const modalRejectBtn = screen.getAllByText('Reject plan')[0] as HTMLButtonElement;
    expect(modalRejectBtn.disabled).toBe(true);
  });
});

// ── Already-approved plan display ─────────────────────────────────────────

describe('CarePlanReview — approved plan', () => {
  it('shows approved badge for ACTIVE plan', async () => {
    renderReview('CP-002');
    await waitFor(() =>
      expect(screen.getByText('Approved')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('shows Track adherence link for ACTIVE plan', async () => {
    renderReview('CP-002');
    await waitFor(() =>
      expect(screen.getByText('Track adherence')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });
});

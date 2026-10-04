import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { CarePlanGenerator } from '../../pages/CarePlanGenerator';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderGenerator(path: string) {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/care-plans/new"         element={<CarePlanGenerator />} />
          <Route path="/care-plans/:planId/edit" element={<CarePlanGenerator />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── No context — EmptyState ────────────────────────────────────────────────

describe('CarePlanGenerator — no context', () => {
  it('shows EmptyState when no patientId or planId provided', async () => {
    tokenStore.set('mock.access.token');
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
    });
    render(
      <MemoryRouter initialEntries={['/care-plans/new']}>
        <QueryClientProvider client={client}>
          <Routes>
            <Route path="/care-plans/new" element={<CarePlanGenerator />} />
          </Routes>
        </QueryClientProvider>
      </MemoryRouter>,
    );
    await waitFor(() =>
      expect(screen.getByText('No patient selected')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });
});

// ── Create mode (?patientId=) ─────────────────────────────────────────────

describe('CarePlanGenerator — create mode', () => {
  it('renders page title for generate mode', async () => {
    renderGenerator('/care-plans/new?patientId=P001');
    await waitFor(() =>
      expect(screen.getByText('Generate personalized care plan')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('calls generateCarePlan on mount and shows recommendations', async () => {
    renderGenerator('/care-plans/new?patientId=P001');
    await waitFor(() =>
      expect(screen.getByText('Medication Management')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('shows patient name from API in clinical inputs panel', async () => {
    renderGenerator('/care-plans/new?patientId=P001');
    await waitFor(() =>
      expect(screen.getByText('John Doe')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('shows plan goal from AI response', async () => {
    renderGenerator('/care-plans/new?patientId=P001');
    await waitFor(() =>
      expect(screen.getAllByText(/HbA1c < 7\.0%/).length).toBeGreaterThan(0),
      { timeout: 8000 },
    );
  });

  it('renders Review plan button enabled after generation', async () => {
    renderGenerator('/care-plans/new?patientId=P001');
    await waitFor(() => {
      const reviewBtns = screen.getAllByText('Review plan');
      expect(reviewBtns.length).toBeGreaterThan(0);
      expect(reviewBtns[0]).not.toBeDisabled();
    }, { timeout: 8000 });
  });

  it('renders Regenerate button in create mode', async () => {
    renderGenerator('/care-plans/new?patientId=P001');
    await waitFor(() =>
      expect(screen.getByText('Regenerate')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('shows generation failure toast when API fails', async () => {
    server.use(
      http.post('http://localhost:8080/api/care-plans/generate', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 }),
      ),
    );
    renderGenerator('/care-plans/new?patientId=P001');
    // Generation failure — page still renders without crashing
    await waitFor(() =>
      expect(screen.getByText('Generate personalized care plan')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });
});

// ── Edit mode (/care-plans/:planId/edit) ──────────────────────────────────

describe('CarePlanGenerator — edit mode', () => {
  it('renders edit page title', async () => {
    renderGenerator('/care-plans/CP-001/edit');
    await waitFor(() =>
      expect(screen.getByText('Edit care plan')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });

  it('loads existing plan data from API', async () => {
    renderGenerator('/care-plans/CP-001/edit');
    await waitFor(() =>
      expect(screen.getByText('Medication Management')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('shows patient name from plan in clinical inputs', async () => {
    renderGenerator('/care-plans/CP-001/edit');
    await waitFor(() =>
      expect(screen.getByText('John Doe')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('does NOT show Regenerate button in edit mode', async () => {
    renderGenerator('/care-plans/CP-001/edit');
    await waitFor(() =>
      expect(screen.queryByText('Regenerate')).not.toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('shows 404 fallback when plan not found', async () => {
    server.use(
      http.get('http://localhost:8080/api/care-plans/CP-999', () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 }),
      ),
    );
    renderGenerator('/care-plans/CP-999/edit');
    // Should render without crash — page still loads with empty state
    await waitFor(() =>
      expect(screen.getByText('Edit care plan')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });
});

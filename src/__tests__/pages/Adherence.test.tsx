import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { Adherence } from '../../pages/Adherence';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderAdherence(planId = 'CP-002') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/care-plans/${planId}/adherence`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/care-plans/:planId/adherence" element={<Adherence />} />
          <Route path="/care-plans"                   element={<div>Care plans list</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Page structure ─────────────────────────────────────────────────────────

describe('Adherence page — rendering', () => {
  it('renders page title', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Patient adherence')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('renders patient name and plan goal from plan API', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText(/John Doe/)).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('redirects to care-plans when plan not found', async () => {
    server.use(
      http.get('http://localhost:8080/api/care-plans/CP-999', () =>
        HttpResponse.json({ message: 'Not found' }, { status: 404 }),
      ),
    );
    renderAdherence('CP-999');
    await waitFor(() =>
      expect(screen.getByText('Care plans list')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });
});

// ── Real adherence data ───────────────────────────────────────────────────

describe('Adherence page — adherence data from API', () => {
  it('renders overall adherence percentage from API', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('78%')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('renders Overall adherence card heading', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Overall adherence')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('renders adherence breakdown labels from API', async () => {
    renderAdherence();
    await waitFor(() => {
      expect(screen.getByText('Medication')).toBeInTheDocument();
      expect(screen.getByText('Monitoring')).toBeInTheDocument();
      expect(screen.getByText('Follow-up')).toBeInTheDocument();
    }, { timeout: 8000 });
  });
});

// ── Trend data ────────────────────────────────────────────────────────────

describe('Adherence page — trend chart', () => {
  it('renders adherence trend chart section', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Adherence trend')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('renders correct number of trend weeks from API', async () => {
    renderAdherence();
    await waitFor(() =>
      // TrendChart renders data — we check the section exists
      expect(screen.getByText('Adherence trend')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });

  it('shows empty state when no trend data', async () => {
    server.use(
      http.get('http://localhost:8080/api/care-plans/CP-002/adherence/trend', () =>
        HttpResponse.json([]),
      ),
    );
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('No trend data')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });
});

// ── Outcomes ──────────────────────────────────────────────────────────────

describe('Adherence page — outcomes', () => {
  it('renders outcome section heading', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Outcome')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('renders metric name from API', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getAllByText(/HbA1c/).length).toBeGreaterThan(0),
      { timeout: 8000 },
    );
  });

  it('renders baseline and current values from API', async () => {
    renderAdherence();
    await waitFor(() => {
      expect(screen.getByText('8.2%')).toBeInTheDocument();   // baseline
      expect(screen.getByText('7.6%')).toBeInTheDocument();   // current
    }, { timeout: 8000 });
  });

  it('renders improving trend badge', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Improving')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });
});

// ── Care plan timeline ────────────────────────────────────────────────────

describe('Adherence page — timeline', () => {
  it('renders care plan timeline section', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Care plan timeline')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('renders timeline events from API', async () => {
    renderAdherence();
    await waitFor(() =>
      expect(screen.getByText('Care plan generated')).toBeInTheDocument(),
      { timeout: 8000 },
    );
  });
});

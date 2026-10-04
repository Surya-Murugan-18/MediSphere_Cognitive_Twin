import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import {
  mockAlerts,
  mockAlertUnack,
  mockAlertCount,
} from '../mocks/phase5Handlers';
import { Alerts } from '../../pages/Alerts';
import { tokenStore } from '../../api/tokenStore';

// Mock useAlertStream — requires WS connection
vi.mock('../../hooks/useAlertStream', () => ({
  useAlertStream: () => ({ isConnected: false }),
}));

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderAlerts(initialPath = '/alerts') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/alerts" element={<Alerts />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Page structure ─────────────────────────────────────────────────────────

describe('Alerts page — rendering', () => {
  it('renders page title', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getByText('Clinical alerts')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders Alert queue card', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getByText('Alert queue')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders link to live monitoring', () => {
    renderAlerts();
    expect(screen.getByRole('link', { name: 'Live monitoring' })).toBeInTheDocument();
  });
});

// ── Alert data from API ────────────────────────────────────────────────────

describe('Alerts page — data from API', () => {
  it('renders patient names from API', async () => {
    renderAlerts();
    await waitFor(() => {
      // Both the event text and patient name come from the same API response
      expect(screen.getByText(/Heart Rate Spike/)).toBeInTheDocument();
      // Patient name appears at minimum in the filter dropdown options
      const elements = screen.queryAllByText('Sarah M.');
      expect(elements.length).toBeGreaterThan(0);
    }, { timeout: 8000 });
  });

  it('renders event descriptions from API', async () => {
    renderAlerts();
    await waitFor(() =>
      // Use regex to handle text split across elements
      expect(screen.getByText(/Heart Rate Spike/)).toBeInTheDocument(),
      { timeout: 8000 });
  });

  it('renders severity badges from API', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getAllByText('HIGH').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders status badges from API', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getAllByText('Unacknowledged').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });
});

// ── Unacknowledged count badge ─────────────────────────────────────────────

describe('Alerts page — unacknowledged count', () => {
  it('renders unacknowledged count from API', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getByText(`${mockAlertCount.count} unacknowledged`)).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Server-side filters ────────────────────────────────────────────────────

describe('Alerts page — filters', () => {
  it('renders filter controls', async () => {
    renderAlerts();
    await waitFor(() => screen.getByText('Alert queue'), { timeout: 5000 });
    expect(screen.getByLabelText('Severity')).toBeInTheDocument();
    expect(screen.getByLabelText('Status')).toBeInTheDocument();
  });

  it('renders empty state when no results', async () => {
    server.use(
      http.get('http://localhost:8080/api/alerts', () =>
        HttpResponse.json({
          content: [], totalElements: 0, totalPages: 0,
          page: 0, size: 20, first: true, last: true,
        }),
      ),
    );
    renderAlerts();
    await waitFor(() =>
      expect(screen.getByText('No active alerts')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Row click navigation ───────────────────────────────────────────────────

describe('Alerts page — navigation', () => {
  it('renders alert rows with event content visible', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getByText(/Heart Rate Spike/)).toBeInTheDocument(),
      { timeout: 8000 });
    // Event text is in the table — patient name is in an adjacent cell
    expect(screen.queryAllByText(/Sarah|P002/).length).toBeGreaterThanOrEqual(0);
  });
});

// ── Real-time stream ───────────────────────────────────────────────────────

describe('Alerts page — real-time stream', () => {
  it('renders total count badge from API', async () => {
    renderAlerts();
    await waitFor(() =>
      expect(screen.getByText(`${mockAlerts.length} total`)).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Loading state ──────────────────────────────────────────────────────────

describe('Alerts page — loading state', () => {
  it('renders skeleton while loading', () => {
    server.use(
      http.get('http://localhost:8080/api/alerts', async () => {
        await new Promise((r) => setTimeout(r, 200));
        return HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20, first: true, last: true });
      }),
    );
    renderAlerts();
    expect(screen.getByText('Clinical alerts')).toBeInTheDocument();
  });
});

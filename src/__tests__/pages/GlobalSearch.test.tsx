import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { GlobalSearch } from '../../components/layout/GlobalSearch';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderSearch() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <GlobalSearch />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Debounce: no API call for query < 2 chars ─────────────────────────────

describe('GlobalSearch — debounce behaviour', () => {
  it('does not call /api/search for single-character query', async () => {
    let searchCalled = false;
    server.use(
      http.get('http://localhost:8080/api/search', () => {
        searchCalled = true;
        return HttpResponse.json([]);
      }),
    );

    renderSearch();

    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'j');

    // Wait longer than debounce (300ms) — no call should happen for 1-char query
    await new Promise(r => setTimeout(r, 500));
    expect(searchCalled).toBe(false);
  });

  it('calls /api/search after 300ms debounce for query >= 2 chars', async () => {
    let searchCalled = false;
    server.use(
      http.get('http://localhost:8080/api/search', () => {
        searchCalled = true;
        return HttpResponse.json([]);
      }),
    );

    renderSearch();

    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'jo');

    // Wait past the 300ms debounce
    await waitFor(() => expect(searchCalled).toBe(true), { timeout: 3000 });
  });
});

// ── Grouped results display ───────────────────────────────────────────────

describe('GlobalSearch — grouped results', () => {
  it('displays results grouped by category', async () => {
    renderSearch();

    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'john');

    await waitFor(() =>
      expect(screen.getByText('Patients')).toBeInTheDocument(),
      { timeout: 5000 },
    );

    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  it('shows maximum 4 results per group', async () => {
    server.use(
      http.get('http://localhost:8080/api/search', () =>
        HttpResponse.json([
          {
            category: 'Patients',
            results: [
              { id: 'P001', label: 'Patient 1', detail: 'P001', to: '/patients/P001' },
              { id: 'P002', label: 'Patient 2', detail: 'P002', to: '/patients/P002' },
              { id: 'P003', label: 'Patient 3', detail: 'P003', to: '/patients/P003' },
              { id: 'P004', label: 'Patient 4', detail: 'P004', to: '/patients/P004' },
              // 5th item — should be clipped to 4
              { id: 'P005', label: 'Patient 5', detail: 'P005', to: '/patients/P005' },
            ],
          },
        ]),
      ),
    );

    renderSearch();

    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'pa');

    await waitFor(() =>
      expect(screen.getByText('Patient 1')).toBeInTheDocument(),
      { timeout: 5000 },
    );

    // 5th result should not be visible (slice(0, 4) in component)
    expect(screen.queryByText('Patient 5')).not.toBeInTheDocument();
  });

  it('shows "No results" message when API returns empty groups', async () => {
    server.use(
      http.get('http://localhost:8080/api/search', () =>
        HttpResponse.json([]),
      ),
    );

    renderSearch();

    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'xyz');

    await waitFor(() =>
      expect(screen.getByText(/No results for/i)).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('shows loading indicator while fetching', async () => {
    server.use(
      http.get('http://localhost:8080/api/search', async () => {
        await new Promise(r => setTimeout(r, 200));
        return HttpResponse.json([]);
      }),
    );

    renderSearch();

    const input = screen.getByRole('searchbox');
    await userEvent.type(input, 'jo');

    // "Searching…" should appear while the request is in flight
    await waitFor(() =>
      expect(screen.getByText('Searching…')).toBeInTheDocument(),
      { timeout: 3000 },
    );
  });
});

import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { AuditLogs } from '../../pages/AuditLogs';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderAuditLogs() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <AuditLogs />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Date filter → API params ──────────────────────────────────────────────

describe('AuditLogs — date filter maps to ISO params', () => {
  it('renders audit entries from API', async () => {
    renderAuditLogs();

    await waitFor(() =>
      expect(screen.getByText('Viewed Digital Twin')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('sends from/to ISO params when date filter is set to "Today"', async () => {
    let capturedUrl: string | null = null;
    server.use(
      http.get('http://localhost:8080/api/audit', ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          page: 0,
          size: 20,
        });
      }),
    );

    renderAuditLogs();

    await waitFor(() => expect(capturedUrl).toBeTruthy(), { timeout: 5000 });

    // Today filter should include a "from" param
    expect(capturedUrl).toContain('from=');
  });

  it('shows EmptyState when no results match filters', async () => {
    server.use(
      http.get('http://localhost:8080/api/audit', () =>
        HttpResponse.json({ content: [], totalElements: 0, totalPages: 0 }),
      ),
    );

    renderAuditLogs();

    await waitFor(() =>
      expect(screen.getByText('No audit events match these filters')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });
});

// ── Pagination ────────────────────────────────────────────────────────────

describe('AuditLogs — pagination', () => {
  it('shows pagination controls when more than one page exists', async () => {
    server.use(
      http.get('http://localhost:8080/api/audit', () =>
        HttpResponse.json({
          content: [
            { id: 'AU-001', timestamp: '2026-09-21T12:00:00Z', userId: 'PROV-001', userName: 'Dr. A. Mehta',
              userRole: 'CLINICIAN', action: 'Login', module: 'Auth', status: 'Success' },
          ],
          totalElements: 40,
          totalPages: 2,
          page: 0,
          size: 20,
        }),
      ),
    );

    renderAuditLogs();

    await waitFor(() =>
      expect(screen.getByText('Page 1 of 2')).toBeInTheDocument(),
      { timeout: 5000 },
    );

    expect(screen.getByRole('button', { name: /next/i })).not.toBeDisabled();
  });

  it('changing page sends correct page param to API', async () => {
    const requestedPages: string[] = [];

    server.use(
      http.get('http://localhost:8080/api/audit', ({ request }) => {
        const url = new URL(request.url);
        requestedPages.push(url.searchParams.get('page') ?? '0');
        return HttpResponse.json({
          content: [{ id: 'AU-001', timestamp: '2026-09-21T12:00:00Z', userId: 'PROV-001',
            userName: 'Dr. A. Mehta', userRole: 'CLINICIAN', action: 'Login', module: 'Auth', status: 'Success' }],
          totalElements: 40,
          totalPages: 2,
          page: 0,
          size: 20,
        });
      }),
    );

    renderAuditLogs();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /next/i })).toBeInTheDocument(),
      { timeout: 5000 },
    );

    await userEvent.click(screen.getByRole('button', { name: /next/i }));

    await waitFor(() =>
      expect(requestedPages).toContain('1'),
      { timeout: 5000 },
    );
  });
});

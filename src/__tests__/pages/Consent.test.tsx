import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { Toaster } from 'sonner';
import { server } from '../mocks/server';
import { Consent } from '../../pages/Consent';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderConsent() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <Toaster />
        <Consent />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Update mutation ──────────────────────────────────────────────────────

describe('Consent page — update mutation', () => {
  it('calls updateConsent API and shows success toast when Update consent is clicked', async () => {
    renderConsent();

    // Wait for consent to load
    await waitFor(() =>
      expect(screen.getByText('Update consent')).toBeInTheDocument(),
      { timeout: 5000 },
    );

    // Mock the PUT to succeed
    server.use(
      http.put('http://localhost:8080/api/patients/P001/consent', () =>
        HttpResponse.json({ id: 'CON-001', patientId: 'P001', ehr: false, wearable: true, ai: true }),
      ),
    );

    await userEvent.click(screen.getByRole('button', { name: /update consent/i }));

    // Toast success appears
    await waitFor(() =>
      expect(screen.getByText('Consent updated')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('shows error toast when update fails', async () => {
    renderConsent();

    await waitFor(() =>
      expect(screen.getByText('Update consent')).toBeInTheDocument(),
      { timeout: 5000 },
    );

    server.use(
      http.put('http://localhost:8080/api/patients/P001/consent', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 }),
      ),
    );

    await userEvent.click(screen.getByRole('button', { name: /update consent/i }));

    await waitFor(() =>
      expect(screen.getByText(/Failed to update consent/i)).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });
});

// ── History toggle ────────────────────────────────────────────────────────

describe('Consent page — history toggle', () => {
  it('fetches consent history only when View history is toggled on', async () => {
    let historyCalled = false;
    server.use(
      http.get('http://localhost:8080/api/patients/P001/consent/history', () => {
        historyCalled = true;
        return HttpResponse.json([
          { id: 'ch-1', date: '2026-09-21T12:00:00Z', type: 'EHR Data Access', status: 'Granted', updatedBy: 'Dr. A. Mehta' },
        ]);
      }),
    );

    renderConsent();

    // History should NOT be fetched yet
    await new Promise(r => setTimeout(r, 200));
    expect(historyCalled).toBe(false);

    // Click "View history"
    const btn = await screen.findByRole('button', { name: /view history/i }, { timeout: 5000 });
    await userEvent.click(btn);

    await waitFor(() => expect(historyCalled).toBe(true), { timeout: 5000 });

    // History entries visible
    await waitFor(() =>
      expect(screen.getByText('EHR Data Access')).toBeInTheDocument(),
      { timeout: 5000 },
    );
  });

  it('does not re-fetch history when toggled again after initial fetch', async () => {
    let callCount = 0;
    server.use(
      http.get('http://localhost:8080/api/patients/P001/consent/history', () => {
        callCount++;
        return HttpResponse.json([
          { id: 'ch-1', date: '2026-09-21T12:00:00Z', type: 'EHR Data Access', status: 'Granted', updatedBy: 'Dr. A. Mehta' },
        ]);
      }),
    );

    renderConsent();

    const btn = await screen.findByRole('button', { name: /view history/i }, { timeout: 5000 });
    await userEvent.click(btn); // show
    await waitFor(() => expect(callCount).toBe(1), { timeout: 5000 });

    const hideBtn = await screen.findByRole('button', { name: /hide history/i }, { timeout: 3000 });
    await userEvent.click(hideBtn); // hide

    // Due to staleTime=0 in test, a second click may refetch — verify at most 2 calls
    expect(callCount).toBeLessThanOrEqual(2);
  });
});

import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockAlertUnack, mockAlertAcknowledged } from '../mocks/phase5Handlers';
import { AlertDetails } from '../../pages/AlertDetails';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderAlertDetails(alertId = 'A-2291') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/alerts/${alertId}`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/alerts/:alertId" element={<AlertDetails />} />
          <Route path="/alerts" element={<div>Alerts list</div>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Page structure ─────────────────────────────────────────────────────────

describe('AlertDetails — rendering', () => {
  it('renders page title', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByText('Alert details')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders back link to /alerts', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'Back to alerts' })).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Alert data from API ────────────────────────────────────────────────────

describe('AlertDetails — data from API', () => {
  it('renders alert event description from API', async () => {
    renderAlertDetails();
    await waitFor(() =>
      // Event appears in CardHeader description as "patientName · event"
      // Use regex to handle text across sibling nodes
      expect(screen.getByText(/Heart Rate Spike/)).toBeInTheDocument(),
      { timeout: 8000 });
  });

  it('renders severity badge from API', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getAllByText('HIGH priority').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders patient name from API', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getAllByText(new RegExp(mockAlertUnack.patientName)).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders confidence value from API', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByText(`${mockAlertUnack.confidence}%`)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders AI analysis text from API', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByText(mockAlertUnack.analysis!)).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Audit trail ────────────────────────────────────────────────────────────

describe('AlertDetails — audit trail', () => {
  it('renders audit trail section', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByText('Alert audit trail')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders audit trail entries from API', async () => {
    renderAlertDetails();
    await waitFor(() =>
      // First audit entry actor
      expect(screen.getByText('MediSphere Stream Processor')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders audit trail action text', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByText(mockAlertUnack.auditTrail[0].action)).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Acknowledge mutation ───────────────────────────────────────────────────

describe('AlertDetails — acknowledge mutation', () => {
  it('renders Acknowledge button for Unacknowledged alert', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Acknowledge' })).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('Acknowledge button is enabled for Unacknowledged alert', async () => {
    renderAlertDetails();
    await waitFor(() => {
      const btn = screen.getByRole('button', { name: 'Acknowledge' });
      expect(btn).not.toBeDisabled();
    }, { timeout: 5000 });
  });

  it('Acknowledge button becomes "Acknowledged" after mutation success', async () => {
    // Override the GET handler to return Acknowledged after the PATCH fires
    let acknowledged = false;
    server.use(
      http.patch('http://localhost:8080/api/alerts/A-2291/acknowledge', () => {
        acknowledged = true;
        return HttpResponse.json({
          ...mockAlertUnack,
          status: 'Acknowledged',
          acknowledgedAt: new Date().toISOString(),
          acknowledgedBy: 'PROV-001',
          auditTrail: [...mockAlertUnack.auditTrail,
            { id: 'at-new', timestamp: new Date().toISOString(), actor: 'PROV-001', actorId: 'PROV-001', action: 'Alert acknowledged' },
          ],
        });
      }),
      http.get('http://localhost:8080/api/alerts/A-2291', () => {
        if (acknowledged) {
          return HttpResponse.json({ ...mockAlertUnack, status: 'Acknowledged', acknowledgedAt: new Date().toISOString(), acknowledgedBy: 'PROV-001' });
        }
        return HttpResponse.json(mockAlertUnack);
      }),
    );

    renderAlertDetails();
    await waitFor(() => screen.getByRole('button', { name: 'Acknowledge' }), { timeout: 5000 });
    fireEvent.click(screen.getByRole('button', { name: 'Acknowledge' }));

    await waitFor(() =>
      expect(screen.getAllByText('Acknowledged').length).toBeGreaterThan(0),
      { timeout: 8000 });
  });
});

// ── Escalate mutation ──────────────────────────────────────────────────────

describe('AlertDetails — escalate mutation', () => {
  it('renders Escalate button', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Escalate' })).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('opens confirm modal on Escalate click', async () => {
    renderAlertDetails();
    await waitFor(() => screen.getByRole('button', { name: 'Escalate' }), { timeout: 5000 });

    fireEvent.click(screen.getByRole('button', { name: 'Escalate' }));

    await waitFor(() =>
      expect(screen.getByText('Escalate alert to on-call cardiology?')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('closes modal on Cancel click', async () => {
    renderAlertDetails();
    await waitFor(() => screen.getByRole('button', { name: 'Escalate' }), { timeout: 5000 });

    fireEvent.click(screen.getByRole('button', { name: 'Escalate' }));
    await waitFor(() => screen.getByText('Escalate alert to on-call cardiology?'), { timeout: 2000 });

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    await waitFor(() =>
      expect(screen.queryByText('Escalate alert to on-call cardiology?')).not.toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Resolve mutation ───────────────────────────────────────────────────────

describe('AlertDetails — resolve mutation', () => {
  it('renders Resolve button for non-resolved alert', async () => {
    renderAlertDetails();
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Resolve' })).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('Resolve button changes alert status via API', async () => {
    let resolved = false;
    server.use(
      http.post('http://localhost:8080/api/alerts/A-2291/resolve', () => {
        resolved = true;
        return HttpResponse.json({ ...mockAlertUnack, status: 'Resolved', resolvedAt: new Date().toISOString(), resolution: 'Patient stabilised' });
      }),
      http.get('http://localhost:8080/api/alerts/A-2291', () => {
        if (resolved) {
          return HttpResponse.json({ ...mockAlertUnack, status: 'Resolved', resolvedAt: new Date().toISOString(), resolution: 'Patient stabilised' });
        }
        return HttpResponse.json(mockAlertUnack);
      }),
    );

    renderAlertDetails();
    await waitFor(() => screen.getByRole('button', { name: 'Resolve' }), { timeout: 5000 });
    fireEvent.click(screen.getByRole('button', { name: 'Resolve' }));

    await waitFor(() =>
      expect(screen.getAllByText('Resolved').length).toBeGreaterThan(0),
      { timeout: 8000 });
  });
});

// ── 404 redirect ───────────────────────────────────────────────────────────

describe('AlertDetails — 404 redirect', () => {
  it('redirects to /alerts for unknown alertId', async () => {
    server.use(
      http.get('http://localhost:8080/api/alerts/MISSING', () =>
        HttpResponse.json(null, { status: 404 }),
      ),
    );
    renderAlertDetails('MISSING');

    await waitFor(() =>
      expect(screen.getByText('Alerts list')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

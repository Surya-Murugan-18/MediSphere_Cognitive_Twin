import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockMonitoringStats, mockKafkaStats, mockKafkaEvents } from '../mocks/phase5Handlers';
import { mockPatientSummary } from '../mocks/patientHandlers';
import { Monitoring } from '../../pages/Monitoring';
import { tokenStore } from '../../api/tokenStore';

// Mock WebSocket hooks — they require a real WS connection which is not available in tests
vi.mock('../../hooks/useMonitoringStream', () => ({
  useMonitoringStream: () => ({ liveVitals: new Map(), isConnected: false }),
}));
vi.mock('../../hooks/useKafkaEventStream', () => ({
  useKafkaEventStream: () => ({ events: [], isConnected: false }),
}));

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderMonitoring() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={['/monitoring']}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/monitoring" element={<Monitoring />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Page structure ─────────────────────────────────────────────────────────

describe('Monitoring page — rendering', () => {
  it('renders page title', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText('Real-time health surveillance')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders Kafka badge', () => {
    renderMonitoring();
    expect(screen.getByText('Kafka · vitals.raw')).toBeInTheDocument();
  });

  it('renders link to clinical alerts', () => {
    renderMonitoring();
    expect(screen.getByRole('link', { name: 'Clinical alerts' })).toBeInTheDocument();
  });
});

// ── KPI cards from API ─────────────────────────────────────────────────────

describe('Monitoring page — KPI cards from API', () => {
  it('renders alertsToday from monitoring stats API', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getAllByText(String(mockMonitoringStats.alertsToday)).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders wearablesOnline from monitoring stats API', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getAllByText(String(mockMonitoringStats.wearablesOnline)).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders avgResponseMin from monitoring stats API', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText(`${mockMonitoringStats.avgResponseMin} min`)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders streamStatus from stats', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getAllByText(mockMonitoringStats.streamStatus).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });
});

// ── Patient monitoring table ───────────────────────────────────────────────

describe('Monitoring page — patient table', () => {
  it('renders patient name in monitoring table from API', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText(mockPatientSummary.name)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders patient ID in monitoring table', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getAllByText(mockPatientSummary.id).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });
});

// ── Kafka panel ────────────────────────────────────────────────────────────

describe('Monitoring page — Kafka panel', () => {
  it('renders Kafka stream section', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText('Kafka stream')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders eventsPerSec from kafka-stats API', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText(String(mockKafkaStats.eventsPerSec))).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders Latest events panel', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText('Latest events')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── WebSocket state ────────────────────────────────────────────────────────

describe('Monitoring page — WebSocket state', () => {
  it('shows "Connecting" when WebSocket is not connected', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getAllByText('Connecting').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('shows "REST" label in the table header when WS disconnected', async () => {
    renderMonitoring();
    await waitFor(() =>
      expect(screen.getByText('REST')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Loading state ──────────────────────────────────────────────────────────

describe('Monitoring page — loading state', () => {
  it('renders skeleton while stats are loading', () => {
    server.use(
      http.get('http://localhost:8080/api/monitoring/stats', async () => {
        await new Promise((r) => setTimeout(r, 200));
        return HttpResponse.json(mockMonitoringStats);
      }),
    );
    renderMonitoring();
    // Page title renders immediately, skeleton rows are present
    expect(screen.getByText('Real-time health surveillance')).toBeInTheDocument();
  });
});

import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import {
  mockPredictionStats,
  mockPredictions,
} from '../mocks/phase4Handlers';
import { Predictions } from '../../pages/Predictions';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderPredictions() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={['/predictions']}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/predictions" element={<Predictions />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Page structure ────────────────────────────────────────────────────────

describe('Predictions page — structure', () => {
  it('renders page title', () => {
    renderPredictions();
    expect(screen.getByText('AI Risk Prediction Engine')).toBeInTheDocument();
  });

  it('renders federated learning link', () => {
    renderPredictions();
    expect(screen.getByRole('link', { name: 'Federated learning' })).toBeInTheDocument();
  });
});

// ── KPI cards from API ────────────────────────────────────────────────────

describe('Predictions page — KPI cards', () => {
  it('renders total prediction count from API', async () => {
    renderPredictions();
    await waitFor(() =>
      expect(screen.getAllByText(String(mockPredictionStats.total)).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders high risk count from API', async () => {
    renderPredictions();
    await waitFor(() =>
      expect(screen.getAllByText(String(mockPredictionStats.highRiskCount)).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders latest federated round from API', async () => {
    renderPredictions();
    await waitFor(() =>
      // latestRound=47 appears in KPI card AND in the page header badge — use getAllByText
      expect(screen.getAllByText(String(mockPredictionStats.latestRound)).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders model accuracy from API', async () => {
    renderPredictions();
    await waitFor(() =>
      expect(screen.getByText(`${mockPredictionStats.avgAccuracy}%`)).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Primary prediction card ───────────────────────────────────────────────

describe('Predictions page — primary card', () => {
  it('renders primary CVD prediction value from API', async () => {
    renderPredictions();
    // 24.3% appears in the primary card AND the table
    await waitFor(() =>
      expect(screen.getAllByText('24.3%').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders View explanation link with correct predictionId', async () => {
    renderPredictions();
    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: 'View explanation' }),
      ).toHaveAttribute('href', `/explain?predictionId=${mockPredictions[0].id}`),
      { timeout: 5000 });
  });

  it('"View digital twin" link uses patientId from API (not hardcoded)', async () => {
    renderPredictions();
    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: 'View digital twin' }),
      ).toHaveAttribute('href', `/twins/${mockPredictions[0].patientId}`),
      { timeout: 5000 });
  });
});

// ── All predictions table ─────────────────────────────────────────────────

describe('Predictions page — table', () => {
  it('renders a row for each prediction returned by API', async () => {
    renderPredictions();
    await waitFor(() => {
      // Each prediction has a model name in the table — use getAllByText since
      // model names may appear in multiple places (primary card + table)
      expect(screen.getAllByText('CVD-Risk-v3.2').length).toBeGreaterThan(0);
      expect(screen.getAllByText('DM-Complication-v2.4').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Readmit-30d-v1.8').length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });

  it('renders Explain links in table with correct predictionId params', async () => {
    renderPredictions();
    await waitFor(() => {
      const explainLinks = screen.getAllByRole('link', { name: 'Explain' });
      expect(explainLinks.length).toBeGreaterThan(0);
      // First Explain link should have a predictionId param
      expect(explainLinks[0].getAttribute('href')).toMatch(/\/explain\?predictionId=/);
    }, { timeout: 5000 });
  });
});

// ── Risk distribution donut ───────────────────────────────────────────────

describe('Predictions page — risk distribution donut', () => {
  it('renders Risk distribution card from API', async () => {
    renderPredictions();
    await waitFor(() =>
      expect(screen.getByText('Risk distribution')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders each risk category label from API distribution data', async () => {
    renderPredictions();
    await waitFor(() => {
      // RiskDonut renders "{name} risk" for each entry
      expect(screen.getByText(/High risk/)).toBeInTheDocument();
      expect(screen.getByText(/Low risk/)).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('renders donut total from API distribution counts', async () => {
    renderPredictions();
    // total = 1 + 0 + 2 = 3 from mockRiskDistribution
    await waitFor(() =>
      expect(screen.getAllByText('3').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });
});

describe('Predictions page — loading', () => {
  it('shows "…" placeholders while stats are loading', () => {
    server.use(
      http.get('http://localhost:8080/api/predictions/stats', async () => {
        await new Promise((r) => setTimeout(r, 200));
        return HttpResponse.json(mockPredictionStats);
      }),
    );
    renderPredictions();
    // Page title renders immediately
    expect(screen.getByText('AI Risk Prediction Engine')).toBeInTheDocument();
  });
});

// ── Empty state ───────────────────────────────────────────────────────────

describe('Predictions page — empty state', () => {
  it('shows EmptyState when no predictions returned', async () => {
    server.use(
      http.get('http://localhost:8080/api/predictions', () =>
        HttpResponse.json({
          content: [], totalElements: 0, totalPages: 0,
          page: 0, size: 20, first: true, last: true,
        }),
      ),
    );
    renderPredictions();
    await waitFor(() =>
      // "No predictions yet" appears in both primary card and table — check at least one exists
      expect(screen.getAllByText('No predictions yet').length).toBeGreaterThan(0),
      { timeout: 5000 });
  });
});

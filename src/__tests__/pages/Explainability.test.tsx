import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockExplanation } from '../mocks/phase4Handlers';
import { Explainability } from '../../pages/Explainability';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderExplainability(search = '') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/explain${search}`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/explain" element={<Explainability />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Empty state — no predictionId ────────────────────────────────────────

describe('Explainability page — no predictionId', () => {
  it('shows EmptyState when ?predictionId= param is absent', () => {
    renderExplainability();
    expect(screen.getByText('No prediction selected')).toBeInTheDocument();
  });

  it('shows link to Predictions page when no predictionId', () => {
    renderExplainability();
    expect(screen.getByRole('link', { name: 'Go to predictions' })).toBeInTheDocument();
  });

  it('does NOT call the API when predictionId is absent', () => {
    let apiCalled = false;
    server.use(
      http.get('http://localhost:8080/api/explainability/:id', () => {
        apiCalled = true;
        return HttpResponse.json(mockExplanation);
      }),
    );
    renderExplainability();
    expect(apiCalled).toBe(false);
  });
});

// ── With predictionId — loads from API ───────────────────────────────────

describe('Explainability page — with predictionId', () => {
  it('renders page with predictionId in subtitle', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(screen.getByText(/PR-CVD001/)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders prediction value from API response', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(screen.getByText(`${mockExplanation.value}%`)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders model name from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(screen.getByText(mockExplanation.model)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders patient name from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(screen.getByText(mockExplanation.patientName)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders federated round badge from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(
        screen.getByText(`Federated round ${mockExplanation.federatedRound}`),
      ).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders SHAP feature names from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() => {
      expect(screen.getByText('HbA1c')).toBeInTheDocument();
      expect(screen.getByText('Blood Pressure')).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('renders SHAP contribution values from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      // contribution 8.0 renders as "+8%"
      expect(screen.getByText('+8%')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders clinical evidence section from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(screen.getByText('ACC/AHA CVD Risk Calculator')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders natural-language summary from API', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      // naturalLanguageSummary contains "24.3%" — multiple elements may match
      expect(screen.getAllByText(/24\.3%/).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders "Create care plan" link with patientId from API (not hardcoded)', async () => {
    renderExplainability('?predictionId=PR-CVD001');
    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: 'Create care plan' }),
      ).toHaveAttribute('href', `/care-plans/new?patientId=${mockExplanation.patientId}`),
      { timeout: 5000 });
  });
});

// ── Error / Not found ─────────────────────────────────────────────────────

describe('Explainability page — not found', () => {
  it('shows explanation not found message for unknown predictionId', async () => {
    renderExplainability('?predictionId=PR-UNKNOWN-999');
    await waitFor(() =>
      expect(screen.getByText('Explanation not found')).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('shows link to predictions when explanation not found', async () => {
    renderExplainability('?predictionId=PR-UNKNOWN-999');
    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: 'Back to predictions' }),
      ).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Loading state ─────────────────────────────────────────────────────────

describe('Explainability page — loading', () => {
  it('renders loading page header while fetching', () => {
    server.use(
      http.get('http://localhost:8080/api/explainability/:id', async () => {
        await new Promise((r) => setTimeout(r, 200));
        return HttpResponse.json(mockExplanation);
      }),
    );
    renderExplainability('?predictionId=PR-CVD001');
    expect(screen.getByText('Prediction explainability')).toBeInTheDocument();
  });
});

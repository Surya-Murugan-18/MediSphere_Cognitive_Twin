import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import {
  mockFederatedNodes,
  mockCurrentRound,
  mockFederatedRoundsPage,
} from '../mocks/phase4Handlers';
import { FederatedLearning } from '../../pages/FederatedLearning';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderFederated() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={['/federated']}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/federated" element={<FederatedLearning />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

// ── Page structure ────────────────────────────────────────────────────────

describe('FederatedLearning page — structure', () => {
  it('renders page title', () => {
    renderFederated();
    expect(screen.getByText('Federated learning')).toBeInTheDocument();
  });

  it('renders privacy notice', () => {
    renderFederated();
    expect(screen.getByText('Privacy-preserving by design')).toBeInTheDocument();
  });

  it('renders pipeline steps', () => {
    renderFederated();
    expect(screen.getByText('Local Training')).toBeInTheDocument();
    expect(screen.getByText('Federated Aggregation')).toBeInTheDocument();
    expect(screen.getByText('Global Model')).toBeInTheDocument();
  });
});

// ── Nodes from API ────────────────────────────────────────────────────────

describe('FederatedLearning page — hospital nodes', () => {
  it('renders hospital node names from API', async () => {
    renderFederated();
    await waitFor(() => {
      // Names appear split by "—"; check for first part of each hospital
      expect(screen.getByText('Hospital A')).toBeInTheDocument();
      expect(screen.getByText('Hospital B')).toBeInTheDocument();
      expect(screen.getByText('Hospital C')).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('renders correct patient count for HOSP-A from API', async () => {
    renderFederated();
    await waitFor(() =>
      expect(screen.getByText(String(mockFederatedNodes[0].patients))).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders all 3 nodes from API', async () => {
    renderFederated();
    await waitFor(() => {
      mockFederatedNodes.forEach((node) => {
        // Contribution % appears in progress bar label area
        expect(screen.getByText(`${node.contribution}%`)).toBeInTheDocument();
      });
    }, { timeout: 5000 });
  });
});

// ── Current round from API ────────────────────────────────────────────────

describe('FederatedLearning page — current round', () => {
  it('renders current round number from API', async () => {
    renderFederated();
    await waitFor(() =>
      // Round 47 appears in "Current round" section header badge and in the table
      expect(screen.getAllByText(`${mockCurrentRound.round}`).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders model name from current round API', async () => {
    renderFederated();
    await waitFor(() =>
      expect(screen.getAllByText(mockCurrentRound.model).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders global accuracy from current round API', async () => {
    renderFederated();
    await waitFor(() =>
      // 91.4% appears in current round card AND round history table — getAllByText
      expect(screen.getAllByText(`${mockCurrentRound.accuracy}%`).length).toBeGreaterThan(0),
      { timeout: 5000 });
  });

  it('renders Completed status badge from API', async () => {
    renderFederated();
    await waitFor(() => {
      const badges = screen.getAllByText(mockCurrentRound.status);
      expect(badges.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });
});

// ── Round history from API ────────────────────────────────────────────────

describe('FederatedLearning page — round history', () => {
  it('renders a row for each round in history', async () => {
    renderFederated();
    const expectedRounds = mockFederatedRoundsPage.content;
    await waitFor(() => {
      expectedRounds.forEach((round) => {
        expect(screen.getAllByText(String(round.round)).length).toBeGreaterThan(0);
      });
    }, { timeout: 5000 });
  });

  it('renders accuracy for each round in history', async () => {
    renderFederated();
    await waitFor(() => {
      // Round 43 accuracy 89.1%
      expect(screen.getByText('89.1%')).toBeInTheDocument();
      // Round 47 accuracy 91.4%
      expect(screen.getAllByText('91.4%').length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });
});

// ── Accuracy trend chart ──────────────────────────────────────────────────

describe('FederatedLearning page — accuracy trend chart', () => {
  it('renders accuracy trend section', async () => {
    renderFederated();
    await waitFor(() =>
      expect(screen.getByText('Accuracy trend')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

// ── Loading state ─────────────────────────────────────────────────────────

describe('FederatedLearning page — loading', () => {
  it('renders page title immediately while loading', () => {
    server.use(
      http.get('http://localhost:8080/api/federated/nodes', async () => {
        await new Promise((r) => setTimeout(r, 200));
        return HttpResponse.json(mockFederatedNodes);
      }),
    );
    renderFederated();
    expect(screen.getByText('Federated learning')).toBeInTheDocument();
  });
});

// ── Error resilience ──────────────────────────────────────────────────────

describe('FederatedLearning page — error resilience', () => {
  it('still renders page structure when nodes API fails', async () => {
    server.use(
      http.get('http://localhost:8080/api/federated/nodes', () =>
        HttpResponse.json({ message: 'Error' }, { status: 500 })),
    );
    renderFederated();
    await waitFor(() =>
      expect(screen.getByText('Federated learning')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

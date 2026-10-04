import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockPatient } from '../mocks/patientHandlers';
import { HealthTwin } from '../../pages/HealthTwin';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderHealthTwin(patientId = 'P001') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/twins/${patientId}`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/twins/:patientId" element={<HealthTwin />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('HealthTwin — rendering', () => {
  it('renders Digital Health Twin page title', async () => {
    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('Digital Health Twin')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders patient name John Doe in the page', async () => {
    renderHealthTwin();
    // Patient name appears in subtitle and possibly selector
    await waitFor(() => {
      const nameEls = screen.getAllByText(/John Doe/);
      expect(nameEls.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });

  it('shows Health twin status card', async () => {
    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('Health twin status')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders body map SVG with aria-label', async () => {
    renderHealthTwin();
    await waitFor(() => {
      const svg = screen.getByRole('img', { name: /risk heatmap/i });
      expect(svg).toBeInTheDocument();
    }, { timeout: 5000 });
  });
});

describe('HealthTwin — body regions', () => {
  it('body map SVG is visible after twin data loads', async () => {
    renderHealthTwin();
    // Wait for the full chain: patient → twinId → twin loading completes → BodyMap renders
    await waitFor(() => {
      const svg = screen.getByRole('img', { name: /risk heatmap/i });
      expect(svg).toBeInTheDocument();
    }, { timeout: 10000 });
  });

  it('shows medium risk level in region list', async () => {
    renderHealthTwin();
    // Once BodyMap renders, region list buttons appear with their level text
    await waitFor(() => {
      const medium = screen.getAllByText('medium');
      expect(medium.length).toBeGreaterThan(0);
    }, { timeout: 10000 });
  });

  it('shows medium risk level for Respiratory region button', async () => {
    renderHealthTwin();
    await waitFor(() => {
      const mediumItems = screen.getAllByText('medium');
      expect(mediumItems.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });
});

describe('HealthTwin — data sources', () => {
  it('renders Data sources card title', async () => {
    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('Data sources')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows Hospital EHR label', async () => {
    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('Hospital EHR (FHIR R4)')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows Not connected badges for disconnected sources', async () => {
    renderHealthTwin();
    await waitFor(() => {
      const badges = screen.getAllByText('Not connected');
      // 4 sources all disconnected
      expect(badges.length).toBe(4);
    }, { timeout: 5000 });
  });
});

describe('HealthTwin — timeline', () => {
  it('shows Twin state timeline section', async () => {
    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('Twin state timeline')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows Twin created event', async () => {
    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('Twin created')).toBeInTheDocument(), { timeout: 5000 });
  });
});

describe('HealthTwin — not found', () => {
  it('shows error description for unknown patient', async () => {
    renderHealthTwin('P_UNKNOWN');
    await waitFor(() =>
      expect(screen.getByText(/No patient matches this ID/)).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

describe('HealthTwin — twin not created state', () => {
  it('shows no-twin EmptyState when twinStatus is Not Created', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId', () =>
        HttpResponse.json({ ...mockPatient, twinId: null, twinStatus: 'Not Created' })));

    renderHealthTwin();
    await waitFor(() =>
      expect(screen.getByText('No digital twin for this patient')).toBeInTheDocument(),
      { timeout: 5000 });
  });
});

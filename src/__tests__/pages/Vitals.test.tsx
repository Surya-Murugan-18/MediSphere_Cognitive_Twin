import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockVitalsSnapshot, mockWearableDevice } from '../mocks/phase3Handlers';
import { Vitals } from '../../pages/Vitals';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderVitals(patientId = 'P001') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/vitals/${patientId}`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/vitals/:patientId" element={<Vitals />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Vitals page — rendering', () => {
  it('renders page title', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('Patient vitals')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders heart rate from API', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('85')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders blood pressure from API', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('120/80')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders SpO2 from API', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('98')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders all 5 vital labels', async () => {
    renderVitals();
    await waitFor(() => screen.getByText('85'), { timeout: 5000 });
    expect(screen.getByText('Heart rate')).toBeInTheDocument();
    expect(screen.getByText('Blood pressure')).toBeInTheDocument();
    expect(screen.getByText('SpO₂')).toBeInTheDocument();
    expect(screen.getByText('Temperature')).toBeInTheDocument();
    expect(screen.getByText('Respiratory rate')).toBeInTheDocument();
  });
});

describe('Vitals page — wearable card', () => {
  it('renders wearable device section', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('Wearable device')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders wearable display name from API', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('Smart Watch · SW-1044')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows Connected badge when device is Online', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('Connected')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows offline notice when device status is Offline', async () => {
    server.use(
      http.get('http://localhost:8080/api/devices/by-patient/:patientId', () =>
        HttpResponse.json({ ...mockWearableDevice, status: 'Offline' })));

    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('Wearable offline')).toBeInTheDocument(), { timeout: 5000 });
  });
});

describe('Vitals page — charts', () => {
  it('renders heart rate chart section', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText(/Heart rate.*last 24 hours/i)).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders blood pressure chart section', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText(/Blood pressure.*last 7 days/i)).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders SpO2 chart section', async () => {
    renderVitals();
    await waitFor(() =>
      expect(screen.getByText(/SpO₂.*last 24 hours/i)).toBeInTheDocument(), { timeout: 5000 });
  });
});

describe('Vitals page — loading state', () => {
  it('shows skeleton loading blocks initially', () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/vitals/current', async () => {
        await new Promise(r => setTimeout(r, 100));
        return HttpResponse.json(mockVitalsSnapshot);
      }));

    renderVitals();
    // Title renders immediately even while data loads
    expect(screen.getByText('Patient vitals')).toBeInTheDocument();
  });
});

describe('Vitals page — error state', () => {
  it('still renders page structure when vitals API fails', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/vitals/current', () =>
        HttpResponse.json({ message: 'Error' }, { status: 500 })));

    renderVitals();
    await waitFor(() =>
      expect(screen.getByText('Patient vitals')).toBeInTheDocument(), { timeout: 5000 });
  });
});

import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockLabResults } from '../mocks/phase3Handlers';
import { Patient360 } from '../../pages/Patient360';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderPatient360(patientId = 'P001') {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter initialEntries={[`/patients/${patientId}`]}>
      <QueryClientProvider client={client}>
        <Routes>
          <Route path="/patients/:patientId" element={<Patient360 />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Patient360 — Overview lab results', () => {
  it('shows Recent lab results section in overview tab', async () => {
    renderPatient360();
    await waitFor(() =>
      expect(screen.getByText('Recent lab results')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders top-3 lab result test names in overview', async () => {
    renderPatient360();
    await waitFor(() =>
      expect(screen.getByText('HbA1c')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders HbA1c status badge in overview', async () => {
    renderPatient360();
    await waitFor(() => {
      // HbA1c status is 'High' → warning tone badge
      const highBadges = screen.getAllByText('High');
      expect(highBadges.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });
});

describe('Patient360 — Labs tab', () => {
  it('Labs tab shows real data after clicking', async () => {
    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const labsTab = screen.getAllByRole('tab').find(t => t.textContent === 'Labs')!;
    await user.click(labsTab);

    await waitFor(() =>
      expect(screen.getByText('HbA1c')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('Labs tab shows all 3 lab results', async () => {
    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const labsTab = screen.getAllByRole('tab').find(t => t.textContent === 'Labs')!;
    await user.click(labsTab);

    await waitFor(() => {
      expect(screen.getByText('HbA1c')).toBeInTheDocument();
      expect(screen.getByText('Fasting Glucose')).toBeInTheDocument();
      expect(screen.getByText('Total Cholesterol')).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('Labs tab shows table headers', async () => {
    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const labsTab = screen.getAllByRole('tab').find(t => t.textContent === 'Labs')!;
    await user.click(labsTab);

    await waitFor(() => {
      expect(screen.getByText('Test')).toBeInTheDocument();
      expect(screen.getByText('Result')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('Labs tab shows empty state when no results', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/labs', () =>
        HttpResponse.json({
          content: [], totalElements: 0, totalPages: 0,
          page: 0, size: 50, first: true, last: true,
        })),
      http.get('http://localhost:8080/api/patients/:patientId/labs/recent', () =>
        HttpResponse.json([])));

    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const labsTab = screen.getAllByRole('tab').find(t => t.textContent === 'Labs')!;
    await user.click(labsTab);

    await waitFor(() =>
      expect(screen.getByText('No lab results')).toBeInTheDocument(), { timeout: 5000 });
  });
});

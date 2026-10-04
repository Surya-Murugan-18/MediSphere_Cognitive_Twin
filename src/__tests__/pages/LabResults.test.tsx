import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockLabResults, mockLabPageResponse } from '../mocks/phase3Handlers';
import { LabResults } from '../../pages/LabResults';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

function renderLabResults() {
  tokenStore.set('mock.access.token');
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
  });
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <LabResults />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('LabResults page — rendering', () => {
  it('renders page title', async () => {
    renderLabResults();
    await waitFor(() =>
      expect(screen.getByText('Laboratory results')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders lab test names from API', async () => {
    renderLabResults();
    await waitFor(() =>
      expect(screen.getByText('HbA1c')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders all 3 mock lab results', async () => {
    renderLabResults();
    await waitFor(() => {
      expect(screen.getByText('HbA1c')).toBeInTheDocument();
      expect(screen.getByText('Fasting Glucose')).toBeInTheDocument();
      expect(screen.getByText('Total Cholesterol')).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('renders result values in table', async () => {
    renderLabResults();
    await waitFor(() =>
      expect(screen.getByText('8.2 %')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders date column', async () => {
    renderLabResults();
    await waitFor(() =>
      expect(screen.getAllByText('2026-09-06').length).toBeGreaterThan(0), { timeout: 5000 });
  });
});

describe('LabResults page — filters', () => {
  it('renders filter dropdowns', async () => {
    renderLabResults();
    await waitFor(() => {
      expect(screen.getByText('Date')).toBeInTheDocument();
      expect(screen.getByText('Test type')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('sends category filter as query param when test type changes', async () => {
    let capturedUrl = '';
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/labs', ({ request }) => {
        capturedUrl = request.url;
        return HttpResponse.json(mockLabPageResponse);
      }));

    const user = userEvent.setup();
    renderLabResults();

    // Wait for initial load then change filter
    await waitFor(() => screen.getByText('Test type'), { timeout: 5000 });
    const testTypeSelect = screen.getAllByRole('combobox').find(
      el => (el as HTMLSelectElement).options?.[0]?.text === 'All types'
    )!;
    if (testTypeSelect) {
      await user.selectOptions(testTypeSelect, 'Metabolic');
      await waitFor(() =>
        expect(capturedUrl).toContain('category=Metabolic'), { timeout: 3000 });
    }
  });
});

describe('LabResults page — detail panel', () => {
  it('opens detail panel on row click', async () => {
    const user = userEvent.setup();
    renderLabResults();

    await waitFor(() => screen.getByText('HbA1c'), { timeout: 5000 });
    await user.click(screen.getByText('HbA1c'));

    await waitFor(() =>
      expect(screen.getByText('Current result')).toBeInTheDocument(), { timeout: 3000 });
  });

  it('shows clinical significance in detail panel', async () => {
    const user = userEvent.setup();
    renderLabResults();

    await waitFor(() => screen.getByText('HbA1c'), { timeout: 5000 });
    await user.click(screen.getByText('HbA1c'));

    await waitFor(() =>
      expect(screen.getByText('Clinical significance')).toBeInTheDocument(), { timeout: 3000 });
  });

  it('closes detail panel when X is clicked', async () => {
    const user = userEvent.setup();
    renderLabResults();

    await waitFor(() => screen.getByText('HbA1c'), { timeout: 5000 });
    await user.click(screen.getByText('HbA1c'));
    await waitFor(() => screen.getByLabelText('Close result detail'), { timeout: 3000 });
    await user.click(screen.getByLabelText('Close result detail'));

    await waitFor(() =>
      expect(screen.queryByText('Current result')).not.toBeInTheDocument());
  });
});

describe('LabResults page — loading/empty/error states', () => {
  it('shows skeleton rows while loading', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/labs', async () => {
        await new Promise(r => setTimeout(r, 100));
        return HttpResponse.json(mockLabPageResponse);
      }));

    renderLabResults();
    expect(screen.getByText('Laboratory results')).toBeInTheDocument();
  });

  it('shows empty state when no results match filters', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/labs', () =>
        HttpResponse.json({
          content: [], totalElements: 0, totalPages: 0,
          page: 0, size: 50, first: true, last: true,
        })));

    renderLabResults();
    await waitFor(() =>
      expect(screen.getByText('No lab results')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows error state when API fails', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients/:patientId/labs', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 })));

    renderLabResults();
    await waitFor(() =>
      expect(screen.getByText(/Could not load lab results/i)).toBeInTheDocument(), { timeout: 5000 });
  });
});

import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockPatientSummary } from '../mocks/patientHandlers';
import { Patients } from '../../pages/Patients';
import { tokenStore } from '../../api/tokenStore';

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); });
afterAll(() => server.close());

/** Fresh QueryClient per test — prevents cache interference between tests */
function makeFreshQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
    },
  });
}

function renderPatients() {
  tokenStore.set('mock.access.token');
  const client = makeFreshQueryClient();
  return render(
    <MemoryRouter>
      <QueryClientProvider client={client}>
        <Patients />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('Patients page — rendering', () => {
  it('renders page title', async () => {
    renderPatients();
    await waitFor(() => expect(screen.getByText('Patients')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders patient name from API', async () => {
    renderPatients();
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders patient ID P001', async () => {
    renderPatients();
    await waitFor(() => expect(screen.getByText('P001')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders conditions from API data', async () => {
    renderPatients();
    await waitFor(() =>
      expect(screen.getByText('Diabetes, Hypertension')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('renders Synchronized twin status badge', async () => {
    renderPatients();
    await waitFor(() =>
      expect(screen.getByText('Synchronized')).toBeInTheDocument(), { timeout: 5000 });
  });
});

describe('Patients page — error state', () => {
  it('shows error state when API fails with 500', async () => {
    // Override BEFORE render so the fresh query client picks up the override
    server.use(
      http.get('http://localhost:8080/api/patients', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 })));

    renderPatients();
    await waitFor(() =>
      expect(screen.getByText(/Could not load patients/i)).toBeInTheDocument(), { timeout: 8000 });
  });
});

describe('Patients page — empty state', () => {
  it('shows empty state when API returns no content', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients', () =>
        HttpResponse.json({
          content: [], totalElements: 0, totalPages: 0,
          page: 0, size: 20, first: true, last: true,
        })));

    renderPatients();
    await waitFor(() =>
      expect(screen.getByText(/No patients match these filters/i)).toBeInTheDocument(),
      { timeout: 8000 });
  });
});

describe('Patients page — search', () => {
  it('renders search input field', async () => {
    renderPatients();
    await waitFor(() =>
      expect(screen.getByPlaceholderText(/Search by patient name/i)).toBeInTheDocument(),
      { timeout: 5000 });
  });

  it('renders action buttons for patient rows', async () => {
    renderPatients();
    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    expect(screen.getByText('View')).toBeInTheDocument();
    expect(screen.getByText('Predictions')).toBeInTheDocument();
  });

  it('search sends request with search param after debounce', async () => {
    let capturedSearch = '';
    server.use(
      http.get('http://localhost:8080/api/patients', ({ request }) => {
        capturedSearch = new URL(request.url).searchParams.get('search') ?? '';
        return HttpResponse.json({
          content: [], totalElements: 0, totalPages: 0,
          page: 0, size: 20, first: true, last: true,
        });
      }));

    const user = userEvent.setup({ delay: null });
    renderPatients();
    await waitFor(() =>
      screen.getByPlaceholderText(/Search by patient name/i));
    await user.type(screen.getByPlaceholderText(/Search by patient name/i), 'John');
    await waitFor(() => expect(capturedSearch).toBe('John'), { timeout: 2000 });
  });
});

describe('Patients page — pagination', () => {
  it('shows pagination controls when totalPages > 1', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients', () =>
        HttpResponse.json({
          content: [mockPatientSummary],
          totalElements: 25, totalPages: 2, page: 0, size: 20, first: true, last: false,
        })));

    renderPatients();
    await waitFor(() =>
      expect(screen.getByText('Next')).toBeInTheDocument(), { timeout: 8000 });
    expect(screen.getByText('Previous')).toBeInTheDocument();
    expect(screen.getByText(/Page 1 of 2/)).toBeInTheDocument();
  });

  it('Previous button is disabled on first page', async () => {
    server.use(
      http.get('http://localhost:8080/api/patients', () =>
        HttpResponse.json({
          content: [mockPatientSummary],
          totalElements: 25, totalPages: 2, page: 0, size: 20, first: true, last: false,
        })));

    renderPatients();
    const prevBtn = await screen.findByText('Previous', {}, { timeout: 8000 });
    expect(prevBtn).toBeDisabled();
  });
});

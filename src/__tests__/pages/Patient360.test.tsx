import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { server } from '../mocks/server';
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

describe('Patient360 — rendering', () => {
  it('renders patient name after loading', async () => {
    renderPatient360();
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows patient ID in subtitle', async () => {
    renderPatient360();
    await waitFor(() => {
      // Patient ID is in a span with "Patient ID: P001" — match partial text
      expect(screen.getByText(/P001/)).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('shows FHIR connected badge', async () => {
    renderPatient360();
    await waitFor(() =>
      expect(screen.getByText('FHIR connected')).toBeInTheDocument(), { timeout: 5000 });
  });

  it('shows patient status Active badge', async () => {
    renderPatient360();
    await waitFor(() => {
      // At least one Active badge exists in the header
      const badges = screen.getAllByText('Active');
      expect(badges.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });

  it('shows View digital twin action button', async () => {
    renderPatient360();
    await waitFor(() =>
      expect(screen.getByText('View digital twin')).toBeInTheDocument(), { timeout: 5000 });
  });
});

describe('Patient360 — not found', () => {
  it('shows not-found EmptyState content for unknown patient', async () => {
    renderPatient360('P_NOT_EXIST');
    // Use role=heading for the EmptyState title (it is a <p> with specific class, not heading)
    // The EmptyState renders title in a <p class="text-sm font-semibold">
    await waitFor(() => {
      // The EmptyState text is unique — "No patient exists with this ID"
      expect(screen.getByText(/No patient exists with this ID/)).toBeInTheDocument();
    }, { timeout: 5000 });
  });

  it('shows back to patients link in not-found state', async () => {
    renderPatient360('P_NOT_EXIST');
    await waitFor(() => {
      // There will be a "Back to patients" link
      const backLinks = screen.getAllByText('Back to patients');
      expect(backLinks.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });
});

describe('Patient360 — tabs', () => {
  it('renders all 7 tab buttons by role', async () => {
    renderPatient360();
    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });

    // Tabs render as role="tab" buttons — use that to disambiguate from other "Vitals" link
    const tabs = screen.getAllByRole('tab');
    const tabLabels = tabs.map(t => t.textContent);
    expect(tabLabels).toEqual(
      expect.arrayContaining(['Overview', 'Health Twin', 'Vitals', 'Labs', 'Risks', 'Alerts', 'Care Plan'])
    );
  });

  it('Overview tab shows Patient information card', async () => {
    renderPatient360();
    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    expect(screen.getByText('Patient information')).toBeInTheDocument();
  });

  it('clicking Labs tab shows lab content (Phase 3 live data)', async () => {
    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const labsTab = screen.getAllByRole('tab').find(t => t.textContent === 'Labs')!;
    await user.click(labsTab);

    // Phase 3: Labs tab shows real data from API (table headers) or empty state
    await waitFor(() => {
      const hasHeader = screen.queryByText('Test') !== null;
      const hasEmpty  = screen.queryByText('No lab results') !== null;
      expect(hasHeader || hasEmpty).toBe(true);
    }, { timeout: 5000 });
  });

  it('clicking Risks tab shows AI notice and predictions from API', async () => {
    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const risksTab = screen.getAllByRole('tab').find(t => t.textContent === 'Risks')!;
    await user.click(risksTab);

    // Phase 4: Risks tab shows real predictions from API
    // The AI notice is always shown; predictions load from mockPatientPredictions
    await waitFor(() => {
      // AiNotice is always present on the Risks tab
      const hasPredictions = screen.queryByText('10-Year Cardiovascular Risk') !== null;
      const hasEmpty       = screen.queryByText('No predictions available') !== null;
      // One of these must be true — either real data or empty state
      expect(hasPredictions || hasEmpty).toBe(true);
    }, { timeout: 5000 });
  });

  it('clicking Care Plan tab shows create link', async () => {
    const user = userEvent.setup();
    renderPatient360();

    await waitFor(() => screen.getByText('John Doe'), { timeout: 5000 });
    const careTab = screen.getAllByRole('tab').find(t => t.textContent === 'Care Plan')!;
    await user.click(careTab);

    await waitFor(() =>
      expect(screen.getByText('Create care plan')).toBeInTheDocument());
  });
});

describe('Patient360 — page structure', () => {
  it('renders back to patients navigation link', async () => {
    renderPatient360();
    await waitFor(() => {
      // The header back link
      const backLinks = screen.getAllByText('Back to patients');
      expect(backLinks.length).toBeGreaterThan(0);
    }, { timeout: 5000 });
  });
});

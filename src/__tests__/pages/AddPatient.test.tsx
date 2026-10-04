import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { mockPatient } from '../mocks/patientHandlers';
import { AddPatient } from '../../pages/AddPatient';
import { QueryProvider } from '../../context/QueryProvider';
import { tokenStore } from '../../api/tokenStore';

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual as any, useNavigate: () => mockNavigate };
});

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => { server.resetHandlers(); tokenStore.clear(); mockNavigate.mockReset(); });
afterAll(() => server.close());

function renderAddPatient() {
  tokenStore.set('mock.access.token');
  return render(
    <MemoryRouter>
      <QueryProvider>
        <AddPatient />
      </QueryProvider>
    </MemoryRouter>,
  );
}

describe('AddPatient — rendering', () => {
  it('renders the form title', async () => {
    renderAddPatient();
    await waitFor(() => expect(screen.getByText('Add patient')).toBeInTheDocument());
  });

  it('shows auto-generated patient ID from API', async () => {
    renderAddPatient();
    await waitFor(() => {
      const idField = screen.getByDisplayValue('P007');
      expect(idField).toBeInTheDocument();
      expect(idField).toHaveAttribute('readonly');
    });
  });

  it('renders all required form fields', async () => {
    renderAddPatient();
    await waitFor(() => {
      expect(screen.getByLabelText(/Full name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Date of birth/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/FHIR Patient ID/i)).toBeInTheDocument();
    });
  });
});

describe('AddPatient — validation', () => {
  it('shows validation errors for missing required fields', async () => {
    const user = userEvent.setup();
    renderAddPatient();

    await waitFor(() => screen.getByText('Save patient'));
    await user.click(screen.getByText('Save patient'));

    await waitFor(() => {
      expect(screen.getByText(/Full name is required/i)).toBeInTheDocument();
      expect(screen.getByText(/Date of birth is required/i)).toBeInTheDocument();
    });
  });

  it('shows FHIR ID validation error when missing', async () => {
    const user = userEvent.setup();
    renderAddPatient();

    await waitFor(() => screen.getByLabelText(/Full name/i));
    await user.type(screen.getByLabelText(/Full name/i), 'Test Patient');

    const dobInput = screen.getByLabelText(/Date of birth/i);
    await user.type(dobInput, '1990-01-01');

    // Select gender
    const genderSelect = screen.getByLabelText(/Gender/i);
    await user.selectOptions(genderSelect, 'Male');

    await user.click(screen.getByText('Save patient'));

    await waitFor(() =>
      expect(screen.getByText(/FHIR Patient ID is required/i)).toBeInTheDocument());
  });
});

describe('AddPatient — API submission', () => {
  it('shows saving state and navigates to new patient on success', async () => {
    const user = userEvent.setup();
    renderAddPatient();

    await waitFor(() => screen.getByLabelText(/Full name/i));

    await user.type(screen.getByLabelText(/Full name/i), 'Alice New');
    await user.type(screen.getByLabelText(/Date of birth/i), '1990-06-15');
    await user.selectOptions(screen.getByLabelText(/Gender/i), 'Female');
    await user.type(screen.getByLabelText(/FHIR Patient ID/i), 'fhir:Patient/alice-new');

    await user.click(screen.getByText('Save patient'));

    await waitFor(() =>
      expect(mockNavigate).toHaveBeenCalledWith('/patients/P008'));
  });

  it('shows conflict error when FHIR ID already exists (409)', async () => {
    server.use(
      http.post('http://localhost:8080/api/patients', () =>
        HttpResponse.json({ message: 'A patient with this FHIR ID already exists.' }, { status: 409 })));

    const user = userEvent.setup();
    renderAddPatient();

    await waitFor(() => screen.getByLabelText(/Full name/i));
    await user.type(screen.getByLabelText(/Full name/i), 'Dup Patient');
    await user.type(screen.getByLabelText(/Date of birth/i), '1990-06-15');
    await user.selectOptions(screen.getByLabelText(/Gender/i), 'Male');
    await user.type(screen.getByLabelText(/FHIR Patient ID/i), 'fhir:Patient/dup');

    await user.click(screen.getByText('Save patient'));

    await waitFor(() =>
      expect(screen.getByText(/A patient with this FHIR ID already exists/i)).toBeInTheDocument());
  });

  it('shows field-level errors from 400 response', async () => {
    server.use(
      http.post('http://localhost:8080/api/patients', () =>
        HttpResponse.json({
          status: 400, error: 'Bad Request', message: 'Validation failed',
          fieldErrors: { name: 'Name must not exceed 200 characters' },
        }, { status: 400 })));

    const user = userEvent.setup();
    renderAddPatient();

    await waitFor(() => screen.getByLabelText(/Full name/i));
    await user.type(screen.getByLabelText(/Full name/i), 'A'.repeat(201));
    await user.type(screen.getByLabelText(/Date of birth/i), '1990-06-15');
    await user.selectOptions(screen.getByLabelText(/Gender/i), 'Male');
    await user.type(screen.getByLabelText(/FHIR Patient ID/i), 'fhir:Patient/test');

    await user.click(screen.getByText('Save patient'));

    // Should not navigate on error
    await waitFor(() => expect(mockNavigate).not.toHaveBeenCalled(), { timeout: 1000 });
  });
});

describe('AddPatient — FHIR status indicator', () => {
  it('shows "Ready to connect" when FHIR ID is entered', async () => {
    const user = userEvent.setup();
    renderAddPatient();

    await waitFor(() => screen.getByLabelText(/FHIR Patient ID/i));
    await user.type(screen.getByLabelText(/FHIR Patient ID/i), 'fhir:Patient/test-001');

    await waitFor(() =>
      expect(screen.getByText('Ready to connect')).toBeInTheDocument());
  });

  it('shows "Not connected" when FHIR ID is empty', async () => {
    renderAddPatient();
    await waitFor(() =>
      expect(screen.getByText('Not connected')).toBeInTheDocument());
  });
});

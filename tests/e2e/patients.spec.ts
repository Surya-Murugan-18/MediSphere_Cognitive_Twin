/**
 * MediSphere E2E — Patients
 *
 * Phase 8 — B8.4
 *
 * Pre-condition: DataSeeder has created patient P-E2E-01 "Eva Testpatient"
 * with full clinical context on dev startup.
 *
 * Tests:
 *  1. Patients page loads with seeded patient visible
 *  2. Search by patient name filters results
 *  3. Add Patient form → submit → navigates to Patient360
 *  4. Patient360 tabs load their corresponding data
 *
 * Uses shared authenticated storageState (auth.setup.ts runs first).
 */

import { test, expect } from '@playwright/test';

const SEED_PATIENT_NAME = 'Eva Testpatient';
const SEED_PATIENT_ID = 'P-E2E-01';

// ── Test 1: Patients page loads and shows seeded patient ─────────────────────

test('Patients page loads and seeded patient is visible', async ({ page }) => {
  await page.goto('/patients');

  // Page heading — scope to main because the navigation also contains "Patients"
  await expect(
    page.getByRole('main').getByRole('heading', { name: /^Patients$/i })
  ).toBeVisible({ timeout: 15_000 });

  // Wait for the table to populate — at least one row with the seeded patient name
  await expect(
    page.getByRole('cell', { name: SEED_PATIENT_NAME })
  ).toBeVisible({ timeout: 20_000 });
});

// ── Test 2: Search by name filters results ───────────────────────────────────

test('search by patient name filters results', async ({ page }) => {
  await page.goto('/patients');

  await expect(
    page.getByRole('main').getByRole('heading', { name: /^Patients$/i })
  ).toBeVisible({ timeout: 15_000 });

  // Use the Patients page search field specifically.
  // The global clinical search also contains "Search", so a broad selector
  // would match multiple inputs.
  const searchInput = page.getByPlaceholder(
    'Search by patient name, patient ID, FHIR ID'
  );

  await expect(searchInput).toBeVisible({ timeout: 10_000 });

  // Type into the search input
  await searchInput.fill('Eva');

  // Wait for the debounce (350ms) + API response
  await page.waitForTimeout(800);

  // Eva should appear in the filtered table
  await expect(
    page.getByRole('cell', { name: SEED_PATIENT_NAME })
  ).toBeVisible({ timeout: 10_000 });

  // Verify filtering: clear and type something that matches nothing seeded
  await searchInput.fill('zzz_no_match_xyz');

  await page.waitForTimeout(800);

  // The seeded patient should no longer appear
  await expect(
    page.getByRole('cell', { name: SEED_PATIENT_NAME })
  ).not.toBeVisible();
});

// ── Test 3: Add Patient form → submit → Patient360 ───────────────────────────

test('Add Patient form submit navigates to Patient360', async ({ page }) => {
  await page.goto('/patients/new');

  // Page should show the Add Patient form.
  // Scope to main because the navigation also contains "Add Patient".
  await expect(
    page.getByRole('main').getByRole('heading', {
      name: /Add patient|Register patient|New patient/i,
    })
  ).toBeVisible({ timeout: 15_000 });

  // Fill minimum required fields
  const timestamp = Date.now();
  const newPatientName = `E2E Patient ${timestamp}`;

  // Name field
  await page.getByLabel(/Full name/i).fill(newPatientName);

  // Date of birth
  await page.getByLabel(/Date of birth/i).fill('1985-06-15');

  // Gender
  const genderSelect = page.getByLabel(/Gender/i);
  await genderSelect.selectOption('Male');

  // FHIR Patient ID is required by AddPatient validation
  await page.getByLabel(/FHIR Patient ID/i).fill(`fhir:E2E-${timestamp}`);


  // Submit — find the submit button
  const submitButton = page.getByRole('button', {
    name: /Add patient|Register|Create|Save/i,
  });

  await expect(submitButton).toBeVisible({ timeout: 10_000 });
  await expect(submitButton).toBeEnabled({ timeout: 10_000 });

  await submitButton.click();

  // Should navigate to Patient360 for the new patient
  await expect(page).toHaveURL(/\/patients\/P\d+$/, {
  timeout: 30_000,
});

  // Patient360 should show the patient's name
  await expect(
    page.getByText(newPatientName, { exact: true })
  ).toBeVisible({ timeout: 15_000 });
});

// ── Test 4: Patient360 tabs load their data ───────────────────────────────────

test('Patient360 tabs load their corresponding data', async ({ page }) => {
  await page.goto(`/patients/${SEED_PATIENT_ID}`);

  // Wait for patient name to appear in the page
  await expect(
    page.getByText(SEED_PATIENT_NAME, { exact: true })
  ).toBeVisible({ timeout: 20_000 });

  // ── Overview tab (default) ────────────────────────────────────────────────

  // The Overview tab should be active by default and show clinical data
  await expect(
    page.getByRole('tab', { name: /Overview/i })
  ).toBeVisible();

  // Specific seeded condition
  await expect(
    page.getByText('Essential hypertension', { exact: true })
  ).toBeVisible({ timeout: 10_000 });

  // ── Labs tab ──────────────────────────────────────────────────────────────

  await page.getByRole('tab', { name: /Labs/i }).click();

  // Seeded lab result: HbA1c
  await expect(
    page.getByText(/HbA1c/i)
  ).toBeVisible({ timeout: 15_000 });

  // ── Vitals tab ────────────────────────────────────────────────────────────

  await page.getByRole('tab', { name: /Vitals/i }).click();

  // Use the specific vital label rather than a broad regex.
  // This avoids matching multiple "BPM"/heart-rate elements.
  await expect(
    page.getByText('Heart rate', { exact: true })
  ).toBeVisible({ timeout: 15_000 });

  // ── Risks tab ─────────────────────────────────────────────────────────────

  await page.getByRole('tab', { name: /Risks/i }).click();

  // Should show prediction/risk data triggered by DataSeeder
  await expect(
    page.getByText(/CVD|Risk|High/i).first()
  ).toBeVisible({ timeout: 20_000 });

  // ── Alerts tab ────────────────────────────────────────────────────────────

  await page.getByRole('tab', { name: /Alerts/i }).click();

  // Seeded alert: Heart Rate Spike
  await expect(
    page.getByText(/Heart Rate Spike|HR_SPIKE/i).first()
  ).toBeVisible({ timeout: 15_000 });
});
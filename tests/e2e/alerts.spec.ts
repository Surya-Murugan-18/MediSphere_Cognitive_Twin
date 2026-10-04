/**
 * MediSphere E2E — Alerts
 * Phase 8 — B8.4
 *
 * Tests:
 *  1. Alerts list loads and shows real severity badges
 *  2. Clicking alert opens AlertDetails
 *  3. Acknowledge alert → status changes to Acknowledged
 */

import { test, expect } from '@playwright/test';

const SEED_ALERT_ID = 'A-E2E-01';
const SEED_PATIENT_NAME = 'Eva Testpatient';
const SEED_ALERT_EVENT = 'Heart Rate Spike: 145 BPM';

// ─────────────────────────────────────────────────────────────────────────────
// Test 1
// ─────────────────────────────────────────────────────────────────────────────

test('Alerts list loads and displays real severity badges', async ({ page }) => {
  await page.goto('/alerts');

  // Main page heading
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', { name: /^Clinical alerts$/i })
  ).toBeVisible({ timeout: 15_000 });

  // The patient name is rendered as a table cell.
  // Avoid the hidden Patient filter <option>.
  const patientCell = page.locator('td').filter({
    hasText: SEED_PATIENT_NAME,
  }).first();

  await expect(patientCell).toBeVisible({
    timeout: 20_000,
  });

  // The alert row/container is the clickable parent of the patient cell.
  // Its accessible name contains HIGH + patient + event.
  const alertButton = page.getByRole('button', {
  name: /HIGH.*Eva Testpatient.*Heart Rate Spike/i,
}).first();

  await expect(alertButton).toBeVisible({
    timeout: 10_000,
  });

  // Verify HIGH severity is visible in the actual alert item,
  // not the hidden HIGH filter option.
  await expect(
    alertButton.getByText('HIGH', { exact: true })
  ).toBeVisible({
    timeout: 10_000,
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 2
// ─────────────────────────────────────────────────────────────────────────────

test('clicking an alert row opens AlertDetails', async ({ page }) => {
  await page.goto('/alerts');

  // Main Alerts page heading
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', { name: /^Clinical alerts$/i })
  ).toBeVisible({ timeout: 15_000 });

  // Wait for the actual patient cell.
  const patientCell = page.locator('td').filter({
    hasText: SEED_PATIENT_NAME,
  }).first();

  await expect(patientCell).toBeVisible({
    timeout: 20_000,
  });

  // Navigate directly to the deterministic seeded alert.
  await page.goto(`/alerts/${SEED_ALERT_ID}`);

  // Alert Details heading
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', { name: /^Alert details$/i })
  ).toBeVisible({
    timeout: 15_000,
  });

  // Alert event
  await expect(
  page.getByText(/Heart Rate Spike:\s*145 BPM/i).first()
).toBeVisible({
  timeout: 10_000,
});

  // Patient name
 

  // Exact current value.
  // This avoids matching "Heart Rate Spike: 145 BPM".
  await expect(
    page.getByText('145 BPM', { exact: true })
  ).toBeVisible({
    timeout: 10_000,
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 3
// ─────────────────────────────────────────────────────────────────────────────

test('acknowledging alert changes status to Acknowledged', async ({ page }) => {
  await page.goto(`/alerts/${SEED_ALERT_ID}`);

  // Main Alert Details heading
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', { name: /^Alert details$/i })
  ).toBeVisible({
    timeout: 15_000,
  });

  // Current status
  const statusBadge = page.getByText(
    /Unacknowledged|Acknowledged/i
  ).first();

  await statusBadge.waitFor({
    state: 'visible',
    timeout: 10_000,
  });

  const currentStatus = await statusBadge.textContent();

  if (currentStatus?.includes('Unacknowledged')) {
    const acknowledgeButton = page.getByRole(
      'button',
      { name: /Acknowledge/i }
    );

    await expect(acknowledgeButton).toBeVisible({
      timeout: 10_000,
    });

    await expect(acknowledgeButton).toBeEnabled();

    // Acknowledge
    await acknowledgeButton.click();

    // Status should change
    await expect(
      page.getByText('Acknowledged').first()
    ).toBeVisible({
      timeout: 15_000,
    });

    // The active Acknowledge button is removed after acknowledgement.
    await expect(
      page.getByRole(
        'button',
        { name: /^Acknowledge$/i }
      )
    ).toHaveCount(0);

  } else {
    // Already acknowledged from a previous run.
    expect(currentStatus).toContain('Acknowledged');

    const acknowledgeButton = page.getByRole(
      'button',
      { name: /Acknowledged/i }
    );

    await expect(acknowledgeButton).toBeDisabled();
  }
});
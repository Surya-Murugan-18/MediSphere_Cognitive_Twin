/**
 * MediSphere E2E — Care Plans
 *
 * Phase 8 — B8.4
 *
 * Pre-condition:
 *  - DataSeeder has created patient P-E2E-01 "Eva Testpatient"
 *  - Patient has predictions from seedMissingPredictions (async — may need waitFor)
 *
 * Tests:
 *  1. Generate care plan → recommendations render
 *  2. Review care plan → Approve → status becomes Active
 *  3. Edit care plan navigates to edit route (/care-plans/:id/edit), not create route
 *
 * Uses shared authenticated storageState (auth.setup.ts runs first).
 *
 * Care plans are cumulative — each test run may create a new Draft plan.
 * Tests are designed to work with the most recently generated plan.
 */

import { test, expect, type Page } from '@playwright/test';

const SEED_PATIENT_ID = 'P-E2E-01';
const SEED_PATIENT_NAME = 'Eva Testpatient';

// ── Helper: generate a care plan and return the plan ID ──────────────────────

async function generateCarePlanForE2EPatient(page: Page): Promise<string> {
  await page.goto(`/care-plans/new?patientId=${SEED_PATIENT_ID}`);

  // Page header should confirm we are in create mode for our patient.
  // Use first() because the patient name appears in more than one UI location.
  await expect(
    page.getByText(SEED_PATIENT_NAME, { exact: false }).first()
  ).toBeVisible({ timeout: 20_000 });

  // Wait for auto-generation to complete.
  // Scope to main because the header/navigation can also contain
  // a "Review plan" action.
  const reviewButton = page
    .getByRole('main')
    .getByRole('button', { name: /^Review plan$/i })
    .first();

  await expect(reviewButton).toBeEnabled({ timeout: 30_000 });

  // Navigate to review.
  await reviewButton.click();

  // Now on /care-plans/:planId/review
  const url = page.url();
  const match = url.match(/\/care-plans\/([^/]+)\/review/);

  if (!match) {
    throw new Error(
      `Expected URL to match /care-plans/:id/review but got: ${url}`
    );
  }

  return match[1];
}

// ── Test 1: Generate care plan → recommendations render ──────────────────────

test('generate care plan for seeded patient shows recommendations', async ({ page }) => {
  await page.goto(`/care-plans/new?patientId=${SEED_PATIENT_ID}`);

  // Heading: create mode.
  // Scope to main because navigation also contains a similarly named heading.
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', {
        name: /^Generate personalized care plan$/i,
      })
  ).toBeVisible({ timeout: 15_000 });

  // Patient name should appear in the context.
  await expect(
    page.getByText(SEED_PATIENT_NAME, { exact: false }).first()
  ).toBeVisible({ timeout: 20_000 });

  // Wait for AI generation — the stub is fast, but async.
  // Scope to main to avoid duplicate Review plan buttons.
  const reviewButton = page
    .getByRole('main')
    .getByRole('button', { name: /^Review plan$/i })
    .first();

  await expect(reviewButton).toBeEnabled({ timeout: 30_000 });

  // At least one recommendation should be visible.
  // StubAICarePlanService always generates recommendations.
  const recommendations = page.getByText(
    /Medication|Exercise|Monitor|Diet|Lifestyle|Follow-up/i
  );

  await expect(recommendations.first()).toBeVisible({
    timeout: 15_000,
  });
});

// ── Test 2: Review → Approve → status Active ─────────────────────────────────

test('review and approve care plan changes status to Active', async ({ page }) => {
  // Generate a fresh plan and navigate to its review page.
  const planId = await generateCarePlanForE2EPatient(page);

  // Should now be on /care-plans/:planId/review.
  await expect(page).toHaveURL(
    new RegExp(`/care-plans/${planId}/review`)
  );

  // Review heading — scope to main to avoid navigation duplicates.
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', {
        name: /Review care plan|Care plan review/i,
      })
  ).toBeVisible({ timeout: 15_000 });

  // Patient name should appear in the review.
  // The name is rendered in multiple locations, so use first().
  await expect(
    page.getByText(SEED_PATIENT_NAME, { exact: false }).first()
  ).toBeVisible({ timeout: 10_000 });

  // Approve button.
  // The review page first opens the provider approval confirmation modal.
const confirmApprovalButton = page
  .getByRole('main')
  .getByRole('button', { name: /^Confirm approval$/i })
  .first();

await expect(confirmApprovalButton).toBeVisible({
  timeout: 10_000,
});

await expect(confirmApprovalButton).toBeEnabled({
  timeout: 10_000,
});

await confirmApprovalButton.click();

// Confirmation modal should now appear.
const approvePlanButton = page.getByRole('button', {
  name: /^Approve plan$/i,
});

await expect(approvePlanButton).toBeVisible({
  timeout: 10_000,
});

await expect(approvePlanButton).toBeEnabled({
  timeout: 10_000,
});

await approvePlanButton.click();

  // After approval, should navigate to adherence page.
  await expect(page).toHaveURL(
    new RegExp(`/care-plans/${planId}/adherence`),
    { timeout: 20_000 }
  );

  // Navigate back to care plans list and verify the plan shows as Active.
  await page.goto('/care-plans');

  await expect(
    page.getByText(/Active/i).first()
  ).toBeVisible({ timeout: 20_000 });
});

// ── Test 3: Edit care plan uses edit route, not create route ─────────────────

test('editing a care plan navigates to edit route not create route', async ({ page }) => {
  // Navigate to care plans list.
  await page.goto('/care-plans');

  // Scope heading to main because navigation also contains "Care plans".
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', { name: /^Care plans$/i })
  ).toBeVisible({ timeout: 15_000 });

  // Wait for the list to load.
  await page.waitForTimeout(2_000);

  // Try to find an Edit link/button in the care plans table.
  const editLink = page
    .getByRole('main')
    .getByRole('link', { name: /Edit/i })
    .or(
      page
        .getByRole('main')
        .getByRole('button', { name: /Edit/i })
    )
    .first();

  // If no edit link found, navigate to a known care plan detail and find edit there.
  if (!(await editLink.isVisible({ timeout: 3_000 }).catch(() => false))) {
    // Navigate via care plan detail page.
    // First find a care plan row/link and navigate to it.
    const carePlanLink = page
      .getByRole('main')
      .getByRole('link', { name: /CP-|View|Details/i })
      .first();

    if (await carePlanLink.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await carePlanLink.click();

      await page.waitForURL(/\/care-plans\/CP-/, {
        timeout: 10_000,
      });
    }

    // Try edit from detail page.
    const detailEditLink = page
      .getByRole('main')
      .getByRole('link', { name: /Edit/i })
      .or(
        page
          .getByRole('main')
          .getByRole('button', { name: /Edit|Modify/i })
      )
      .first();

    await expect(detailEditLink).toBeVisible({
      timeout: 10_000,
    });

    await detailEditLink.click();
  } else {
    await editLink.click();
  }

  // The URL must match the EDIT pattern: /care-plans/:id/edit.
  await expect(page).toHaveURL(
    /\/care-plans\/[^/]+\/edit/,
    { timeout: 10_000 }
  );

  // The URL must NOT match the CREATE pattern: /care-plans/new.
  const editUrl = page.url();

  expect(editUrl).not.toContain('/care-plans/new');
  expect(editUrl).toContain('/edit');

  // The page heading should indicate edit mode.
  await expect(
    page
      .getByRole('main')
      .getByRole('heading', { name: /^Edit care plan$/i })
  ).toBeVisible({ timeout: 15_000 });
});
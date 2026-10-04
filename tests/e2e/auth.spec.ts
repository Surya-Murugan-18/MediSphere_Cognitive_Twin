/**
 * MediSphere E2E — Authentication
 * Phase 8 — B8.4
 *
 * Tests:
 *  1. Valid credentials → Dashboard renders
 *  2. Invalid credentials → error displayed
 *  3. Page refresh → session remains authenticated
 *  4. Logout → redirected to Login page
 *
 * These tests intentionally do NOT reuse the shared storageState because they
 * test the login flow itself. Each test starts from a fresh unauthenticated
 * browser context.
 */

import { test, expect } from '@playwright/test';

// Authentication tests start unauthenticated.
test.use({ storageState: { cookies: [], origins: [] } });

// ─────────────────────────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────────────────────────

async function loginWith(
  page: any,
  email: string,
  password: string
) {
  await page.goto('/');

  await expect(
    page.getByRole('heading', { name: 'Welcome back' })
  ).toBeVisible({
    timeout: 15_000,
  });

  await page
    .getByLabel(/Provider ID \/ Email/i)
    .fill(email);

  await page
    .getByLabel(/Password/i)
    .fill(password);

  await page
    .getByRole('button', { name: /Sign In/i })
    .click();
}

// ─────────────────────────────────────────────────────────────────────────────
// Test 1: Valid login → Dashboard renders
// ─────────────────────────────────────────────────────────────────────────────

test('valid credentials → Dashboard renders', async ({ page }) => {
  await loginWith(
    page,
    'a.mehta@medisphere.dev',
    'Medisphere@123'
  );

  // Dashboard heading
  await expect(
    page.getByRole(
      'heading',
      { name: /Clinical Operations Dashboard/i }
    )
  ).toBeVisible({
    timeout: 30_000,
  });

  // URL should remain root
  await expect(page).toHaveURL('/');

  // Authenticated shell navigation.
  // There are two navigation elements:
  // sidebar + Breadcrumb. We only need to verify navigation exists.
  await expect(
    page.getByRole('navigation').first()
  ).toBeVisible();
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 2: Invalid credentials → error shown
// ─────────────────────────────────────────────────────────────────────────────

test('invalid credentials → error message shown', async ({ page }) => {
  await loginWith(
    page,
    'a.mehta@medisphere.dev',
    'WrongPassword999!'
  );

  await expect(
    page.getByText(
      /Invalid credentials|Sign in failed/i
    )
  ).toBeVisible({
    timeout: 15_000,
  });

  await expect(
    page.getByRole(
      'heading',
      { name: 'Welcome back' }
    )
  ).toBeVisible({
    timeout: 10_000,
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 3: Page refresh → session restored
// ─────────────────────────────────────────────────────────────────────────────

test('page refresh → session remains authenticated', async ({ page }) => {
  await loginWith(
    page,
    'a.mehta@medisphere.dev',
    'Medisphere@123'
  );

  await expect(
    page.getByRole(
      'heading',
      { name: /Clinical Operations Dashboard/i }
    )
  ).toBeVisible({
    timeout: 30_000,
  });

  // Reload page — AuthContext should restore the session.
  await page.reload();

  // Dashboard must still be visible.
  await expect(
    page.getByRole(
      'heading',
      { name: /Clinical Operations Dashboard/i }
    )
  ).toBeVisible({
    timeout: 20_000,
  });

  // Login form must not be visible.
  await expect(
    page.getByRole(
      'heading',
      { name: 'Welcome back' }
    )
  ).not.toBeVisible();
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 4: Logout → Login page appears
// ─────────────────────────────────────────────────────────────────────────────

test('logout → Login page appears', async ({ page }) => {
  await loginWith(
    page,
    'a.mehta@medisphere.dev',
    'Medisphere@123'
  );

  await expect(
    page.getByRole(
      'heading',
      { name: /Clinical Operations Dashboard/i }
    )
  ).toBeVisible({
    timeout: 30_000,
  });

  // The Sign out button is inside the profile dropdown.
  // First open the profile menu.
  const profileButton = page.getByRole('button', {
    name: /Dr\. A\. Mehta|Clinician/i,
  });

  await expect(profileButton).toBeVisible({
    timeout: 10_000,
  });

  await profileButton.click();

  // Now the Sign out button is rendered.
  const signOutButton = page.getByRole('button', {
    name: /^Sign out$/i,
  });

  await expect(signOutButton).toBeVisible({
    timeout: 10_000,
  });

  await signOutButton.click();

  // Login page must appear.
  await expect(
    page.getByRole(
      'heading',
      { name: 'Welcome back' }
    )
  ).toBeVisible({
    timeout: 10_000,
  });

  // Dashboard must no longer be visible.
  await expect(
    page.getByRole(
      'heading',
      { name: /Clinical Operations Dashboard/i }
    )
  ).not.toBeVisible();
});
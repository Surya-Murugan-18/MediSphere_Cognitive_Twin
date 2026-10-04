/**
 * Authentication setup — runs before all E2E tests.
 *
 * Performs a real login via the UI, then saves the browser storage state
 * (localStorage access token + httpOnly cookie) to a file so that all
 * subsequent test projects can reuse the authenticated session without
 * logging in again.
 *
 * Seeded clinician credentials (DataSeeder @Profile("dev")):
 *   Email:    a.mehta@medisphere.dev
 *   Password: Medisphere@123
 */
import { test as setup, expect } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';

const STORAGE_STATE_PATH = path.join(process.cwd(), 'playwright', '.auth', 'clinician.json');

setup('authenticate as clinician', async ({ page }) => {
  // Ensure the auth directory exists
  fs.mkdirSync(path.dirname(STORAGE_STATE_PATH), { recursive: true });

  await page.goto('/');

  // Wait for the login form to appear
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible({ timeout: 15_000 });

  // Fill credentials
  await page.getByLabel(/Provider ID \/ Email/i).fill('a.mehta@medisphere.dev');
  await page.getByLabel(/Password/i).fill('Medisphere@123');

  // Submit
  await page.getByRole('button', { name: /Sign In/i }).click();

  // Wait for Dashboard to appear — confirms successful login
  await expect(page.getByRole('heading', { name: /Clinical Operations Dashboard/i }))
    .toBeVisible({ timeout: 30_000 });

  // Save storage state (localStorage + cookies) for reuse in all tests
  await page.context().storageState({ path: STORAGE_STATE_PATH });
});

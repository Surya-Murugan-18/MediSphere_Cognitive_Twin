import { defineConfig, devices } from '@playwright/test';

/**
 * MediSphere Cognitive Twin — Playwright E2E Configuration
 * Phase 8 — B8.4
 *
 * Tests run against the Docker Compose environment:
 *   docker compose up --build
 *   Frontend:  http://localhost:3000
 *   Backend:   http://localhost:8080
 *
 * For local development (without Docker):
 *   Set BASE_URL=http://localhost:5173 in environment.
 *
 * Execution:
 *   npm run test:e2e
 *   npm run test:e2e:report
 */

const BASE_URL =
  process.env.BASE_URL ?? 'http://localhost:3000';

export default defineConfig({
  // Test directory
  testDir: './tests/e2e',

  // Test file pattern
  testMatch: '**/*.spec.ts',

  // Run sequentially for deterministic E2E execution
  fullyParallel: false,

  // Fail CI if test.only is accidentally committed
  forbidOnly: Boolean(process.env.CI),

  // Retry once on CI
  retries: process.env.CI ? 1 : 0,

  // Sequential execution
  workers: 1,

  // Reports
  reporter: [
    ['html', { open: 'never' }],
    ['line'],
  ],

  // Global timeout per test
  timeout: 60_000,

  // Shared settings
  use: {
    baseURL: BASE_URL,

    trace: 'on-first-retry',

    screenshot: 'only-on-failure',

    viewport: {
      width: 1280,
      height: 800,
    },

    ignoreHTTPSErrors: true,
  },

  projects: [
    // ─────────────────────────────────────────────
    // 1. Authentication setup
    //
    // Performs a real login once and saves:
    // playwright/.auth/clinician.json
    // ─────────────────────────────────────────────
    {
      name: 'setup',

      testMatch: /.*\.setup\.ts/,
    },

    // ─────────────────────────────────────────────
    // 2. Authentication tests
    //
    // auth.spec.ts must start WITHOUT the shared
    // authenticated storage state because it tests:
    // - valid login
    // - invalid login
    // - session restoration
    // - logout
    // ─────────────────────────────────────────────
    {
  name: 'auth',

  testMatch: /.*auth\.spec\.ts/,

  use: {
    ...devices['Desktop Chrome'],

    storageState: {
      cookies: [],
      origins: [],
    },
  },

  dependencies: ['chromium'],
},

    // ─────────────────────────────────────────────
    // 3. Main authenticated application tests
    //
    // Includes:
    // - Patients
    // - Alerts
    // - Care Plans
    //
    // Reuses the authenticated state created by
    // auth.setup.ts.
    // ─────────────────────────────────────────────
    {
      name: 'chromium',

      use: {
        ...devices['Desktop Chrome'],

        storageState:
          'playwright/.auth/clinician.json',
      },

      dependencies: ['setup'],

      // Do not execute setup or auth tests again
      // inside the authenticated project.
      testIgnore: [
        /.*\.setup\.ts/,
        /.*auth\.spec\.ts/,
      ],
    },
  ],
});
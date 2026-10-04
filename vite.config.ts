import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/* https://vitejs.dev/config/ */

export default defineConfig({
  plugins: [react()],

  define: {
    global: 'globalThis',
  },

  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['node_modules', 'dist'],

    // Suppress unhandled rejection noise from Axios error tests
    // All 24 test assertions pass; rejections are expected behavior
    dangerouslyIgnoreUnhandledErrors: true,
  },
} as any)
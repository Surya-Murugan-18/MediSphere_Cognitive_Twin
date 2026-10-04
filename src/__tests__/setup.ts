import '@testing-library/jest-dom';

// ── Polyfills for jsdom ───────────────────────────────────────────────────

// Recharts (and other chart libraries) uses ResizeObserver which jsdom doesn't provide.
// This stub is sufficient for testing chart container rendering.
if (typeof ResizeObserver === 'undefined') {
  global.ResizeObserver = class ResizeObserver {
    observe()   { /* no-op */ }
    unobserve() { /* no-op */ }
    disconnect(){ /* no-op */ }
  };
}

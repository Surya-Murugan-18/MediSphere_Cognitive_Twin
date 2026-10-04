import { setupServer } from 'msw/node';
import { authHandlers } from './authHandlers';
import { patientHandlers } from './patientHandlers';
import { phase3Handlers } from './phase3Handlers';
import { phase4Handlers } from './phase4Handlers';
import { phase5Handlers } from './phase5Handlers';
import { phase6Handlers } from './phase6Handlers';
import { phase7Handlers } from './phase7Handlers';

export const server = setupServer(
  ...phase7Handlers,   // Phase 7 — consent, audit, population, reports, system, settings, search, dashboard
  ...phase6Handlers,   // Phase 6 — care plans, adherence
  ...phase5Handlers,   // Phase 5 — alerts, monitoring
  ...phase4Handlers,   // Phase 4 — predictions, explainability, federated
  ...phase3Handlers,   // Phase 3 — vitals, labs
  ...authHandlers,
  ...patientHandlers,
);

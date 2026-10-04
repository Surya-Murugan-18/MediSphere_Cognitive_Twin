import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

// ── Mock data matching StubAIPredictionService output shape ──────────────

export const mockPredictionCvd = {
  id: 'PR-CVD001',
  patientId: 'P001',
  model: 'CVD-Risk-v3.2',
  label: '10-Year Cardiovascular Risk',
  value: 24.3,
  category: 'High',
  federatedRound: 47,
  confidence: 91,
  calibration: 'Calibrated',
  shapFactors: [
    { feature: 'HbA1c',           contribution: 8.0,  value: '8.2%',         direction: 'increases' },
    { feature: 'Blood Pressure',  contribution: 6.0,  value: '142/91 mmHg',  direction: 'increases' },
    { feature: 'Age',             contribution: 4.2,  value: '58 years',     direction: 'increases' },
    { feature: 'Medication Adherence', contribution: -2.1, value: '78%',     direction: 'decreases' },
  ],
  clinicalEvidence: [
    { guideline: 'ACC/AHA CVD Risk Calculator', detail: 'Validated against Pooled Cohort Equations.' },
    { guideline: 'ADA Standards of Care 2026',  detail: 'HbA1c above 8% is associated with macrovascular risk.' },
  ],
  createdAt: '2026-09-21T12:07:00Z',
};

export const mockPredictionDm = {
  id: 'PR-DM001',
  patientId: 'P001',
  model: 'DM-Complication-v2.4',
  label: 'Diabetes Complication Risk (12 mo)',
  value: 16.0,
  category: 'Low',
  federatedRound: 47,
  confidence: 88,
  calibration: 'Calibrated',
  shapFactors: [
    { feature: 'HbA1c',           contribution: 4.0,  value: '8.2%', direction: 'increases' },
    { feature: 'Fasting Glucose', contribution: 2.0,  value: '126 mg/dL', direction: 'increases' },
  ],
  clinicalEvidence: [
    { guideline: 'ADA Standards of Care 2026', detail: 'HbA1c and fasting glucose are primary indicators.' },
  ],
  createdAt: '2026-09-21T12:07:01Z',
};

export const mockPredictionReadmission = {
  id: 'PR-READ001',
  patientId: 'P001',
  model: 'Readmit-30d-v1.8',
  label: '30-Day Readmission Risk',
  value: 8.0,
  category: 'Low',
  federatedRound: 46,
  confidence: 84,
  calibration: 'Calibrated',
  shapFactors: [
    { feature: 'Prior Hospitalizations', contribution: 3.0, value: 'Baseline risk factor', direction: 'increases' },
  ],
  clinicalEvidence: [
    { guideline: 'CMS Hospital Readmissions Reduction Program', detail: 'Identifies patients at elevated 30-day risk.' },
  ],
  createdAt: '2026-09-21T12:07:02Z',
};

export const mockPredictions = [mockPredictionCvd, mockPredictionDm, mockPredictionReadmission];

export const mockPredictionPage = {
  content: mockPredictions,
  totalElements: 3,
  totalPages: 1,
  page: 0,
  size: 20,
  first: true,
  last: true,
};

export const mockPredictionStats = {
  total: 3,
  avgAccuracy: 87.7,
  highRiskCount: 1,
  latestRound: 47,
};

export const mockRiskDistribution = [
  { name: 'High',   value: 1 },
  { name: 'Medium', value: 0 },
  { name: 'Low',    value: 2 },
];

export const mockExplanation = {
  predictionId: 'PR-CVD001',
  patientId:    'P001',
  patientName:  'John Doe',
  model:        'CVD-Risk-v3.2',
  label:        '10-Year Cardiovascular Risk',
  value:        24.3,
  category:     'High',
  federatedRound: 47,
  confidence:   91,
  calibration:  'Calibrated',
  shapFactors:  mockPredictionCvd.shapFactors,
  clinicalEvidence: mockPredictionCvd.clinicalEvidence,
  naturalLanguageSummary:
    'The model estimated a 10-year cardiovascular risk of 24.3% for this patient. ' +
    'Risk was driven upward mainly by HbA1c (8.2%), Blood Pressure (142/91 mmHg), Age (58 years). ' +
    'All contributions are computed locally on hospital data. ' +
    'No patient-level features leave this institution during federated training.',
  createdAt: '2026-09-21T12:07:00Z',
};

// ── Federated learning mock data ─────────────────────────────────────────

export const mockFederatedNodes = [
  { id: 'HOSP-A', hospitalName: 'Hospital A — Northside General', status: 'Connected', patients: 512, lastSync: '2026-09-21T12:17:00Z', contribution: 41 },
  { id: 'HOSP-B', hospitalName: 'Hospital B — Lakeview Medical',  status: 'Connected', patients: 438, lastSync: '2026-09-21T12:16:00Z', contribution: 35 },
  { id: 'HOSP-C', hospitalName: 'Hospital C — Riverbend Clinic',  status: 'Connected', patients: 297, lastSync: '2026-09-21T12:14:00Z', contribution: 24 },
];

export const mockCurrentRound = {
  round: 47, model: 'CVD-Risk-v3.2', accuracy: 91.4, durationMinutes: 13,
  status: 'Completed',
  startedAt: '2026-09-21T11:02:00Z', completedAt: '2026-09-21T11:15:00Z',
  nodeContributions: [
    { nodeId: 'HOSP-A', contribution: 41, status: 'Completed' },
    { nodeId: 'HOSP-B', contribution: 35, status: 'Completed' },
    { nodeId: 'HOSP-C', contribution: 24, status: 'Completed' },
  ],
};

export const mockFederatedRoundsPage = {
  content: [
    { round: 47, model: 'CVD-Risk-v3.2', accuracy: 91.4, durationMinutes: 13, status: 'Completed', startedAt: '2026-09-21T11:02:00Z', completedAt: '2026-09-21T11:15:00Z', nodeContributions: [] },
    { round: 46, model: 'CVD-Risk-v3.2', accuracy: 90.9, durationMinutes: 12, status: 'Completed', startedAt: '2026-09-21T10:48:00Z', completedAt: '2026-09-21T11:00:00Z', nodeContributions: [] },
    { round: 45, model: 'CVD-Risk-v3.1', accuracy: 90.3, durationMinutes: 15, status: 'Completed', startedAt: '2026-09-21T10:32:00Z', completedAt: '2026-09-21T10:47:00Z', nodeContributions: [] },
    { round: 44, model: 'CVD-Risk-v3.1', accuracy: 89.8, durationMinutes: 13, status: 'Completed', startedAt: '2026-09-21T10:18:00Z', completedAt: '2026-09-21T10:31:00Z', nodeContributions: [] },
    { round: 43, model: 'CVD-Risk-v3.0', accuracy: 89.1, durationMinutes: 14, status: 'Completed', startedAt: '2026-09-21T10:03:00Z', completedAt: '2026-09-21T10:17:00Z', nodeContributions: [] },
  ],
  totalElements: 5, totalPages: 1, page: 0, size: 10, first: true, last: true,
};

// ── Patient predictions (for Patient360 Risks tab) ────────────────────────

export const mockPatientPredictions = mockPredictions;

// ── MSW handlers ──────────────────────────────────────────────────────────

export const phase4Handlers = [
  // Predictions list
  http.get(`${BASE}/api/predictions`, () =>
    HttpResponse.json(mockPredictionPage)),

  // Prediction stats
  http.get(`${BASE}/api/predictions/stats`, () =>
    HttpResponse.json(mockPredictionStats)),

  // Risk distribution
  http.get(`${BASE}/api/predictions/risk-distribution`, () =>
    HttpResponse.json(mockRiskDistribution)),

  // Single prediction
  http.get(`${BASE}/api/predictions/:id`, ({ params }) => {
    const pred = mockPredictions.find((p) => p.id === params.id);
    if (!pred) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    return HttpResponse.json(pred);
  }),

  // SHAP factors
  http.get(`${BASE}/api/predictions/:id/shap`, ({ params }) => {
    const pred = mockPredictions.find((p) => p.id === params.id);
    if (!pred) return HttpResponse.json({ message: 'Not found' }, { status: 404 });
    return HttpResponse.json(pred.shapFactors);
  }),

  // Explainability
  http.get(`${BASE}/api/explainability/:predictionId`, ({ params }) => {
    if (params.predictionId === 'PR-CVD001') return HttpResponse.json(mockExplanation);
    return HttpResponse.json({ message: 'Not found' }, { status: 404 });
  }),

  // Patient predictions (Patient360 Risks tab)
  http.get(`${BASE}/api/patients/:patientId/predictions`, () =>
    HttpResponse.json(mockPatientPredictions)),

  // Federated nodes
  http.get(`${BASE}/api/federated/nodes`, () =>
    HttpResponse.json(mockFederatedNodes)),

  // Current round
  http.get(`${BASE}/api/federated/rounds/current`, () =>
    HttpResponse.json(mockCurrentRound)),

  // Round history
  http.get(`${BASE}/api/federated/rounds`, () =>
    HttpResponse.json(mockFederatedRoundsPage)),
];

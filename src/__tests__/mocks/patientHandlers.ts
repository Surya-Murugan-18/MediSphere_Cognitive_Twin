import { http, HttpResponse } from 'msw';

const BASE = 'http://localhost:8080';

// ── Mock data ─────────────────────────────────────────────────────────────

export const mockPatientSummary = {
  id: 'P001', name: 'John Doe', dob: '1968-04-12', gender: 'Male',
  conditions: ['Diabetes', 'Hypertension'],
  riskLevel: 'High', status: 'Active',
  twinId: 'HT-001', twinStatus: 'Synchronized', twinCompleteness: 96,
  fhirConnected: true, consentComplete: true, wearableStatus: 'Online',
  providerName: 'Dr. A. Mehta', updatedAt: new Date().toISOString(),
};

export const mockPatient = {
  ...mockPatientSummary,
  fhirId: 'fhir:Patient/8a21-4c77', age: 58,
  phone: '+1 (415) 555-0142', email: 'john.doe@example.com',
  consentId: 'CON-001', providerId: 'PROV-001', ehrSystem: 'Epic',
  healthStatus: 'Stable', adherence: 78,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  vitals: null, medications: null,
};

export const mockTwin = {
  id: 'HT-001', patientId: 'P001', modelVersion: 'v2.1',
  completeness: 0, status: 'Syncing', stateVersion: 1,
  lastUpdated: new Date().toISOString(),
  dataSources: {
    ehr:      { connected: false, lastSync: null },
    lab:      { connected: false, lastSync: null },
    wearable: { connected: false, lastSync: null },
    kafka:    { connected: false, lastSync: null },
  },
  bodyRegions: [
    { region: 'cardiac',     label: 'Cardiac',     detail: 'Awaiting data', riskLevel: 'low' },
    { region: 'vascular',    label: 'Vascular',    detail: 'Awaiting data', riskLevel: 'low' },
    { region: 'metabolic',   label: 'Metabolic',   detail: 'Awaiting data', riskLevel: 'low' },
    { region: 'renal',       label: 'Renal',       detail: 'Awaiting data', riskLevel: 'low' },
    { region: 'respiratory', label: 'Respiratory', detail: 'Awaiting data', riskLevel: 'medium' },
  ],
  timeline: [
    { id: 'te-1', timestamp: new Date().toISOString(), title: 'Twin created', detail: 'Digital Health Twin initialised.', tone: 'info' },
  ],
};

export const mockPageResponse = {
  content: [mockPatientSummary],
  totalElements: 1, totalPages: 1, page: 0, size: 20, first: true, last: true,
};

export const mockFilterOptions = {
  conditions: ['Diabetes', 'Hypertension'],
  providers:  ['Dr. A. Mehta'],
  statuses:   ['Active', 'Inactive', 'Pending Consent'],
  riskLevels: ['High', 'Medium', 'Low'],
};

// ── MSW handlers — use path parameters for dynamic routes ─────────────────

export const patientHandlers = [
  // Patient list
  http.get(`${BASE}/api/patients`, () =>
    HttpResponse.json(mockPageResponse)),

  // Filter options — must be before :id route
  http.get(`${BASE}/api/patients/filter-options`, () =>
    HttpResponse.json(mockFilterOptions)),

  // Next ID — must be before :id route
  http.get(`${BASE}/api/patients/next-id`, () =>
    HttpResponse.json({ nextId: 'P007' })),

  // Single patient — dynamic path using MSW path params
  http.get(`${BASE}/api/patients/:patientId`, ({ params }) => {
    const { patientId } = params;
    if (patientId === 'P001') {
      return HttpResponse.json(mockPatient);
    }
    if (patientId === 'P_NOT_EXIST' || patientId === 'P_UNKNOWN') {
      return HttpResponse.json({ status: 404, message: 'Not found' }, { status: 404 });
    }
    return HttpResponse.json(mockPatient);
  }),

  // Timeline
  http.get(`${BASE}/api/patients/:patientId/timeline`, ({ params }) => {
    const { patientId } = params;
    if (patientId === 'P_NOT_EXIST' || patientId === 'P_UNKNOWN') {
      return HttpResponse.json({ status: 404, message: 'Not found' }, { status: 404 });
    }
    return HttpResponse.json(mockTwin.timeline);
  }),

  // Create patient
  http.post(`${BASE}/api/patients`, async ({ request }) => {
    const body = await request.json() as any;
    return HttpResponse.json(
      { ...mockPatient, id: 'P008', name: body.name, fhirId: body.fhirId },
      { status: 201 },
    );
  }),

  // Update patient
  http.put(`${BASE}/api/patients/:patientId`, async ({ request }) => {
    const body = await request.json() as any;
    return HttpResponse.json({ ...mockPatient, ...body });
  }),

  // Twin — dynamic path
  http.get(`${BASE}/api/twins/:twinId`, ({ params }) => {
    const { twinId } = params;
    if (twinId === 'HT-001') return HttpResponse.json(mockTwin);
    return HttpResponse.json({ status: 404, message: 'Not found' }, { status: 404 });
  }),

  http.get(`${BASE}/api/twins/:twinId/body-regions`, ({ params }) => {
    if ((params.twinId as string) === 'HT-001')
      return HttpResponse.json(mockTwin.bodyRegions);
    return HttpResponse.json([]);
  }),

  http.get(`${BASE}/api/twins/:twinId/data-sources`, ({ params }) => {
    if ((params.twinId as string) === 'HT-001')
      return HttpResponse.json(mockTwin.dataSources);
    return HttpResponse.json({ ehr: { connected: false, lastSync: null }, lab: { connected: false, lastSync: null }, wearable: { connected: false, lastSync: null }, kafka: { connected: false, lastSync: null } });
  }),

  http.get(`${BASE}/api/twins/:twinId/timeline`, ({ params }) => {
    if ((params.twinId as string) === 'HT-001')
      return HttpResponse.json(mockTwin.timeline);
    return HttpResponse.json([]);
  }),
];
